package decaf.compile.ir

import decaf.gen.{DecafParser, DecafParserBaseVisitor}
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode

import collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
 * Converts the ANTLR AST to our own IR tree
 *
 * Errors that the parser should catch throw IllegalStateException. If this class is throwing an IllegalStateException
 * something is very wrong.
 *
 * We are checking for the following semantic errors because they have to be checked in this class:
 * V1. Duplicate symbols
 * V2. We are checking whether an array declaration has both an initializer and a declared size, or neither
 * V3  Int literal in array declaration must be greater than 0
 * V4. Void parameters
 * V5. Integer literal overflows and underflows
 * V6. ++ and -- are converted to += 1 and -= 1, respectively
 */
object DecafIrVisitor {

  /**
   * Contains the error state for the visitor
   */
  private var isErrored: Boolean = false

  private var curScopeType: ScopeType = null;
  private var curMethodIdentifier: Option[String] = None;

  private var removeTernary: Boolean = false;

  private var globalScope: IrScope = null;
  private var ternaryFieldDeclarations: ListBuffer[IrFieldDecl] = ListBuffer.empty
  private var continueStack = mutable.Stack[List[IrStatement]]()


  /**
   * Write an error to the error console and change the isErrored flag
   *
   * @param errorStr     String to print out
   * @param codeLocation Code location
   */
  private def writeError(errorStr: String, codeLocation: CodeLocation): Unit = {
    Console.err.println(s"${codeLocation.lineNumber}:${codeLocation.colNumber} $errorStr")
    isErrored = true
  }

  def resetErrored(): Unit = {
    isErrored = false
    curScopeType = null
    curMethodIdentifier = None
    ternaryFieldDeclarations = ListBuffer.empty
    globalScope = null
    continueStack = mutable.Stack[List[IrStatement]]()
  }

  def getErrored: Boolean = {
    isErrored
  }

  /**
   * Get the line and column number of the ParserRuleContext from ANTLR
   *
   * @param ctx Parser Rule Context
   * @return CodeLocation
   */
  private def getCodeLocation(ctx: ParserRuleContext): CodeLocation = {
    CodeLocation(ctx.getStart.getLine, ctx.getStart.getCharPositionInLine)
  }

  /**
   * Main program entry point
   *
   * program: import_decl* field_decl* method_decl* EOF;
   */
  def visitProgram(ctx: DecafParser.ProgramContext, removeTernary: Boolean): IrProgram = {
    DecafIrVisitor.removeTernary = removeTernary
    val scope: IrScope = IrScope(mutable.HashMap.empty[String, IrMemberDecl], mutable.HashMap.empty[String, Int],
      ScopeType.PROGRAM, None)

    DecafIrVisitor.globalScope = scope

    val importDecl: List[IrImportDecl] = ctx.import_decl().asScala.map(importCtx => {
      visitImport_decl(importCtx)
    }).toList

    // Each FieldDecl statement can contain multiple FieldDecl, and therefore needs to be unwrapped
    val fieldDecl: ListBuffer[IrFieldDecl] = ListBuffer.empty
    ctx.field_decl().asScala.map(fieldCtx => {
      visitField_decl(fieldCtx)
    }).foreach(containers => {
      containers.fields.foreach(decl => {
        fieldDecl.append(decl)
      })
    })

    val methodDecl: List[IrFunctionDecl] = ctx.method_decl().asScala.map(methodCtx => {
      visitMethod_decl(methodCtx)
    }).toList

    // Add the imports and field declarations to the scope
    importDecl.foreach(importDecl => {
      if (scope.symbols.contains(importDecl.identifier)) {
        writeError(s"Duplicate identifier ${importDecl.identifier}", importDecl.location)
      } else {
        scope.symbols.put(importDecl.identifier, importDecl)
        scope.order.put(importDecl.identifier, 0)
      }
    })

    fieldDecl.foreach(fieldDecl => {
      if (scope.symbols.contains(fieldDecl.identifier)) {
        writeError(s"Duplicate identifier ${fieldDecl.identifier}", fieldDecl.location)
      } else {
        scope.symbols.put(fieldDecl.identifier, fieldDecl)
        scope.order.put(fieldDecl.identifier, 1)
      }
    })

    var methodIdx = 2
    methodDecl.foreach(methodDecl => {
      if (scope.symbols.contains(methodDecl.identifier)) {
        writeError(s"Duplicate identifier ${methodDecl.identifier}", methodDecl.location)
      } else {
        scope.symbols.put(methodDecl.identifier, methodDecl)
        scope.order.put(methodDecl.identifier, methodIdx)
        methodIdx += 1
      }
    })

    IrProgram(
      importDecl,
      fieldDecl.toList ::: ternaryFieldDeclarations.toList,
      methodDecl,
      scope,
      getCodeLocation(ctx)
    )
  }

  /*
   * import_decl: IMPORT IDENTIFIER SEMI;
   */
  def visitImport_decl(ctx: DecafParser.Import_declContext): IrImportDecl = {
    IrImportDecl(
      ctx.IDENTIFIER().getText,
      getCodeLocation(ctx)
    )
  }


  /*
  field_decl: type (
      field_decl_sub
      (COMMA field_decl_sub)*
      SEMI
  );
   */
  def visitField_decl(ctx: DecafParser.Field_declContext): IrFieldDeclContainer = {
    val declType: Type = ctx.`type`().getChild(0) match {
      case node: TerminalNode =>
        node.getSymbol.getType match {
          case DecafParser.INT => Type.INT
          case DecafParser.BOOL => Type.BOOL
          case _ =>
            throw new IllegalStateException("Expected INT or BOOL token for field decl type")
        }
      case _ =>
        throw new IllegalStateException("Expected TerminalNode for type when visiting field decl")
    }

    val declList = ctx.field_decl_sub().asScala.map(fieldDecl => {
      visitField_decl_sub(fieldDecl)
    }).toList

    val newDeclList = declList.map(decl => {
      if (declType == Type.BOOL) {
        decl.declType match {
          case Type.INT => IrRegFieldDecl(
            declType = Type.BOOL,
            identifier = decl.identifier,
            initializer = decl.initializer,
            location = decl.location
          )
          case Type.INT_ARR => IrArrayFieldDecl(
            declType = Type.BOOL_ARR,
            identifier = decl.identifier,
            initializer = decl.initializer,
            length = decl.asInstanceOf[IrArrayFieldDecl].length,
            location = decl.location
          )
          case _ => decl;
        }
      } else {
        decl
      }
    })

    new IrFieldDeclContainer(newDeclList, getCodeLocation(ctx))
  }


  /*
    (IDENTIFIER field_decl_arr? (EQUAL initializer)?)
   */
  def visitField_decl_sub(ctx: DecafParser.Field_decl_subContext): IrFieldDecl = {
    val identifier: String = ctx.IDENTIFIER().getText

    var initializer: Option[IrInitializer] = None
    var hasInitializer = false
    var hasDeclaredSize = false
    var size : Long = -1

    if (ctx.initializer() != null) {
      initializer = Some(visitInitializer(ctx.initializer()))
      hasInitializer = true

      initializer.get.literal match {
        case arrayLiteral: IrArrayLiteral =>
          size = arrayLiteral.value.size
        case _ =>
      }
    }

    if (ctx.field_decl_arr() != null) {
      // It is an array
      // Check for a declared size

      if (ctx.field_decl_arr().int_literal() != null) {

        size = ctx.field_decl_arr().int_literal().getText.toLong
        if (size <= 0) {
          writeError("Array size declaration must be greater than 0", getCodeLocation(ctx))
        }
        hasDeclaredSize = true
      }

      if (hasInitializer && hasDeclaredSize) {
        writeError("Array must not have both an initializer and declared size", getCodeLocation(ctx))
      }

      if (!hasInitializer && !hasDeclaredSize) {
        writeError("Array must have either an initalizer or declared size", getCodeLocation(ctx))
      }

      IrArrayFieldDecl(
        declType = Type.INT_ARR, // Guess this is an INT_ARR for right now, we'll fix it if it's actually wrong
        identifier = identifier,
        initializer = initializer,
        length = size,
        location = getCodeLocation(ctx)
      )
    } else {
      IrRegFieldDecl(
        declType = Type.INT, // Guess this is an INT for right now, we'll fix it if it's actually wrong
        identifier = identifier,
        initializer = initializer,
        location = getCodeLocation(ctx)
      )
    }
  }

  /*
    literal: (SUB)? (int_literal | CHAR_LITERAL | BOOL_LITERAL);
   */
  def visitLiteral(ctx: DecafParser.LiteralContext): IrLiteral = {

    if (ctx.int_literal() != null) {
      var intText = ctx.int_literal().getText
      if (ctx.SUB() != null) {
        intText = "-" + intText;
      }

      try {
        var value = -999L

        if (intText.startsWith("0x")){
          // Positive hex literal
          value = java.lang.Long.parseLong(intText.substring(2), 16)
        } else if (intText.startsWith("-0x")){
          value = java.lang.Long.parseLong(intText.substring(3), 16) * -1L
        } else {
          value = intText.toLong
        }

        IrIntLiteral(
          value = value, location = getCodeLocation(ctx)
        )
      } catch {
        case nfe: NumberFormatException => {
          writeError("Overflow or invalid integer literal", getCodeLocation(ctx))
          // Allows the code to continue, but marks the error
          IrIntLiteral(
            value = -999, location = getCodeLocation(ctx)
          )
        }
        case _ => {
          throw new IllegalStateException("Int literal parsing did not match valid exception handling")
        }
      }
    } else if (ctx.CHAR_LITERAL() != null) {
      // Removes the single quotes
      var string = ctx.CHAR_LITERAL().getText.substring(1)
      string = string.substring(0, string.length() - 1)

      var value = string match {
        case "\\\\" => '\\'.toInt
        case "\\t" => '\t'.toInt
        case "\\f" => '\f'.toInt
        case "\\n" => '\n'.toInt
        case "\\r" => '\r'.toInt
        case "\\\"" => '\"'.toInt
        case "\\\'" => '\''.toInt
        case _ => string.charAt(0).toInt
      }

      IrIntLiteral(
        value = value, location = getCodeLocation(ctx)
      )
    } else if (ctx.BOOL_LITERAL() != null) {
      var value = ctx.BOOL_LITERAL().getText == "true"

      IrBoolLiteral(
        value = value, location = getCodeLocation(ctx)
      )
    } else {
      throw new IllegalStateException("Expected int, char, or bool literal.")
    }
  }

  /*
    array_literal: LCURLY (literal) (COMMA literal)* RCURLY
   */
  def visitArray_literal(ctx: DecafParser.Array_literalContext): IrArrayLiteral = {
    IrArrayLiteral(ctx.literal().asScala.map(literal => {
      visitLiteral(literal)
    }).toList, getCodeLocation(ctx))
  }

  /*
    initializer: literal | array_literal;
   */
  def visitInitializer(ctx: DecafParser.InitializerContext): IrInitializer = {
    if (ctx.literal() != null) {
      IrInitializer(visitLiteral(ctx.literal()), getCodeLocation(ctx))
    } else if (ctx.array_literal() != null) {
      IrInitializer(visitArray_literal(ctx.array_literal()), getCodeLocation(ctx))
    } else {
      throw new IllegalStateException("Expected literal or array literal when visiting initializer, got none")
    }
  }

  /*
    (type | VOID) IDENTIFIER LPAREN (decl_parameter (COMMA decl_parameter)*)? RPAREN block;
   */
  def visitMethod_decl(ctx: DecafParser.Method_declContext): IrFunctionDecl = {
    var declType: Type = null
    val scope: IrScope = IrScope(mutable.HashMap.empty[String, IrMemberDecl],
      mutable.HashMap.empty[String, Int],
      ScopeType.METHOD_PARAMETERS,
      Some(ctx.IDENTIFIER().getText))

    if (ctx.VOID() != null) {
      declType = Type.VOID
    } else {
      declType = ctx.`type`().getChild(0).asInstanceOf[TerminalNode].getSymbol.getType match {
        case DecafParser.INT => Type.INT
        case DecafParser.BOOL => Type.BOOL
        case _ => throw new IllegalStateException("Expected INT or BOOL for field decl type")
      }
    }

    val identifier = ctx.IDENTIFIER().getText
    val parameters: List[IrParameterDecl] = ctx.decl_parameter().asScala.map(paramCtx => {
      visitDecl_parameter(paramCtx)
    }).toList

    parameters.foreach(declParameter => {
      if (scope.symbols.contains(declParameter.identifier)) {
        writeError(s"Duplicate identifier ${declParameter.identifier}", declParameter.location)
      } else {
        scope.symbols.put(declParameter.identifier, declParameter)
      }
    })

    curScopeType = ScopeType.METHOD_BODY
    curMethodIdentifier = Some(ctx.IDENTIFIER().getText)
    val block = visitBlock(ctx.block())

    // Check for method body and method parameters have the same
    block.scope.symbols.keySet.foreach(id => {
      if (scope.symbols.contains(id)){
        writeError("Cannot have duplicate identifiers in the method parameters and method body", getCodeLocation(ctx));
      }
    });


    IrFunctionDecl(
      declType = declType,
      identifier = identifier,
      parameters = parameters,
      block = block,
      scope = scope,
      location = getCodeLocation(ctx)
    )
  }

  /*
    block: LCURLY field_decl* statement* RCURLY;
   */
  def visitBlock(ctx: DecafParser.BlockContext): IrBlock = {
    val scope: IrScope = IrScope(mutable.HashMap.empty[String, IrMemberDecl], mutable.HashMap.empty[String, Int],
      curScopeType, curMethodIdentifier)

    val fieldDeclarations: ListBuffer[IrFieldDecl] = ListBuffer.empty

//    // TODO: Remove by moving to Cfg
//    val fieldStatements: ListBuffer[IrStatement] = ListBuffer.empty

    ctx.field_decl().asScala.map(fieldCtx => {
      visitField_decl(fieldCtx)
    }).foreach(containers => {
      containers.fields.foreach(decl => {
        fieldDeclarations.append(decl)
      })
    })
    fieldDeclarations.foreach(fieldDecl => {
      if (scope.symbols.contains(fieldDecl.identifier)) {
        writeError(s"Duplicate identifier ${fieldDecl.identifier}", fieldDecl.location)
      } else {
        scope.symbols.put(fieldDecl.identifier, fieldDecl)

        // FIX FOR LOOPS (will fix in CFG)
//        var assignExpr: IrExpression = null;
//        if (fieldDecl.initializer.isDefined){
//          fieldDecl.initializer.get.literal match {
//            case IrIntLiteral(value, location) => assignExpr = IrIntLiteral(value, location)
//            case IrBoolLiteral(value, location) => assignExpr = IrBoolLiteral(value, location)
//            case IrArrayLiteral(value, location) => {}
//          }
//        }else{
//          fieldDecl.declType match {
//            case Type.INT => assignExpr = IrIntLiteral(0L, getCodeLocation(ctx))
//            case Type.BOOL => assignExpr = IrBoolLiteral(false, getCodeLocation(ctx))
//            case Type.INT_ARR => {}
//            case Type.BOOL_ARR => {}
//            case Type.VOID => throw new IllegalStateException()
//          }
//        }
//        if (assignExpr != null){
//          fieldStatements.append(IrAssignStatement(
//            assignLocation = IrLocation(fieldDecl.identifier, None, getCodeLocation(ctx)),
//            assignOp = AssignOpType.EQUAL,
//            assignExpr = assignExpr,
//            location = getCodeLocation(ctx)
//          ))
//        }
        // END FIX FOR LOOPS
      }
    })


    val statements = ctx.statement().asScala.map(statementCtx => {
      visitStatement(statementCtx)
    }).toList

    val finalStatements: ListBuffer[IrStatement] = ListBuffer.empty
    statements.foreach(subList => {
      subList.foreach(elem => {
        finalStatements.append(elem)
      })
    })

    IrBlock(
      fields = fieldDeclarations.toList,
      statements = finalStatements.toList,
      scope = scope,
      location = getCodeLocation(ctx)
    )
  }

  /*
    decl_parameter: (type IDENTIFIER);
   */
  def visitDecl_parameter(ctx: DecafParser.Decl_parameterContext): IrParameterDecl = {
    val declType = ctx.`type`().getChild(0).asInstanceOf[TerminalNode].getSymbol.getType match {
      case DecafParser.INT => Type.INT
      case DecafParser.BOOL => Type.BOOL
      case _ => throw new IllegalStateException("Expected INT or BOOL for parameter decl type")
    }
    IrParameterDecl(
      declType = declType,
      identifier = ctx.IDENTIFIER().getText,
      location = getCodeLocation(ctx)
    )
  }


  /*
  statement:
      assign_statement |
      method_call_statement |
      if_statement |
      for_statement |
      while_statement |
      return_statement |
      break_statement |
      continue_statement;
   */
  def visitStatement(ctx: DecafParser.StatementContext): List[IrStatement] = {
    var returnedStatement: List[IrStatement] = null;

    if (ctx.assign_statement() != null) {
      returnedStatement = visitAssign_statement(ctx.assign_statement())
    }

    if (ctx.method_call_statement() != null) {
      returnedStatement = visitMethod_call_statement(ctx.method_call_statement())
    }

    if (ctx.if_statement() != null) {
      returnedStatement = visitIf_statement(ctx.if_statement())
    }

    if (ctx.for_statement() != null) {
      returnedStatement = visitFor_statement(ctx.for_statement())
    }

    if (ctx.while_statement() != null) {
      returnedStatement = visitWhile_statement(ctx.while_statement())
    }

    if (ctx.return_statement() != null) {
      returnedStatement = visitReturn_statement(ctx.return_statement())
    }

    if (ctx.break_statement() != null) {
      returnedStatement = visitBreak_statement(ctx.break_statement())
    }

    if (ctx.continue_statement() != null) {
      returnedStatement = visitContinue_statement(ctx.continue_statement())
    }


    if (returnedStatement != null){
      return returnedStatement
    }else{
      throw new IllegalStateException("A statement must be matched")
    }
  }

  /*
    assign_statement: location assign_expr SEMI;
   */
  def visitAssign_statement(ctx: DecafParser.Assign_statementContext): List[IrStatement] = {
    var assignOp: AssignOpType = null
    var assignStatements: List[IrStatement] = List.empty
    var assignExpr: IrExpression = null

    if (ctx.assign_expr().increment() != null) {
      // handle increment/ decrement operators
      val increment = ctx.assign_expr().increment()
      if (increment.INC() != null) {
        assignOp = AssignOpType.ADD_EQUAL
        assignExpr = IrIntLiteral(1, getCodeLocation(ctx.assign_expr().increment()))
      } else if (increment.DEC() != null) {
        assignOp = AssignOpType.SUB_EQUAL
        assignExpr = IrIntLiteral(1, getCodeLocation(ctx.assign_expr().increment()))
      } else {
        throw new IllegalStateException("Expected either increment or decrement operator when visiting assign expr, got none")
      }
    } else if (ctx.assign_expr().assign_op() != null) {
      // handle =, +=, -=
      val assign = ctx.assign_expr().assign_op()
      val assignResult = visitExpr(ctx.assign_expr().expr())
      assignStatements = assignResult._1
      assignExpr = assignResult._2
      if (assign.EQUAL() != null) {
        assignOp = AssignOpType.EQUAL
      } else if (assign.compound_assign_op() != null) {
        val compoundAssign = assign.compound_assign_op()
        if (compoundAssign.ADD_EQUAL() != null) {
          assignOp = AssignOpType.ADD_EQUAL
        } else if (compoundAssign.SUB_EQUAL() != null) {
          assignOp = AssignOpType.SUB_EQUAL
        } else {
          throw new IllegalStateException("Expected += or -= when visiting compound assign op, got none")
        }
      } else {
        throw new IllegalStateException("Expected assign or compound assignment operator when visiting assign expr, got none")
      }
    } else {
      throw new IllegalStateException("Expected assign operator when visiting assign expr, got none")
    }
    val locationEval = visitLocation(ctx.location())

    (locationEval._1 ::: assignStatements ::: List(IrAssignStatement(
      locationEval._2,
      assignOp,
      assignExpr,
      getCodeLocation(ctx)
    )))
  }

  /*
    method_call_statement: method_call SEMI;
   */
  def visitMethod_call_statement(ctx: DecafParser.Method_call_statementContext): List[IrStatement] = {
    val statements: ListBuffer[IrStatement] = ListBuffer.empty;

    val params: List[MethodCallParam] = ctx.method_call().import_arg().asScala.map(arg => {
      if (arg.expr() != null) {
        val result = visitExpr(arg.expr())
        result._1.foreach(x => statements.append(x))

        FunctionCallExprParam(
          result._2,
          getCodeLocation(arg)
        )
      } else if (arg.STRING_LITERAL() != null) {
        var cleanText = arg.STRING_LITERAL().getText.substring(1)
        cleanText = cleanText.substring(0, cleanText.length() - 1)
        FunctionCallStringParam(
          cleanText,
          getCodeLocation(arg)
        )
      } else {
        throw new IllegalStateException("Expected either string literal or expr when visiting method call param, found none")
      }
    }).toList

    statements.toList ::: List(IrFunctionCallStatement(
      ctx.method_call().IDENTIFIER().getText,
      params,
      getCodeLocation(ctx)
    ))
  }

  /*
    if_statement: IF LPAREN expr RPAREN block (ELSE block)?;
   */
  def visitIf_statement(ctx: DecafParser.If_statementContext): List[IrStatement] = {
    var elseBlock: Option[IrBlock] = None
    curScopeType = ScopeType.IF_ELSE
    if (ctx.block().size() == 2) {
      elseBlock = Some(visitBlock(ctx.block(1)))
    }

    val eval = visitExpr(ctx.expr())

    eval._1 ::: List(IrIfStatement(
      conditional = eval._2,
      thenBlock = visitBlock(ctx.block(0)),
      elseBlock = elseBlock,
      location = getCodeLocation(ctx)
    ))
  }

  /*
    for_statement: FOR LPAREN IDENTIFIER EQUAL expr SEMI expr SEMI for_update RPAREN block;
   */
  def visitFor_statement(ctx: DecafParser.For_statementContext): List[IrStatement] = {
    curScopeType = ScopeType.FOR

    val declEval = visitExpr(ctx.expr(0))
    val conditionalEval = visitExpr(ctx.expr(1))
    val forEval = visitFor_update(ctx.for_update())

    var updateAndThenEvalStatements: List[IrStatement] = List.empty
    var declAssignment: List[IrStatement] = List.empty

    // If any of the conditions (i.e. conditionalEval or forEval)
    // contain blocks, we need to de-sugar the forUpdate and convert this (essentially) to a while loop
    // This is because "continue" will not work without this code in the middle somewhere
    if (conditionalEval._1.isEmpty && forEval._1.isEmpty){
      // push empty
      continueStack.push(List.empty)
    } else {
      // push the update necessary
      declAssignment = List(
        IrAssignStatement(
          assignLocation = IrLocation(ctx.IDENTIFIER().getText, None, getCodeLocation(ctx)),
          assignOp = AssignOpType.EQUAL,
          assignExpr = declEval._2,
          location = getCodeLocation(ctx)
        )
      )

      updateAndThenEvalStatements =
        forEval._1 ::: List(
        IrAssignStatement(
          assignLocation = forEval._2.identifier,
          assignOp = forEval._2.compoundAssignOp,
          assignExpr = forEval._2.expr,
          location = forEval._2.location
        )
      ) ::: conditionalEval._1
      continueStack.push(updateAndThenEvalStatements)
    }

    val block = visitBlock(ctx.block())
    continueStack.pop()

    if (conditionalEval._1.isEmpty && forEval._1.isEmpty){
      declEval._1 ::: List(IrForStatement(
        identifier = ctx.IDENTIFIER().getText,
        declExpr = declEval._2,
        conditional = conditionalEval._2,
        forUpdate = forEval._2,
        forBlock = block,
        location = getCodeLocation(ctx)
      ))
    } else {
      // We need to put the rest of the update statements into the body
      val newBlock = block.copy(
        statements = block.statements ::: updateAndThenEvalStatements
      )

      declEval._1 ::: declAssignment ::: forEval._1 ::: conditionalEval._1 ::: List(
        IrWhileStatement(
          conditional = conditionalEval._2,
          whileBlock = newBlock,
          location = getCodeLocation(ctx)
        )
      )
    }
  }

  /*
    while_statement: WHILE LPAREN expr RPAREN block;
   */
  def visitWhile_statement(ctx: DecafParser.While_statementContext): List[IrStatement] = {
    curScopeType = ScopeType.WHILE

    val conditionalEval = visitExpr(ctx.expr())

    if (conditionalEval._1.isEmpty){
      continueStack.push(List.empty)
    } else {
      continueStack.push(conditionalEval._1)
    }
    val block = visitBlock(ctx.block())
    continueStack.pop()

    if (conditionalEval._1.isEmpty){
      List(IrWhileStatement(
        conditional = conditionalEval._2,
        whileBlock = block,
        location = getCodeLocation(ctx)
      ))
    } else {
      val newBlock = block.copy(
        statements = block.statements ::: conditionalEval._1
      )

      conditionalEval._1 ::: List(IrWhileStatement(
        conditional = conditionalEval._2,
        whileBlock = newBlock,
        location = getCodeLocation(ctx)
      ))
    }
  }

  /*
    return_statement: RETURN expr? SEMI;
   */
  def visitReturn_statement(ctx: DecafParser.Return_statementContext): List[IrStatement] = {
    if (ctx.expr() != null){
      val eval = visitExpr(ctx.expr())
      eval._1 ::: List(IrReturnStatement(expr = Some(eval._2), location = getCodeLocation(ctx)))
    } else {
      List(IrReturnStatement(expr = None, location = getCodeLocation(ctx)))
    }
  }

  /*
    break_statement: BREAK SEMI;
   */
  def visitBreak_statement(ctx: DecafParser.Break_statementContext): List[IrStatement] = {
    List(IrBreakStatement(getCodeLocation(ctx)))
  }

  /*
    continue_statement: CONTINUE SEMI;
   */
  def visitContinue_statement(ctx: DecafParser.Continue_statementContext): List[IrStatement] = {
    if (removeTernary){
      continueStack.top ::: List(IrContinueStatement(getCodeLocation(ctx)))
    }else{
      List(IrContinueStatement(getCodeLocation(ctx)))
    }
  }

  /*
    for_update: location (compound_assign_op expr | increment);
   */
  def visitFor_update(ctx: DecafParser.For_updateContext): (List[IrStatement], IrForUpdate) = {
    var assignOp: AssignOpType = null
    var expr: IrExpression = null

    var evalStatements: List[IrStatement] = List.empty

    if (ctx.compound_assign_op() != null) {
      val eval = visitExpr(ctx.expr())

      if (ctx.compound_assign_op().ADD_EQUAL() != null) {
        assignOp = AssignOpType.ADD_EQUAL
        evalStatements = eval._1
        expr = eval._2
      }
      if (ctx.compound_assign_op().SUB_EQUAL() != null) {
        assignOp = AssignOpType.SUB_EQUAL
        evalStatements = eval._1
        expr = eval._2
      }
    }

    // Reducing all increments (++ and --) to += 1 and -= 1
    if (ctx.increment() != null) {
      if (ctx.increment().INC() != null) {
        assignOp = AssignOpType.ADD_EQUAL
        expr = IrIntLiteral(value = 1, getCodeLocation(ctx.increment()))
      }

      if (ctx.increment().DEC() != null) {
        assignOp = AssignOpType.SUB_EQUAL
        expr = IrIntLiteral(value = 1, getCodeLocation(ctx.increment()))
      }
    }

    if (assignOp == null || expr == null) {
      throw new IllegalStateException("Must be += or -= in for_update block")
    }

    val locationEval = visitLocation(ctx.location())

    (locationEval._1 ::: evalStatements, IrForUpdate(
      identifier = locationEval._2,
      compoundAssignOp = assignOp,
      expr = expr,
      location = getCodeLocation(ctx)
    ))
  }

  /*
    location: IDENTIFIER | (IDENTIFIER LSQUARE expr RSQUARE);
   */
  def visitLocation(ctx: DecafParser.LocationContext): (List[IrStatement], IrLocation) = {
    var index: Option[IrExpression] = None
    var statements: List[IrStatement] = List.empty
    if (ctx.expr() != null) {
      val eval = visitExpr(ctx.expr())
      statements = eval._1
      index = Some(eval._2)
    }

    (statements, IrLocation(
      identifier = ctx.IDENTIFIER().getText,
      index = index,
      location = getCodeLocation(ctx)
    ))
  }

  private var ternaryCounter = 0;
  def _getTernaryName(): String = {
    ternaryCounter += 1
    return s"_.tern_t${ternaryCounter}"
  }

  /*
   * expr:
   * location |
   * method_call |
   * literal |
   * LEN LPAREN IDENTIFIER RPAREN |
   * LPAREN expr RPAREN | // Paren
   * SUB expr | // Unary minus
   * NOT expr | // Not
   * expr mul_op expr | // Bin_op in order
   * expr add_op expr |
   * expr rel_op expr |
   * expr eq_op expr  |
   * expr cond_op expr |
   * expr QUES expr COLON expr; // Ternary
   *
   */
  def visitExpr(ctx: DecafParser.ExprContext): (List[IrStatement], IrExpression) = {
    val codeLocation = getCodeLocation(ctx)

    // location
    if (ctx.location() != null) {
      val locationEval = visitLocation(ctx.location())

      return (locationEval._1, IrVarReadExpr(
        locationEval._2,
        codeLocation
      ))
    }

    // method call
    if (ctx.method_call() != null) {
      return visitMethod_call(ctx.method_call())
    }

    // literal
    if (ctx.literal() != null) {
      return (List.empty, visitLiteral(ctx.literal()))
    }

    // len
    if (ctx.LEN() != null) {
      if (ctx.LPAREN() != null && ctx.IDENTIFIER() != null && ctx.RPAREN() != null) {
        return (List.empty, IrLenExpr(ctx.IDENTIFIER().getText, codeLocation))
      }
      throw new IllegalStateException(
        "Expected LPAREN, IDENTIFIER, RPAREN for LEN call while visiting expr, but some are missing")
    }

    // nested expr
    if (ctx.LPAREN() != null && ctx.RPAREN() != null) {
      val result = visitExpr(ctx.expr(0));
      return (result._1, result._2)
    }

    // sub expr
    if (ctx.SUB() != null) {
      val result = visitExpr(ctx.expr(0))

      return (result._1,
        IrUnaryOpExpr(
        UnaryOpType.UMINUS, result._2, codeLocation
      ))
    }

    // not expr
    if (ctx.NOT() != null) {
      val result = visitExpr(ctx.expr(0))

      return (result._1, IrUnaryOpExpr(UnaryOpType.NOT, result._2, codeLocation))
    }

    // BinOps
    if (ctx.mul_op() != null) {
      val expr1 = visitExpr(ctx.expr(0))
      val expr2 = visitExpr(ctx.expr(1))

      return (expr1._1 ::: expr2._1, IrBinOpExpr(
        visitMul_op(ctx.mul_op()).op, expr1._2, expr2._2, codeLocation
      ))
    }
    if (ctx.add_op() != null) {
      val expr1 = visitExpr(ctx.expr(0))
      val expr2 = visitExpr(ctx.expr(1))

      return (expr1._1 ::: expr2._1, IrBinOpExpr(
        visitAdd_op(ctx.add_op()).op, expr1._2, expr2._2, codeLocation
      ))
    }
    if (ctx.rel_op() != null) {
      val expr1 = visitExpr(ctx.expr(0))
      val expr2 = visitExpr(ctx.expr(1))

      return (expr1._1 ::: expr2._1, IrBinOpExpr(
        visitRel_op(ctx.rel_op()).op, expr1._2, expr2._2, codeLocation
      ))
    }
    if (ctx.eq_op() != null) {
      val expr1 = visitExpr(ctx.expr(0))
      val expr2 = visitExpr(ctx.expr(1))

      return (expr1._1 ::: expr2._1, IrBinOpExpr(
        visitEq_op(ctx.eq_op()).op, expr1._2, expr2._2, codeLocation
      ))
    }

    if (ctx.AND() != null){
      val expr1 = visitExpr(ctx.expr(0))
      val expr2 = visitExpr(ctx.expr(1))

      return (expr1._1 ::: expr2._1,
          IrBinOpExpr(
            LogicalBinOpType.AND, expr1._2, expr2._2, codeLocation
          )
      )
    }

    if (ctx.OR() != null) {
      val expr1 = visitExpr(ctx.expr(0))
      val expr2 = visitExpr(ctx.expr(1))

      return (expr1._1 ::: expr2._1,
        IrBinOpExpr(
          LogicalBinOpType.OR, expr1._2, expr2._2, codeLocation
        )
      )
    }

    // ternary
    if (ctx.QUES() != null && ctx.COLON() != null) {
      if (!removeTernary){
        // If there aren't ternaries, there will never be statements, so this works
        return (List.empty,
          IrTernaryExpr(visitExpr(ctx.expr(0))._2, visitExpr(ctx.expr(1))._2, visitExpr(ctx.expr(2))._2, codeLocation))
      }

      val expr1 = visitExpr(ctx.expr(0))
      val expr2 = visitExpr(ctx.expr(1))
      val expr3 = visitExpr(ctx.expr(2))

      val ternaryResultTemporary = _getTernaryName()
      val ternaryConditionalTemporary = _getTernaryName()

      val decl_result = IrRegFieldDecl(
        // For now, we will assume int
        declType = Type.INT,
        identifier = ternaryResultTemporary,
        initializer = None,
        location = codeLocation
      )

      val decl_condition = IrRegFieldDecl(
        declType = Type.BOOL,
        identifier = ternaryConditionalTemporary,
        initializer = None,
        location = codeLocation
      )

      globalScope.symbols.put(ternaryResultTemporary, decl_result)
      globalScope.symbols.put(ternaryConditionalTemporary, decl_condition)
      ternaryFieldDeclarations.append(decl_result)
      ternaryFieldDeclarations.append(decl_condition)

      val statements: ListBuffer[IrStatement] = ListBuffer.empty

      statements.append(
        IrAssignStatement(
          assignLocation = IrLocation(ternaryResultTemporary, None, codeLocation),
          assignOp = AssignOpType.EQUAL,
          assignExpr = IrIntLiteral(0L, codeLocation),
          location = codeLocation
        )
      )

      expr1._1.foreach(x => {statements.append(x)})

      statements.append(
        IrAssignStatement(
          assignLocation = IrLocation(ternaryConditionalTemporary, None, codeLocation),
          assignOp = AssignOpType.EQUAL,
          assignExpr = expr1._2,
          location = codeLocation
        )
      )

//      expr2._1.foreach(x => {statements.append(x)})
//      expr3._1.foreach(x => {statements.append(x)})

      statements.append(IrIfStatement(
        conditional = IrVarReadExpr(IrLocation(ternaryConditionalTemporary, None, codeLocation), codeLocation),
        thenBlock = IrBlock(
          fields = List.empty,
          statements = expr2._1 ::: List(
            IrAssignStatement(assignLocation = IrLocation(ternaryResultTemporary, None, codeLocation),
              assignOp = AssignOpType.EQUAL,
              assignExpr = expr2._2,
              location = codeLocation)
          ),
          scope = IrScope(
            symbols = mutable.HashMap.empty,
            order = mutable.HashMap.empty,
            scopeType = ScopeType.IF_ELSE,
            curMethodIdentifier = curMethodIdentifier
          ),
          location = codeLocation
        ),
        elseBlock = Some(IrBlock(
          fields = List.empty,
          statements = expr3._1 ::: List(
            IrAssignStatement(assignLocation = IrLocation(ternaryResultTemporary, None, codeLocation),
              assignOp = AssignOpType.EQUAL,
              assignExpr = expr3._2,
              location = codeLocation)
          ),
          scope = IrScope(
            symbols = mutable.HashMap.empty,
            order = mutable.HashMap.empty,
            scopeType = ScopeType.IF_ELSE,
            curMethodIdentifier = curMethodIdentifier
          ),
          location = codeLocation
        )),
        location = codeLocation
      ))

      return (
        statements.toList,
        IrVarReadExpr(
          readLocation = IrLocation(ternaryResultTemporary, None, codeLocation), location = codeLocation
        )
      )
    }

    throw new IllegalStateException("Did not match any productions while visiting expr")
  }

  /*
    This is the method call expression, not the statement

    method_call: IDENTIFIER (LPAREN (import_arg (COMMA import_arg)*)? RPAREN);
   */
  def visitMethod_call(ctx: DecafParser.Method_callContext): (List[IrStatement], IrFunctionCallExpr) = {
    val codeLocation = getCodeLocation(ctx)
    val irStatements: ListBuffer[IrStatement] = ListBuffer.empty

    val params: List[MethodCallParam] = ctx.import_arg().asScala.map(arg => {
      if (arg.expr() != null) {
        val result = visitExpr(arg.expr())
        result._1.foreach(x => irStatements.append(x))
        FunctionCallExprParam(
          result._2,
          getCodeLocation(arg)
        )
      } else if (arg.STRING_LITERAL() != null) {
        var cleanText = arg.STRING_LITERAL().getText.substring(1)
        cleanText = cleanText.substring(0, cleanText.length() - 1)
        FunctionCallStringParam(
          cleanText,
          getCodeLocation(arg)
        )
      } else {
        throw new IllegalStateException("Expected either string literal or expr when visiting method call param, found none")
      }
    }).toList

    (irStatements.toList, IrFunctionCallExpr(
      ctx.IDENTIFIER().getText,
      params,
      codeLocation
    ))
  }

  /*
    mul_op: MULT | DIV | MOD;
   */
  def visitMul_op(ctx: DecafParser.Mul_opContext): IrBinOp = {
    var op: BinOpType = null
    // exactly one of these should be null
    if (ctx.MULT() != null) {
      op = ArithBinOpType.MUL
    } else if (ctx.DIV() != null) {
      op = ArithBinOpType.DIV
    } else if (ctx.MOD() != null) {
      op = ArithBinOpType.MOD
    }
    if (op == null) {
      throw new IllegalStateException("While visiting MulOp, expected one of MUL, DIV, MOD; got none")
    }
    IrBinOp(op, getCodeLocation(ctx))
  }

  /*
    add_op: ADD | SUB;
   */
  def visitAdd_op(ctx: DecafParser.Add_opContext): IrBinOp = {
    var op: BinOpType = null
    // exactly one of these should be null
    if (ctx.ADD() != null) {
      op = ArithBinOpType.ADD
    } else if (ctx.SUB() != null) {
      op = ArithBinOpType.SUB
    }
    if (op == null) {
      throw new IllegalStateException("While visiting AddOp, expected one of ADD, SUB; got none")
    }
    IrBinOp(op, getCodeLocation(ctx))
  }

  /*
    rel_op: LT | LTE | GT | GTE;
   */
  def visitRel_op(ctx: DecafParser.Rel_opContext): IrBinOp = {
    var op: BinOpType = null
    // exactly one of these should be null
    if (ctx.GT() != null) {
      op = RelationalBinOpType.GT
    } else if (ctx.GTE() != null) {
      op = RelationalBinOpType.GTE
    } else if (ctx.LT() != null) {
      op = RelationalBinOpType.LT
    } else if (ctx.LTE() != null) {
      op = RelationalBinOpType.LTE
    }
    if (op == null) {
      throw new IllegalStateException("While visiting RelOp, expected one of LT, LTE, GT, GTE; got none")
    }
    IrBinOp(op, getCodeLocation(ctx))
  }

  /*
    eq_op: EQ | NEQ;
   */
  def visitEq_op(ctx: DecafParser.Eq_opContext): IrBinOp = {
    var op: BinOpType = null
    // exactly one of these should be null
    if (ctx.EQ() != null) {
      op = EqualityBinOpType.EQ
    } else if (ctx.NEQ() != null) {
      op = EqualityBinOpType.NEQ
    }
    if (op == null) {
      throw new IllegalStateException("While visiting EqOp, expected one of EQ, NEQ; got none")
    }
    IrBinOp(op, getCodeLocation(ctx))
  }

  /*
    cond_op: AND | OR;
   */
//  def visitCond_op(ctx: DecafParser.Cond_opContext): IrBinOp = {
//    var op: BinOpType = null
//    // exactly one of these should be null
//    if (ctx.AND() != null) {
//      op = LogicalBinOpType.AND
//    } else if (ctx.OR() != null) {
//      op = LogicalBinOpType.OR
//    }
//    if (op == null) {
//      throw new IllegalStateException("While visiting CondOp, expected one of AND, OR; got none")
//    }
//    IrBinOp(op, getCodeLocation(ctx))
//  }

}