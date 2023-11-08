package decaf.compile.ir

import scala.collection.mutable

object DecafSemanticChecker {
  /**
   * Semantic Rules
   *
   * [x]1. No identifier is declared twice in the same scope
   * [x]2. No identifier is used before it is declared
   * [x]3. The program contains a definition for a method called `main` that has type `void` and takes no parameters
   * [x]4. All types of initializers must match the type of the variable being initialized.
   * [x]5. Array initializers have either a declared length or an initializer list, but not both.
   * [x]6. If present, the ⟨int literal⟩ in an array declaration must be greater than 0.
   * [x]7. The number and types of parameters in a method call (non-import) must be the same as the
   *      number and types of the declared parameters for the method.
   * [x]8. If a method call is used as an expression, the method must return a result.
   * [x]9. String literals and array variables may not be used as parameters to non-import methods.
   * [x]10. A `return` statement must not have a return value unless it appears in the body of a method
   *    that is declared to return a value.
   * [x]11. The expression in a `return` statement must have the same type as the declared result type
   *    of the enclosing method definition.
   * [x]12. An ⟨id⟩ used as a ⟨location⟩ must name a declared local/global variable or parameter.
   * [x]13. The ⟨id⟩ in a method statement must be a declared method or import.
   * [x]14. For all locations of the form ⟨id⟩[⟨expr⟩]:
   *    (a) ⟨id⟩ must be an array variable, and
   *    (b) the type of ⟨expr⟩ must be `int`.
   * [x]15. The argument of the `len` operator must be an array variable.
   * [x]16. The ⟨expr⟩ in an `if` or `while` statement must have type `bool` , as well as the second ⟨expr⟩
   *    of a `for` statement.
   * [x]17. The operands of the unary minus, ⟨arith op⟩s and ⟨rel op⟩s must have type `int`.
   * [x]18. The operands of ⟨eq op⟩s must have the same type, either `int` or `bool`.
   * [x]19. The operands of ⟨cond op⟩s and the operand of logical not ( ! ) must have type `bool`.
   * [x]20. The first operator in a ?: expression must have type `bool`.
   * [x]21. The second and third operators in a ?: expression must have the same type.
   * [x]22. The ⟨location⟩ and the ⟨expr⟩ in an assignment, ⟨location⟩ = ⟨expr⟩, must have the same type.
   * [x]23. The ⟨location⟩ and the ⟨expr⟩ in an incrementing/decrementing assignment, ⟨location⟩ += ⟨expr⟩
   *    and ⟨location⟩ -= ⟨expr⟩, must be of type `int`. The same is true of the ⟨location⟩ in ++
   *    and -- statements.
   * [x]24. All `break` and `continue` statements must be contained within the body of a `for` or a
   *    `while` statement.
   * [x]25. All integer literals must be in the range −9223372036854775808 ≤ x ≤ 9223372036854775807
   *    (64 bits).
   */

  private val scopeStack = mutable.Stack[IrScope]()

  /**
   * Write an error to the error console
   *
   * @param errorStr     String to print out
   * @param codeLocation Code location
   */
  private def writeError(errorStr: String, codeLocation: CodeLocation): Unit = {
    Console.err.println(s"${codeLocation.lineNumber}:${codeLocation.colNumber} $errorStr")
  }

  /**
   * Main IR checker entry point
   *
   * program: import_decl* field_decl* method_decl* EOF;
   */
  def checkProgram(irProgram: IrProgram) : Boolean =  {
    val globalScope: IrScope = irProgram.scope
    scopeStack.push(globalScope)

    val checkMainMethod: Boolean = checkMain(irProgram.location)

    var checkImportDecls: Boolean = true
    var checkFieldDecls: Boolean = true
    var checkMethodDecls: Boolean = true

    if (irProgram.imports.nonEmpty) {
      checkImportDecls = irProgram.imports.map(im => {
        checkImportDecl(im)
      }).reduce(_ && _)
    }

    if (irProgram.fields.nonEmpty) {
      checkFieldDecls = irProgram.fields.map(field => {
        checkFieldDecl(field)
      }).reduce(_ && _)
    }

    if (irProgram.functions.nonEmpty) {
      checkMethodDecls = irProgram.functions.map(method => {
        checkMethodDecl(method)
      }).reduce(_ && _)
    }

    scopeStack.pop()
    checkMainMethod && checkImportDecls && checkFieldDecls && checkMethodDecls
  }

/*
 * Program contains a definition for a method `main` that has type `void` and takes no parameters
 */
  private def checkMain(programLocation: CodeLocation): Boolean = {
    if (scopeStack.top.symbols.contains("main")) {
      val mainMethod = scopeStack.top.symbols("main")
      if (mainMethod.declType == Type.VOID) {
         mainMethod match {
           case IrFunctionDecl(declType, identifier, parameters, block, scope, location) =>
             if (parameters.isEmpty) {
               true
             } else {
               writeError("Main method should have no parameters", mainMethod.location)
               false
             }
         }
      } else {
        writeError("Main method must have type void", mainMethod.location)
        false
      }
    } else {
      writeError("Program must contain a definition for method called main", programLocation)
      false
    }
  }

  /*
   * import_decl: IMPORT IDENTIFIER SEMI;
   */
  private def checkImportDecl(irImportDecl: IrImportDecl): Boolean = {
    true
  }

  /*
  field_decl: type (
      field_decl_sub
      (COMMA field_decl_sub)*
      SEMI
  );

  All types of initializers must match the type of the variable being initialized
   */
  private def checkFieldDecl(irFieldDecl: IrFieldDecl): Boolean = {
    if (irFieldDecl.initializer.isDefined) {
      val fieldDeclType: Type = irFieldDecl.declType

      var initializerDeclType: Type = Type.VOID

      irFieldDecl.initializer.get.literal match {
        case intLiteral: IrIntLiteral => {
          initializerDeclType = Type.INT
          if (fieldDeclType != initializerDeclType) {
            writeError("Initializer type does not match field decl type", irFieldDecl.location)
            return false
          }
        }
        case boolLiteral: IrBoolLiteral => {
          initializerDeclType = Type.BOOL
          if (fieldDeclType != initializerDeclType) {
            writeError("Initializer type does not match field decl type", irFieldDecl.location)
            return false;
          }
        }
        case arrayLiteral: IrArrayLiteral => {
          if (!checkIrArrayFieldDecl(arrayLiteral, fieldDeclType)){
            return false;
          }
        }
        case _ => return false;
      }
      return true;
    } else {
      true
    }
  }

  /*
    (type | VOID) IDENTIFIER LPAREN (decl_parameter (COMMA decl_parameter)*)? RPAREN block;
   */
  private def checkMethodDecl(irMethodDecl: IrFunctionDecl): Boolean = {
    val methodDeclType: Type = irMethodDecl.declType
    val methodBlock: IrBlock = irMethodDecl.block
    val methodScope: IrScope = irMethodDecl.scope

    scopeStack.push(methodScope)
    val validMethodBlock = checkBlock(methodBlock, methodDeclType)
    scopeStack.pop()

    validMethodBlock
  }

  /*
   * All types of array initializers must match the type of the variable being initialized
   */
  private def checkIrArrayFieldDecl(arrayLiteral: IrArrayLiteral, fieldDeclType: Type): Boolean = {
    fieldDeclType match {
      case Type.INT_ARR =>
        arrayLiteral.value.foreach({
          case intLiteral: IrIntLiteral =>
          case _ => {
            writeError("Array initializer literal type does not match field decl type", arrayLiteral.location)
            return false
          }
        })
      case Type.BOOL_ARR =>
        arrayLiteral.value.foreach({
          case boolLiteral: IrBoolLiteral =>
          case _ => {
            writeError("Array initializer literal type does not match field decl type", arrayLiteral.location)
            return false
          }
        })
      case _ => {
        writeError("Array initializer literal type does not match field decl type", arrayLiteral.location)
        return false
      }
    }
    true
  }

  private def checkBlock(block: IrBlock, methodDeclType: Type): Boolean = {
    val blockScope = block.scope
    scopeStack.push(blockScope)

    var checkFieldDecls: Boolean = true

    if (block.fields.nonEmpty) {
      checkFieldDecls = block.fields.map(field => {
        checkFieldDecl(field)
      }).reduce(_ && _)
    }

    var checkStatements: Boolean = true

    if (block.statements.nonEmpty) {
      checkStatements = block.statements.map(statement => {
        checkStatement(statement, methodDeclType)
      }).reduce(_ && _)
    }

    scopeStack.pop()
    checkFieldDecls && checkStatements
  }

  private def checkStatement(statement: IrStatement, methodDeclType: Type): Boolean = {
    statement match {
      case IrAssignStatement(assignLocation, assignOp, assignExpr, location) =>
        val (isAssignLocationValid, assignLocationType, isMethod) = checkLocation(assignLocation)
        val (isAssignExprValid, assignExprType) = checkExpr(assignExpr)

        if (assignLocation.identifier.startsWith("_.tern_t")) {
          // Ternary removal case
          var isDone = false;
          scopeStack.foreach(scope => {
            if (scope.symbols.contains(assignLocation.identifier) && !isDone) {
              scope.symbols.put(
                assignLocation.identifier,
                scope.symbols(assignLocation.identifier).asInstanceOf[IrRegFieldDecl].copy(
                  declType = assignExprType
                )
              )
              isDone = true
            }
          })
          true
        } else {
          if (isMethod) {
            writeError("Methods cannot be assigned to", location)
            false
          } else if (isAssignLocationValid && isAssignExprValid) {
            assignOp match {
              case AssignOpType.EQUAL =>
                if (assignLocationType != assignExprType) {
                  writeError("Location and expr in assignment must have same type", location)
                  false
                } else {
                  true
                }
              case compound_assign_op@(AssignOpType.ADD_EQUAL | AssignOpType.SUB_EQUAL) =>
                if (assignLocationType != Type.INT || assignExprType != Type.INT) {
                  writeError("Location and expr in incrementing/decrementing assignment must be of type int", location)
                  false
                } else {
                  true
                }
            }
          } else {
            false
          }
        }
      case IrFunctionCallStatement(identifier, params, location) =>
        var (isMethodCallValid, methodType) = checkMethodCall(identifier, params, location)
        if (!isMethodCallValid) {
          writeError("Method call is invalid", location)
        }
        isMethodCallValid
      case IrIfStatement(conditional, thenBlock, elseBlock, location) =>
        val (isConditionalValid, conditionalType) = checkExpr(conditional)
        if (isConditionalValid) {
          if (conditionalType != Type.BOOL) {
            writeError("Conditional in statement must have type bool", location)
            false
          } else {
            val checkThenBlock: Boolean = checkBlock(thenBlock, methodDeclType)
            var checkElseBlock: Boolean = true
            if (elseBlock.isDefined) {
              checkElseBlock = checkBlock(elseBlock.get, methodDeclType)
            }
            checkThenBlock && checkElseBlock
          }
        } else {
          false
        }
      case IrForStatement(identifier, declExpr, conditional, forUpdate, forBlock, location) =>
        val (isIdentifierValid, identifierType) = checkId(identifier, location)
        val (isDeclExprValid, declExprType) = checkExpr(declExpr)

        if (isIdentifierValid && isDeclExprValid) {
          if (identifierType != declExprType) {
            writeError("Identifier and expr in assignment must have same type", location)
            false
          } else {
            val (isConditionalValid, conditionalType) = checkExpr(conditional)
            if (isConditionalValid) {
              if (conditionalType != Type.BOOL) {
                writeError("Conditional in if statement must have type bool", location)
                false
              } else {
                val (isForUpdateLocationValid, forUpdateLocationType, isMethod) = checkLocation(forUpdate.identifier)
                val (isForUpdateExprValid, forUpdateExprType) = checkExpr(forUpdate.expr)

                if (isForUpdateLocationValid && isForUpdateExprValid) {
                  if (forUpdateLocationType != Type.INT || forUpdateExprType != Type.INT) {
                    writeError("Location and expr in incrementing/decrementing assignment must be of type int", location)
                    false
                  } else {
                    checkBlock(forBlock, methodDeclType)
                  }
                } else {
                  false
                }
              }
            } else {
              false
            }
          }
        } else {
          false
        }
      case IrWhileStatement(conditional, whileBlock, location) =>
        val (isConditionalValid, conditionalType) = checkExpr(conditional)
        if (isConditionalValid) {
          if (conditionalType != Type.BOOL) {
            writeError("Conditional in if statement must have type bool", location)
            false
          } else {
            checkBlock(whileBlock, methodDeclType)
          }
        } else {
          false
        }
      case IrReturnStatement(expr, location) =>
        if (methodDeclType == Type.VOID && expr.isDefined) {
          writeError("Return statement in method declared to return void, but has return value", location)
          false
        } else if (methodDeclType != Type.VOID && expr.isEmpty) {
          writeError("Return statement in method declared with return value, but does not return value", location)
          false
        } else if (methodDeclType != Type.VOID && expr.isDefined) {
          val (isExprValid, exprType) = checkExpr(expr.get)
          if (isExprValid) {
            if (exprType != methodDeclType) {
              writeError("Expr in return statement must have same type as method type", location)
              false
            } else {
              true
            }
          } else {
            false
          }
        } else {
          true
        }
      case IrContinueStatement(location) =>
        var inForOrWhile: Boolean = false

        scopeStack.foreach(scope => {
          scope.scopeType.toString match {
            case "FOR" =>
              inForOrWhile = true
            case "WHILE" =>
              inForOrWhile = true
            case _ =>
          }
        })

        if (!inForOrWhile) {
          writeError("Continue statement not contained in the body of a for or while statement", location)
        }
        inForOrWhile

      case IrBreakStatement(location) =>
        var inForOrWhile: Boolean = false

        scopeStack.foreach(scope => {
          scope.scopeType.toString match {
            case "FOR" =>
              inForOrWhile = true
            case "WHILE" =>
              inForOrWhile = true
            case _ =>
          }
        })

        if (!inForOrWhile) {
          writeError("Break statement not contained in the body of a for or while statement", location)
        }
        inForOrWhile
    }
  }

  private def checkId(identifier: String, codeLocation: CodeLocation): (Boolean, Type) = {
    var inScope: Boolean = false
    var identifierType: Type = Type.VOID
    var done: Boolean = false

    scopeStack.foreach(scope => {
      if (scope.symbols.contains(identifier) && !done) {
        inScope = true
        done = true;
        identifierType = scope.symbols(identifier).declType
      }
    })

    if (!inScope) {
      writeError("Identifier is used before it is declared", codeLocation)
      (false, Type.VOID)
    } else {
      (true, identifierType)
    }
  }

  private def checkLocation(location: IrLocation): (Boolean, Type, Boolean) = {
    var inScope: Boolean = false
    val locationIdentifier = location.identifier
    var identifierType: Type = Type.VOID

    var done = false
    var isMethod = false
    scopeStack.foreach(scope => {
      if (scope.symbols.contains(locationIdentifier) && !done) {
        inScope = true
        identifierType = scope.symbols(locationIdentifier).declType

        scope.symbols(locationIdentifier) match {
          case _: IrFunctionDecl => isMethod = true
          case _: IrImportDecl => isMethod = true
          case _ =>
        }

        done = true
      }
    })

    val locationIndex = location.index
    // Array Literal
    if (!inScope) {
      writeError("Location identifier does not name a declared local/global variable or parameter", location.location)
      (false, Type.VOID, isMethod)
    } else {
      if (locationIndex.isDefined) {
        if (!(identifierType == Type.INT_ARR || identifierType == Type.BOOL_ARR)) {
          writeError("Location identifier should be an array literal", location.location)
          (false, Type.VOID, isMethod)
        } else {
          val (indexResult, indexType) = checkExpr(locationIndex.get)
          if (!indexResult) {
            writeError("Index for array variable is invalid expr", location.location)
            (false, Type.VOID, isMethod)
          } else {
            if (indexType != Type.INT) {
              writeError("Index for array variable must be type int", location.location)
              (false, Type.VOID, isMethod)
            } else {
              (true, if (identifierType == Type.INT_ARR) Type.INT else Type.BOOL, isMethod)
            }
          }
        }
      // Literal
      } else {
        (true, identifierType, isMethod)
      }
    }
  }

  private def checkMethodCall(identifier: String, callParams: List[MethodCallParam], codeLocation: CodeLocation): (Boolean, Type) = {
    var inScope: Boolean = false
    var methodType: Type = Type.VOID
    val callParamTypes = callParams.map {
      case methodCallExprParam: FunctionCallExprParam =>
        val (isValidExpr, exprType) = checkExpr(methodCallExprParam.param)
        if (isValidExpr) {
          exprType
        } else {
          return (false, Type.VOID)
        }
      case methodCallStringParam: FunctionCallStringParam =>
        methodCallStringParam.param
    }

    var done = false;
    scopeStack.foreach(scope => {
      if (scope.symbols.contains(identifier) && !done) {
        val method = scope.symbols(identifier)
        inScope = true
        done = true;
        methodType = method.declType

        val orderOfCallingMethod: Int = scopeStack.last.order.getOrElse(method.identifier, 0)
        val orderOfCurrentScopeMethod: Int = scopeStack.last.order.getOrElse(scopeStack.top.curMethodIdentifier.getOrElse("null"), 0)

        method match {
          case IrFunctionDecl(declType, identifier, parameters, block, scope, location) =>
            val declParamTypes = parameters.map(param => param.declType)
            if (callParams.size != parameters.size) {
              writeError("Expected number of call parameters to be the same as declared parameters of method", codeLocation)
              return (false, Type.VOID)
            } else if (orderOfCallingMethod > orderOfCurrentScopeMethod) {
              writeError("Method was called before it was defined", codeLocation)
              return (false, Type.VOID)
            } else {
              (declParamTypes, callParamTypes).zipped.foreach((declParam, callParam) => {
                callParam match {
                  case _: String =>
                    writeError("String literals may not be used as parameters to non-import methods", codeLocation)
                    return (false, Type.VOID)
                  case _ @ (Type.INT_ARR | Type.BOOL_ARR) =>
                    writeError("Array variables may not be used as parameters to non-import methods", codeLocation)
                    return (false, Type.VOID)
                  case literal @ (Type.INT | Type.BOOL) =>
                    if (literal != declParam) {
                      writeError("Expected types of call parameters to be the same as declared parameters of method", codeLocation)
                      return (false, Type.VOID)
                    }
                }
              })
              return (true, methodType)
            }
          case IrImportDecl(_, _) => return (true, Type.INT)
          case _ =>
        }
      }
    })

    if (!inScope) {
      writeError("Identifier is used before it is declared", codeLocation)
    }
    (false, Type.VOID)
  }

  private def checkExpr(expr: IrExpression): (Boolean, Type) = {
    expr match {
      case intLiteral : IrIntLiteral =>
        (true, Type.INT)
      case boolLiteral: IrBoolLiteral =>
        (true, Type.BOOL)
      case varReadExpr:IrVarReadExpr => {
        val (isForUpdateLocationValid, forUpdateLocationType, _) = checkLocation(varReadExpr.readLocation)
        (isForUpdateLocationValid, forUpdateLocationType)
      }
      case methodCallExpr: IrFunctionCallExpr =>
        val (isMethodCallValid, methodType) = checkMethodCall(methodCallExpr.identifier, methodCallExpr.params, methodCallExpr.location)

        if (isMethodCallValid) {
          if (methodType == Type.VOID) {
            writeError("Method call must return a result", methodCallExpr.location)
            (false, Type.VOID)
          } else {
            (true, methodType)
          }
        } else {
          (false, Type.VOID)
        }
      case lenExpr: IrLenExpr =>
        val (lenExprExists, lenExprType) = checkId(lenExpr.identifier, lenExpr.location)
        if (lenExprExists) {
          if (!(lenExprType == Type.INT_ARR || lenExprType == Type.BOOL_ARR)) {
            writeError("Argument of len operator must be an array variable", lenExpr.location)
            (false, Type.VOID)
          } else {
            (true, Type.INT)
          }
        } else {
          (false, Type.VOID)
        }
      case binOpExpr: IrBinOpExpr =>
        val binOpType: BinOpType = binOpExpr.op
        val leftExpr: IrExpression = binOpExpr.leftExpr
        val rightExpr: IrExpression = binOpExpr.rightExpr
        val (isLeftExprValid, leftExprType) = checkExpr(leftExpr)
        val (isRightExprValid, rightExprType) = checkExpr(rightExpr)

        binOpType match {
          case arith_ops: ArithBinOpType =>
            if (!isLeftExprValid || !isRightExprValid) {
              writeError("Operands of arith_op(s) are invalid expr(s)", binOpExpr.location)
              (false, Type.VOID)
            } else {
              if (leftExprType != Type.INT || rightExprType != Type.INT) {
                writeError("Operands of arith_op(s) must have type int", binOpExpr.location)
                (false, Type.VOID)
              } else {
                (true, Type.INT)
              }
            }
          case rel_ops: RelationalBinOpType =>
            if (!isLeftExprValid || !isRightExprValid) {
              writeError("Operands of rel_op(s) are invalid expr(s)", binOpExpr.location)
              (false, Type.VOID)
            } else {
              if (leftExprType != Type.INT || rightExprType != Type.INT) {
                writeError("Operands of rel_op(s) must have type int", binOpExpr.location)
                (false, Type.VOID)
              } else {
                (true, Type.BOOL)
              }
            }
          case cond_ops: LogicalBinOpType =>
            if (!isLeftExprValid || !isRightExprValid) {
              writeError("Operands of cond_op(s) are invalid expr(s)", binOpExpr.location)
              (false, Type.VOID)
            } else {
              if (leftExprType != Type.BOOL || rightExprType != Type.BOOL) {
                writeError("Operands of cond_op(s) must have type bool", binOpExpr.location)
                (false, Type.VOID)
              } else {
                (true, Type.BOOL)
              }
            }
          case eq_ops: EqualityBinOpType =>
            if (!isLeftExprValid || !isRightExprValid) {
              writeError("Operands of eq_op(s) are invalid expr(s)", binOpExpr.location)
              (false, Type.VOID)
            } else {
              if (leftExprType != rightExprType) {
                writeError("Operands of eq_op(s) must have the same type, either int or bool", binOpExpr.location)
                (false, Type.VOID)
              } else {
                (true, Type.BOOL)
              }
            }
        }
      case unaryOpExpr: IrUnaryOpExpr =>
        val unaryOpType: UnaryOpType = unaryOpExpr.op
        val unaryExpr: IrExpression = unaryOpExpr.expr
        val (isUnaryExprValid, unaryExprType) = checkExpr(unaryExpr)

        unaryOpType match {
          case UnaryOpType.UMINUS =>
            if (!isUnaryExprValid) {
              writeError("Operand of unary minus is invalid expr", unaryOpExpr.location)
              (false, Type.VOID)
            } else {
              if (unaryExprType != Type.INT) {
                writeError("Operand of unary minus must have type int", unaryOpExpr.location)
                (false, Type.VOID)
              } else {
                (true, Type.INT)
              }
            }
          case UnaryOpType.NOT =>
            if (!isUnaryExprValid) {
              writeError("Operand of logical not (!) is invalid expr", unaryOpExpr.location)
              (false, Type.VOID)
            } else {
              if (unaryExprType != Type.BOOL) {
                writeError("Operand of logical not(!) must have type bool", unaryOpExpr.location)
                (false, Type.VOID)
              } else {
                (true, Type.BOOL)
              }
            }
        }
      case ternaryExpr: IrTernaryExpr =>
        val condition: IrExpression = ternaryExpr.condition
        val trueExpr: IrExpression = ternaryExpr.trueExpr
        val falseExpr: IrExpression = ternaryExpr.falseExpr

        val (isConditionValid, conditionType) = checkExpr(condition)
        val (isTrueExprValid, trueExprType) = checkExpr(trueExpr)
        val (isFalseExprValid, falseExprType) = checkExpr(falseExpr)

        if (!isConditionValid || !isTrueExprValid || !isFalseExprValid) {
          writeError("Operators in ternary are invalid expr(s)", expr.location)
          (false, Type.VOID)
        } else {
          if (conditionType != Type.BOOL) {
            writeError("First operator in ternary must have type bool", ternaryExpr.location)
            (false, Type.VOID)
          } else {
            if (trueExprType != falseExprType) {
              writeError("Second and Third operators in ternary must have the same type", trueExpr.location)
              (false, Type.VOID)
            } else {
              (true, trueExprType)
            }
          }
        }
    }
  }
}
