package decaf.compile.cfg

import decaf.compile._
import decaf.compile.ir._

import scala.annotation.tailrec
import scala.collection.mutable

/**
 * Utility class for storing
 *
 * @param resolveVariable function for looking up variables in higher scopes.
 * @param getNewBlockName function for getting a new block name.
 * @param getNewTemp      function for getting a new temp variable.
 */
case class ScopeUtil(
  resolveVariable: String => CfgVariable,
  getNewBlockName: () => String,
  getNewTemp: () => CfgVariable
)

/**
 * Handles:
 * CFG creation (no more control flow structures except function calls and returns)
 * Shadowing Elimination- variables now include a scope level.
 * Honestly this should have been done in the high-level IR, but whatever.
 * Expression flattening (alongside creation of temps, etc.)
 * Type coalescing (ints merged with booleans, however arrays and non-arrays are still distinguished, see
 * visitFieldDecl)
 * Global string constants
 * Global arrays
 * Does not handle:
 * Declaration/ Assignment coalescing- I chose to leave declarations and assignments distinct since
 * I didn't think array stuff should be handled here, and declarations may include array literal
 * initialization which is seen nowhere else.
 * Arrays and array accesses- left as is
 * (however, the length of arrays should still be inferrable from the declaration of the array variable.
 * This is important for the runtime bounds check generated in the assembly code.)
 * Len of array
 * Global entry point (i.e. main function)
 */
object CfgVisitor {
  var OPTIMIZE_BRANCHES = true

  val stringData = mutable.HashMap.empty[String, String]
  var stringCount = 0
  var arrayLengths = mutable.HashMap.empty[CfgVariable, Long]

  def addStringLiteral(literalValue: String): String = {
    stringCount += 1
    val newStringId = s"_.str_$stringCount"
    stringData += (newStringId -> literalValue)
    newStringId
  }

  val prefixMap = mutable.HashMap.empty[String, Int]

  def getNewVariable(identifier: String): CfgVariable = {
    val prefixNumber = prefixMap.getOrElse(identifier, 1)
    prefixMap.update(identifier, prefixNumber + 1)
    CfgVariable(
      identifier = identifier,
      number = prefixNumber
    )
  }

  def reset(): Unit = {
    stringData.clear()
    stringCount = 0
    prefixMap.clear()
  }

  def visitProgram(irProgram: IrProgram): CfgProgram = {

    // Add external functions. Very simple process because we don't check types
    val imports: List[String] = irProgram.imports.map(imp => imp.identifier)

    // Visit all the global assignments
    val globalAssigns: List[CfgGlobalFieldDecl] = irProgram.fields.map(field =>
      visitGlobalFieldDecl(field) // global scope level = 0
    )

    // Recursively visit all functions
    // To allow searching the global scope
    val globalMap: Map[String, CfgVariable] = globalAssigns.map(
      fieldDecl => (fieldDecl.variable.identifier, fieldDecl.variable)).toMap
    val functions: List[CfgFunction] = irProgram.functions.map(functionDecl =>
      visitFunction(functionDecl, id => globalMap(id))
    )

    Program[CfgBasicBlock](imports, globalAssigns, stringData.toMap, functions)
  }

  /** *
   * Visits a field declaration. Fills in undeclared initial values with 0('s)
   * and converts literals to Long values.
   *
   * @param field the field declaration from Ir.
   * @return a CfgFieldDecl according to above.
   */
  def visitGlobalFieldDecl(field: IrFieldDecl): CfgGlobalFieldDecl = {
    field match {
      case IrRegFieldDecl(_, identifier, initializer, _) =>
        val initialValue: Long =
          if (initializer.isDefined)
            CfgVisitorUtil.irLiteralToLong(initializer.get.literal)
          else 0
        CfgGlobalRegFieldDecl(
          variable = getNewVariable(identifier),
          initialValue
        )
      case IrArrayFieldDecl(_, identifier, initializer, length, _) =>
        val arrayVariable = getNewVariable(identifier)
        arrayLengths.put(arrayVariable, length)
        CfgGlobalArrayFieldDecl(
          variable = arrayVariable,
          initialValue = initializer match {
            case Some(x) => x.literal match {
              case IrArrayLiteral(value, _) =>
                Some(value.map(literal => CfgVisitorUtil.irLiteralToLong(literal)))
              case _ =>
                throw new IllegalStateException("Expected array literal")
            }
            case None =>
              None
          },
          length = length
        )
      case _ =>
        throw new IllegalStateException("Expected regular or array field decl")
    }
  }

  /**
   * Destruct a IrFunctionDecl.
   *
   * @param irFunctionDecl the function declaration.
   * @param resolveGlobal  function for looking up global variables.
   *                       This will always be called with the identifier of a global variable.
   *                       (If this results in an error, then there is a semantic error!)
   * @return a CfgFunction.
   */
  def visitFunction(irFunctionDecl: IrFunctionDecl, resolveGlobal: String => CfgVariable): CfgFunction = {
    // function params
    val functionParamVariables = irFunctionDecl.parameters.map(parameterDecl =>
      getNewVariable(parameterDecl.identifier) // function params have scope level 1
    )

    // variable lookup
    val paramMap: Map[String, CfgVariable] = functionParamVariables.map(
      variable => (variable.identifier, variable)
    ).toMap

    def resolveVariable(id: String): CfgVariable = {
      paramMap.get(id) match {
        case Some(x) => x
        case None => resolveGlobal(id)
      }
    }

    var blockCounter = 0
    val getNewBlockName = () => {
      blockCounter += 1
      s"_.${irFunctionDecl.identifier}_$blockCounter"
    }

    val getNewTemp = () => {
      getNewVariable(s"_.t")
    }

    val scopeUtil = ScopeUtil(
      resolveVariable = resolveVariable,
      getNewBlockName = getNewBlockName,
      getNewTemp = getNewTemp
    )

    if (Compiler.OPTIMIZE_NOOP) {
      Function[CfgBasicBlock](
        identifier = irFunctionDecl.identifier,
        parameters = functionParamVariables,
        scope = CfgVisitorUtil.removeNoOp(CfgVisitorUtil.simplifyScope(
          visitBlock(
            irFunctionDecl.block,
            scopeUtil
          )
        )),
        returnsValue = irFunctionDecl.declType != Type.VOID
      )
    } else {
      Function[CfgBasicBlock](
        identifier = irFunctionDecl.identifier,
        parameters = functionParamVariables,
        scope = CfgVisitorUtil.simplifyScope(
          visitBlock(
            irFunctionDecl.block,
            scopeUtil
          )
        ),
        returnsValue = irFunctionDecl.declType != Type.VOID
      )
    }
  }


  def visitFieldDecl(decl: IrFieldDecl): (CfgFieldDecl, CfgAssignStatement) = {
    val variable = getNewVariable(decl.identifier)
    decl match {
      case IrRegFieldDecl(_, _, initializer, _) =>
        (
          CfgRegFieldDecl(variable),
          CfgRegAssignStatement(
            to = CfgLocation(
              variable = variable,
              index = None
            ),
            value = CfgImmediate(initializer match {
              case Some(x) => CfgVisitorUtil.irLiteralToLong(x.literal)
              case None => 0L
            })
          )
        )
      case IrArrayFieldDecl(_, _, initializer, length, _) =>
        arrayLengths.put(variable, length)
        (
          CfgArrayFieldDecl(variable, length),
          CfgArrayAssignStatement(
            to = CfgLocation(
              variable = variable,
              index = None
            ),
            value = initializer match {
              case Some(x) => x.literal match {
                case IrArrayLiteral(value, _) =>
                  value.map(CfgVisitorUtil.irLiteralToLong)
                case _ =>
                  throw new IllegalStateException("Expected Array literal for array field declaration.")
              }
              case None =>
                (1L to length).map(_ => 0L).toList
            }
          )
        )

      case _ =>
        throw new IllegalStateException("Expected regular or array field decl")
    }
  }

  private def _visitBlock(
    irBlock: IrBlock,
    scopeUtil: ScopeUtil,
    onStatement: (CfgScope, IrStatement, ScopeUtil) => (CfgScope, Boolean)
  ): CfgScope = {
    val fieldsAndAssigns = irBlock.fields.map(fieldDecl => visitFieldDecl(fieldDecl))
    val fields = fieldsAndAssigns.map(x => x._1)
    val assigns = fieldsAndAssigns.map(x => x._2)

    // variable lookup
    val variableMap: Map[String, CfgVariable] = fields.map(
      field => (field.variable.identifier, field.variable)
    ).toMap

    def resolveVariable(id: String): CfgVariable = {
      variableMap.get(id) match {
        case Some(x) => x
        case None => scopeUtil.resolveVariable(id)
      }
    }

    val newScopeUtil = scopeUtil.copy(
      resolveVariable = resolveVariable
    )

    val firstName = newScopeUtil.getNewBlockName()
    var scope = Scope[CfgBasicBlock](
      basicBlocks = Map(firstName -> RegularBlock[CfgStatement](fields, assigns)),
      entry = firstName,
      exit = firstName,
      regCfg = Map.empty,
      trueCfg = Map.empty,
      falseCfg = Map.empty
    )

    // loop over statements in the block
    for (statement <- irBlock.statements) {
      val (newScope, shouldContinue) = onStatement(scope, statement, newScopeUtil)
      scope = newScope
      if (!shouldContinue) {
        // to prevent overwriting exit block that is set by continue and break
        val dummyName = newScopeUtil.getNewBlockName()
        val dummyExitBlock = RegularBlock[CfgStatement](List.empty, List.empty)
        return scope.copy(
          basicBlocks = scope.basicBlocks + (dummyName -> dummyExitBlock),
          exit = dummyName
        )
      }
    }

    scope
  }

  /**
   * Destructs a block that is not within any loop.
   *
   * @param irBlock   the block to be destructed.
   *                  Must not be a block within a loop context, since this deliberately does
   *                  not handle continues and breaks.
   * @param scopeUtil scope utility methods.
   * @return the destructed scope, see `CfgScope`.
   */
  def visitBlock(
    irBlock: IrBlock,
    scopeUtil: ScopeUtil
  ): CfgScope = {
    _visitBlock(irBlock, scopeUtil, onStatement = (prevScope, statement, scopeUtil) =>
      statement match {
        case statement: IrAssignStatement =>
          (addStatementsToScope(prevScope, visitAssignStatement(statement, scopeUtil)), true)
        case statement: IrFunctionCallStatement =>
          (addStatementsToScope(prevScope, visitFunctionCall(statement, scopeUtil)), true)
        case statement: IrIfStatement =>
          (mergeScopes(prevScope, visitIf(statement, scopeUtil, visitBlock)), true)
        case statement: IrForStatement =>
          (mergeScopes(prevScope, visitFor(statement, scopeUtil)), true)
        case statement: IrWhileStatement =>
          (mergeScopes(prevScope, visitWhile(statement, scopeUtil)), true)
        case statement: IrReturnStatement =>
          (addStatementsToScope(prevScope, visitReturn(statement, scopeUtil)), true)
        case _: IrContinueStatement =>
          throw new IllegalStateException("Continue statement without a loop")
        case _: IrBreakStatement =>
          throw new IllegalStateException("Break statement without a loop")
      }
    )
  }

  /**
   * Destructs a block within a loop.
   *
   * @param irBlock    the block to be destructed.
   *                   Must be a block within a loop context.
   * @param scopeUtil  scope utility methods.
   * @param continueTo the name of the block to continue to.
   * @param breakTo    the name of the block to break to.
   * @return the destructed scope, see `CfgScope`.
   */
  def visitLoopBlock(
    irBlock: IrBlock,
    scopeUtil: ScopeUtil,
    continueTo: String,
    breakTo: String
  ): CfgScope = {
    _visitBlock(irBlock, scopeUtil, onStatement = (prevScope, statement, scopeUtil) =>
      statement match {
        case statement: IrAssignStatement =>
          (addStatementsToScope(prevScope, visitAssignStatement(statement, scopeUtil)), true)
        case statement: IrFunctionCallStatement =>
          (addStatementsToScope(prevScope, visitFunctionCall(statement, scopeUtil)), true)
        case statement: IrIfStatement =>
          (mergeScopes(prevScope, visitIf(statement, scopeUtil,
            (irBlock, scopeUtil) => visitLoopBlock(irBlock, scopeUtil, continueTo, breakTo))), true)
        case statement: IrForStatement =>
          (mergeScopes(prevScope, visitFor(statement, scopeUtil)), true)
        case statement: IrWhileStatement =>
          (mergeScopes(prevScope, visitWhile(statement, scopeUtil)), true)
        case statement: IrReturnStatement =>
          (addStatementsToScope(prevScope, visitReturn(statement, scopeUtil)), true)
        case _: IrContinueStatement =>
          (prevScope.copy(
            regCfg = prevScope.regCfg + (prevScope.exit -> continueTo)
          ), false)
        case _: IrBreakStatement =>
          (prevScope.copy(
            regCfg = prevScope.regCfg + (prevScope.exit -> breakTo)
          ), false)
      }
    )
  }

  /**
   * Postorder Traversal on the IrExpression tree to flatten to a list of CfgStatements
   *
   * Assumes that IrExpression does not have ternaries since it should have been handled in the IrVisitor
   *
   * @param irExpression Represents an expression in our IR, tree-like structure.
   * @param scope        scope information and utility functions for the scope.
   * @return a CfgScope required to expand the IrExpression
   *         and the expression that holds the actual value of the expression
   */
  def expandExpressionToExpression(
    irExpression: IrExpression,
    scope: ScopeUtil
  ): (CfgRegularBlock, CfgExpression) = {

    val cfgStatements: mutable.ListBuffer[CfgStatement] = mutable.ListBuffer.empty
    val tempToExpr: mutable.HashMap[CfgVariable, CfgExpression] = mutable.HashMap.empty

    def _visitIrExpression(irExpression: IrExpression): CfgValue = {
      irExpression match {
        case irLiteral: IrLiteral =>
          // never will be an array literal
          CfgImmediate(CfgVisitorUtil.irLiteralToLong(irLiteral))
        case IrVarReadExpr(readLocation, _) =>
          val variable: CfgVariable = scope.resolveVariable(readLocation.identifier)
          readLocation.index match {
            case Some(x) =>
              val temp: CfgVariable = scope.getNewTemp()
              val value = CfgArrayReadExpr(
                variable = variable,
                index = _visitIrExpression(x)
              )

              cfgStatements.append(
                CfgRegAssignStatement(
                  to = CfgLocation(temp, None),
                  value = value
                )
              )
              tempToExpr.update(temp, value)
              temp
            case None =>
              variable
          }
        case irFunctionCallExpr: IrFunctionCallExpr =>
          val identifier: String = irFunctionCallExpr.identifier

          val params = irFunctionCallExpr.params.map({
            case functionCallExprParam: FunctionCallExprParam =>
              CfgFunctionCallValueParam(_visitIrExpression(functionCallExprParam.param))
            case functionCallStringParam: FunctionCallStringParam =>
              CfgFunctionCallStringParam(addStringLiteral(functionCallStringParam.param))
          })

          val temp: CfgVariable = scope.getNewTemp()
          val value = CfgFunctionCallExpr(identifier, params)
          cfgStatements.append(
            CfgRegAssignStatement(
              to = CfgLocation(temp, None),
              value = value
            )
          )
          tempToExpr.update(temp, value)
          temp
        case irLenExpr: IrLenExpr =>
          val temp: CfgVariable = scope.getNewTemp()
          val arrayVariable: CfgVariable = scope.resolveVariable(irLenExpr.identifier)
//          val value = CfgLenExpr(arrayVariable)
          val value = CfgImmediate(arrayLengths(arrayVariable))
          cfgStatements.append(
            CfgRegAssignStatement(
              to = CfgLocation(temp, None),
              value = value
            )
          )
          tempToExpr.update(temp, value)
          temp
        case irUnaryOpExpr: IrUnaryOpExpr =>
          val op: UnaryOpType = irUnaryOpExpr.op
          val expr = _visitIrExpression(irUnaryOpExpr.expr)

          val temp: CfgVariable = scope.getNewTemp()
          val value = CfgUnaryOpExpr(op, expr)
          cfgStatements.append(
            CfgRegAssignStatement(
              to = CfgLocation(temp, None),
              value = value
            )
          )
          tempToExpr.update(temp, value)
          temp
        case irBinOpExpr: IrBinOpExpr =>
          val op: BinOpType = irBinOpExpr.op
          val left = _visitIrExpression(irBinOpExpr.leftExpr)
          val right = _visitIrExpression(irBinOpExpr.rightExpr)

          val temp: CfgVariable = scope.getNewTemp()
          val value = CfgBinOpExpr(op, left, right)
          cfgStatements.append(
            CfgRegAssignStatement(
              to = CfgLocation(temp, None),
              value = value
            )
          )
          tempToExpr.update(temp, value)
          temp
        case IrTernaryExpr(_, _, _, _) =>
          throw new IllegalStateException("Should not encounter ternary in CfgVisitor")
      }
    }

    val expressionValue: CfgValue = _visitIrExpression(irExpression)
    val flattenExpression: CfgExpression = expressionValue match {
      case immediate@CfgImmediate(_) => immediate
      case variable@CfgVariable(_, _) =>
        if (tempToExpr.contains(variable)) {
          cfgStatements.remove(cfgStatements.length - 1)
          tempToExpr(variable)
        } else {
          variable
        }
    }

    val block: CfgRegularBlock = RegularBlock[CfgStatement](
      fieldDecls = List.empty,
      statements = cfgStatements.toList
    )
    (block, flattenExpression)
  }

  /**
   * Postorder Traversal on the IrExpression tree to flatten to a list of CfgStatements
   *
   * Assumes that IrExpression does not have ternaries since it should have been handled in the IrVisitor
   *
   * @param irExpression Represents an expression in our IR, tree-like structure.
   * @param scope        scope information and utility functions for the scope.
   * @return a CfgScope required to expand the IrExpression
   *         and the variable that holds the actual value of the expression
   */
  def expandExpressionToValue(
    irExpression: IrExpression,
    scope: ScopeUtil
  ): (CfgRegularBlock, CfgValue) = {

    val cfgStatements: mutable.ListBuffer[CfgStatement] = mutable.ListBuffer.empty

    def _visitIrExpression(irExpression: IrExpression): CfgValue = {
      irExpression match {
        case irLiteral: IrLiteral =>
          // never will be an array literal
          CfgImmediate(CfgVisitorUtil.irLiteralToLong(irLiteral))
        case IrVarReadExpr(readLocation, _) =>
          val variable: CfgVariable = scope.resolveVariable(readLocation.identifier)
          readLocation.index match {
            case Some(x) =>
              val temp: CfgVariable = scope.getNewTemp()
              val value = CfgArrayReadExpr(
                variable = variable,
                index = _visitIrExpression(x)
              )
              cfgStatements.append(
                CfgRegAssignStatement(
                  to = CfgLocation(temp, None),
                  value = value
                )
              )
              temp
            case None =>
              variable
          }
        case irFunctionCallExpr: IrFunctionCallExpr =>
          val identifier: String = irFunctionCallExpr.identifier

          val params = irFunctionCallExpr.params.map({
            case functionCallExprParam: FunctionCallExprParam =>
              CfgFunctionCallValueParam(_visitIrExpression(functionCallExprParam.param))
            case functionCallStringParam: FunctionCallStringParam =>
              CfgFunctionCallStringParam(addStringLiteral(functionCallStringParam.param))
          })

          val temp: CfgVariable = scope.getNewTemp()
          val value = CfgFunctionCallExpr(identifier, params)
          cfgStatements.append(
            CfgRegAssignStatement(
              to = CfgLocation(temp, None),
              value = value
            )
          )
          temp
        case irLenExpr: IrLenExpr =>
          val temp: CfgVariable = scope.getNewTemp()
          val arrayVariable: CfgVariable = scope.resolveVariable(irLenExpr.identifier)
          val value = CfgImmediate(arrayLengths(arrayVariable))

//          val value = CfgLenExpr(arrayVariable)
          cfgStatements.append(
            CfgRegAssignStatement(
              to = CfgLocation(temp, None),
              value = value
            )
          )
          temp
        case irUnaryOpExpr: IrUnaryOpExpr =>
          val op: UnaryOpType = irUnaryOpExpr.op
          val expr = _visitIrExpression(irUnaryOpExpr.expr)

          val temp: CfgVariable = scope.getNewTemp()
          val value = CfgUnaryOpExpr(op, expr)
          cfgStatements.append(
            CfgRegAssignStatement(
              to = CfgLocation(temp, None),
              value = value
            )
          )
          temp
        case irBinOpExpr: IrBinOpExpr =>
          val op: BinOpType = irBinOpExpr.op
          val left = _visitIrExpression(irBinOpExpr.leftExpr)
          val right = _visitIrExpression(irBinOpExpr.rightExpr)

          val temp: CfgVariable = scope.getNewTemp()
          val value = CfgBinOpExpr(op, left, right)
          cfgStatements.append(
            CfgRegAssignStatement(
              to = CfgLocation(temp, None),
              value = value
            )
          )
          temp
        case IrTernaryExpr(_, _, _, _) =>
          throw new IllegalStateException("Should not encounter ternary in CfgVisitor")
      }
    }

    val expressionVariable: CfgValue = _visitIrExpression(irExpression)

    val block: CfgRegularBlock = RegularBlock[CfgStatement](
      fieldDecls = List.empty,
      statements = cfgStatements.toList
    )
    (block, expressionVariable)
  }

  /**
   * Deconstructs an assignment statement.
   *
   * @param statement the statement.
   * @param scope     scope utility functions.
   * @return scope containing the assignment statements
   */
  def visitAssignStatement(
    statement: IrAssignStatement, scope: ScopeUtil
  ): CfgRegularBlock = {
    val fieldDecls = mutable.ListBuffer.empty[CfgFieldDecl]
    val statements = mutable.ListBuffer.empty[CfgStatement]

    // handle the possible assignment to an array index
    val indexExprValue = statement.assignLocation.index match {
      case Some(x) =>
        val (indexExprBlock, indexExprValue) = expandExpressionToValue(x, scope)
        // append the index calculation to the expr calculation.
        fieldDecls ++= indexExprBlock.fieldDecls
        statements ++= indexExprBlock.statements
        Some(indexExprValue)
      case None => None
    }

    val to = CfgLocation(
      scope.resolveVariable(statement.assignLocation.identifier),
      index = indexExprValue
    )

    // handle the assignment expression
    val assignStatement = statement.assignOp match {
      case AssignOpType.EQUAL =>
        // expression ok
        to.index match {
          case Some(_) =>
            val (exprBlock, exprValue) = expandExpressionToValue(
              statement.assignExpr, scope
            )
            fieldDecls ++= exprBlock.fieldDecls
            statements ++= exprBlock.statements
            CfgRegAssignStatement(
              to,
              exprValue
            )
          case None =>
            val (exprBlock, exprValue) = expandExpressionToExpression(
              statement.assignExpr, scope
            )
            fieldDecls ++= exprBlock.fieldDecls
            statements ++= exprBlock.statements
            CfgRegAssignStatement(
              to,
              exprValue
            )
        }

      case op@(AssignOpType.ADD_EQUAL | AssignOpType.SUB_EQUAL) =>
        // need value rather than expression because can't nest expressions
        val (exprBlock, exprValue) = expandExpressionToValue(
          statement.assignExpr, scope
        )
        fieldDecls ++= exprBlock.fieldDecls
        statements ++= exprBlock.statements
        val addTo: CfgValue = to.index match {
          // have to make a new temp to get and hold the array index value
          case Some(_) =>
            val tempLocation = CfgLocation(scope.getNewTemp(), None)
            statements += CfgRegAssignStatement(
              tempLocation,
              CfgArrayReadExpr(to.variable, indexExprValue.get) // can reuse this
            )
            tempLocation.variable
          case None =>
            to.variable
        }
        CfgRegAssignStatement(
          to, CfgBinOpExpr(
            op match {
              case AssignOpType.ADD_EQUAL => ArithBinOpType.ADD
              case AssignOpType.SUB_EQUAL => ArithBinOpType.SUB
            },
            addTo,
            exprValue
          )
        )
    }

    statements += assignStatement

    RegularBlock[CfgStatement](
      fieldDecls.toList, statements.toList
    )
  }

  def visitFunctionCall(
    statement: IrFunctionCallStatement, scope: ScopeUtil
  ): CfgRegularBlock = {
    val fieldDecls = mutable.ListBuffer.empty[CfgFieldDecl]
    val statements = mutable.ListBuffer.empty[CfgStatement]

    val cfgParams: mutable.ListBuffer[CfgFunctionCallParam] = mutable.ListBuffer.empty
    for (param <- statement.params) {
      param match {
        case FunctionCallExprParam(param, _) =>
          val (exprBlock, exprValue) = expandExpressionToValue(param, scope)
          fieldDecls ++= exprBlock.fieldDecls
          statements ++= exprBlock.statements
          cfgParams += CfgFunctionCallValueParam(
            param = exprValue
          )
        case FunctionCallStringParam(param, _) =>
          val stringDataId = addStringLiteral(param)
          cfgParams += CfgFunctionCallStringParam(
            param = stringDataId
          )
      }
    }
    statements += CfgFunctionCallStatement(
      functionName = statement.identifier,
      parameters = cfgParams.toList
    )
    RegularBlock[CfgStatement](
      fieldDecls.toList, statements.toList
    )
  }

  def visitIf(
    statement: IrIfStatement, scope: ScopeUtil,
    visitBlock: (IrBlock, ScopeUtil) => CfgScope
  ): CfgScope = {
    val thenScope = visitBlock(statement.thenBlock, scope)
    var onlyContinue = false
    if (OPTIMIZE_BRANCHES) {
      // check for presence of continue or break as the first statement
      val firstStatements = statement.thenBlock.statements
      if (firstStatements.nonEmpty) {
        val firstStatement = firstStatements.head
        if (firstStatement.isInstanceOf[IrContinueStatement] || firstStatement.isInstanceOf[IrBreakStatement]) {
          onlyContinue = true
        }
      }
    }
    statement.elseBlock match {
      case Some(x) =>
        val elseScope = visitBlock(x, scope)
        val exitBlockName = scope.getNewBlockName()
        val exprScope = if (OPTIMIZE_BRANCHES && !onlyContinue)
          CfgVisitorUtil.shortCircuitOptimBranch(
            statement.conditional,
            thenScope.entry,
            elseScope.entry,
            fallThroughBranch = true,
            scope
          )
        else
          CfgVisitorUtil.shortCircuit(
            statement.conditional,
            thenScope.entry,
            elseScope.entry,
            scope
          )
        Scope[CfgBasicBlock](
          basicBlocks = exprScope.basicBlocks ++ thenScope.basicBlocks ++ elseScope.basicBlocks
            + (exitBlockName -> RegularBlock[CfgStatement](List.empty, List.empty)),
          entry = exprScope.entry,
          exit = exitBlockName,
          regCfg = exprScope.regCfg ++ thenScope.regCfg ++ elseScope.regCfg
            + (thenScope.exit -> exitBlockName) + (elseScope.exit -> exitBlockName),
          trueCfg = exprScope.trueCfg ++ thenScope.trueCfg ++ elseScope.trueCfg,
          falseCfg = exprScope.falseCfg ++ thenScope.falseCfg ++ elseScope.falseCfg
        )
      case None =>
        val exitBlockName = scope.getNewBlockName()
        val exprScope = if (OPTIMIZE_BRANCHES && !onlyContinue)
          CfgVisitorUtil.shortCircuitOptimBranch(
            statement.conditional,
            thenScope.entry,
            exitBlockName,
            fallThroughBranch = true,
            scope
          )
        else
          CfgVisitorUtil.shortCircuit(
            statement.conditional,
            thenScope.entry,
            exitBlockName,
            scope
          )
        Scope[CfgBasicBlock](
          basicBlocks = exprScope.basicBlocks ++ thenScope.basicBlocks
            + (exitBlockName -> RegularBlock[CfgStatement](List.empty, List.empty)),
          entry = exprScope.entry,
          exit = exitBlockName,
          regCfg = exprScope.regCfg ++ thenScope.regCfg
            + (thenScope.exit -> exitBlockName),
          trueCfg = exprScope.trueCfg ++ thenScope.trueCfg,
          falseCfg = exprScope.falseCfg ++ thenScope.falseCfg
        )
    }
  }

  private def _visitLoop(
    scope: ScopeUtil,
    condition: IrExpression,
    body: IrBlock,
    bodyAfter: (String, CfgRegularBlock)
  ): CfgScope = {
    val exitBlockName = scope.getNewBlockName()
    val exitBlock = RegularBlock[CfgStatement](List.empty, List.empty)
    val (bodyAfterBlockName, bodyBlock) = bodyAfter
    val bodyScope = visitLoopBlock(body, scope,
      continueTo = bodyAfterBlockName,
      breakTo = exitBlockName
    )
    val condScope = if (OPTIMIZE_BRANCHES)
      CfgVisitorUtil.shortCircuitOptimBranch(condition, bodyScope.entry, exitBlockName, fallThroughBranch = true, scope)
    else
      CfgVisitorUtil.shortCircuit(condition, bodyScope.entry, exitBlockName, scope)

    Scope[CfgBasicBlock](
      basicBlocks = condScope.basicBlocks ++ bodyScope.basicBlocks
        + (exitBlockName -> exitBlock) + (bodyAfterBlockName -> bodyBlock),
      entry = condScope.entry,
      exit = exitBlockName,
      regCfg = condScope.regCfg ++ bodyScope.regCfg
        + (bodyScope.exit -> bodyAfterBlockName)
        + (bodyAfterBlockName -> condScope.entry),
      trueCfg = condScope.trueCfg ++ bodyScope.trueCfg,
      falseCfg = condScope.falseCfg ++ bodyScope.falseCfg
    )
  }

  def visitFor(
    statement: IrForStatement, scope: ScopeUtil
  ): CfgScope = {
    val (initExprBlock, initExprValue) = expandExpressionToExpression(statement.declExpr, scope)
    val initVariable = scope.resolveVariable(statement.identifier)
    val initBlockName = scope.getNewBlockName()
    val initBlock = RegularBlock[CfgStatement](
      fieldDecls = initExprBlock.fieldDecls,
      statements = initExprBlock.statements :+ CfgRegAssignStatement(
        to = CfgLocation(initVariable, None),
        value = initExprValue
      )
    )

    val updateStatement = IrAssignStatement(
      assignLocation = statement.forUpdate.identifier,
      assignOp = statement.forUpdate.compoundAssignOp,
      assignExpr = statement.forUpdate.expr,
      location = statement.forUpdate.location
    )

    val updateBlock = visitAssignStatement(updateStatement, scope)
    val updateBlockName = scope.getNewBlockName()

    val loopScope = _visitLoop(
      scope,
      condition = statement.conditional,
      body = statement.forBlock,
      bodyAfter = (updateBlockName, updateBlock)
    )
    loopScope.copy(
      basicBlocks = loopScope.basicBlocks
        + (initBlockName -> initBlock),
      entry = initBlockName,
      exit = loopScope.exit,
      regCfg = loopScope.regCfg
        + (initBlockName -> loopScope.entry)
    )
  }

  def visitWhile(
    statement: IrWhileStatement, scope: ScopeUtil
  ): CfgScope = {
    _visitLoop(
      scope,
      condition = statement.conditional,
      body = statement.whileBlock,
      bodyAfter = (scope.getNewBlockName(), RegularBlock[CfgStatement](List.empty, List.empty))
    )
  }

  def visitReturn(
    statement: IrReturnStatement, scope: ScopeUtil
  ): CfgRegularBlock = {
    statement.expr match {
      case Some(x) =>
        val (exprBlock, exprValue) = expandExpressionToValue(x, scope)
        RegularBlock[CfgStatement](
          fieldDecls = exprBlock.fieldDecls,
          statements = exprBlock.statements :+ CfgReturnStatement(
            value = Some(exprValue)
          )
        )
      case None =>
        RegularBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(CfgReturnStatement(
            value = None
          ))
        )
    }
  }

  /**
   * Utility function to merge a scope with a regular block,
   * appending the statements and field declarations in the block to the exit block of the scope.
   *
   * Most useful while constructing blocks when there is a need to merge a result from a function
   * that returns a CfgRegularBlock. The partial list of statements in the scope being built can be passed in
   * as a CfgRegularBlock, and this function performs the merge, after which the caller should proceed
   * with the exit block in the returned scope.
   *
   * @param scope the cfgScope.
   * @param block the regular block to merge into entry.
   * @return new CfgScope meeting above requirements.
   */
  private def addStatementsToScope(scope: CfgScope, block: CfgRegularBlock): CfgScope = {
    val newExitBlock = scope.basicBlocks(scope.exit) match {
      case RegularBlock(fieldDecls, statements) =>
        RegularBlock[CfgStatement](
          fieldDecls ++ block.fieldDecls,
          statements ++ block.statements
        )
      case ConditionalBlock(fieldDecls, statements, condition) =>
        throw new IllegalArgumentException("attempted to add statements to conditional block")
    }
    scope.copy(
      // overwrite basic blocks with new exit block
      basicBlocks = scope.basicBlocks + (scope.exit -> newExitBlock)
    )
  }

  /**
   * Merges two scopes sequentially, such that the exit of the beforeScope is combined with the
   * entry of the afterScope.
   *
   * @param beforeScope exit block must be a regular basic block.
   * @param afterScope  scope that comes afterwards
   * @return new scope of the two merged scopes
   */
  def mergeScopes(beforeScope: CfgScope, afterScope: CfgScope): CfgScope = {
    Scope[CfgBasicBlock](
      basicBlocks = beforeScope.basicBlocks ++ afterScope.basicBlocks,
      entry = beforeScope.entry,
      exit = afterScope.exit,
      regCfg = beforeScope.regCfg ++ afterScope.regCfg + (beforeScope.exit -> afterScope.entry),
      trueCfg = beforeScope.trueCfg ++ afterScope.trueCfg,
      falseCfg = beforeScope.falseCfg ++ afterScope.falseCfg
    )
  }
}
