package decaf.compile.optim.algebraic_simplification

import decaf.compile.ir._

object OptimIrAlgebraicSimplification {

  var madeSimplification: Boolean = false;

  def visitProgram(irProgram: IrProgram): IrProgram = {
    madeSimplification = false;
    var program = _visitProgram(irProgram)

    while (madeSimplification){
      madeSimplification = false
      program = _visitProgram(program)
    }
    program
  }

  private def _visitProgram(irProgram: IrProgram): IrProgram = {
    val functions = irProgram.functions.map(
      x => visitFunction(x)
    )
    irProgram.copy(
      functions = functions
    )
  }

  private def visitFunction(decl: IrFunctionDecl): IrFunctionDecl = {
    decl.copy(
      block = visitBlock(decl.block)
    )
  }

  private def visitBlock(block: IrBlock): IrBlock = {
    val statements = block.statements.map(x => {
      visitStatement(x)
    })

    block.copy(
      statements = statements
    )
  }

  private def visitStatement(statement: IrStatement) : IrStatement = {
    statement match {
      case statement@IrAssignStatement(assignLocation, assignOp, assignExpr, location) => {
        var newAssignLocation = assignLocation
        if (assignLocation.index.isDefined){
          newAssignLocation = assignLocation.copy(
            index = Some(simplifyExpression(assignLocation.index.get))
          )
        }

        statement.copy(
          assignLocation = newAssignLocation,
          assignOp = assignOp,
          assignExpr = simplifyExpression(assignExpr),
          location = location
        )
      }
      case statement@IrFunctionCallStatement(identifier, params, location) => {
        val newParams = params.map {
          case p@FunctionCallExprParam(param, location) => {
            p.copy(
              param = simplifyExpression(param),
              location = location
            )
          }
          case p@FunctionCallStringParam(param, location) => p
        }

        statement.copy(
          identifier = identifier,
          params = newParams,
          location = location
        )
      }
      case statement@IrIfStatement(conditional, thenBlock, elseBlock, location) => {
        var newElseBlock = elseBlock
        if (elseBlock.isDefined){
          newElseBlock = Some(visitBlock(elseBlock.get))
        }

        statement.copy(
          conditional = simplifyExpression(conditional),
          thenBlock = visitBlock(thenBlock),
          elseBlock = newElseBlock,
          location = location
        )
      }
      case statement@IrForStatement(identifier, declExpr, conditional, forUpdate, forBlock, location) => {
        statement.copy(
          identifier = identifier,
          declExpr = simplifyExpression(declExpr),
          conditional = simplifyExpression(conditional),
          forUpdate = forUpdate,
          forBlock = visitBlock(forBlock),
          location = location
        )
      }
      case statement@IrWhileStatement(conditional, whileBlock, location) => {
        statement.copy(
          conditional = simplifyExpression(conditional),
          whileBlock = visitBlock(whileBlock),
          location = location
        )
      }
      case statement@IrReturnStatement(expr, location) => {
        var newExpr = expr
        if (expr.isDefined){
          newExpr = Some(simplifyExpression(expr.get))
        }

        statement.copy(
          expr = newExpr,
          location = location
        )
      }
      case statement@IrContinueStatement(location) => statement
      case statement@IrBreakStatement(location) => statement
    }
  }

  private def simplifyExpression(expr: IrExpression) : IrExpression = {
    expr match {
      case literal: IrLiteral => literal
      case expr@IrVarReadExpr(readLocation, location) => {
        if (readLocation.index.isDefined){
          expr.copy(
            readLocation = readLocation.copy(
              identifier = readLocation.identifier,
              index = Some(simplifyExpression(readLocation.index.get)),
              location = readLocation.location
            ),
            location
          )
        }else{
          expr
        }
      }
      case expr@IrFunctionCallExpr(identifier, params, location) => {
        val newParams = params.map {
          case p@FunctionCallExprParam(param, location) => {
            p.copy(
              param = simplifyExpression(param),
              location = location
            )
          }
          case p@FunctionCallStringParam(param, location) => p
        }

        expr.copy(
          identifier = identifier,
          params = newParams,
          location = location
        )
      }
      case expr@IrLenExpr(identifier, location) => expr
      case expr@IrUnaryOpExpr(op, subExpr, location) => {
        op match {
          case UnaryOpType.UMINUS => {
            val newSubExpr: IrExpression = simplifyExpression(subExpr)
            if (newSubExpr.isInstanceOf[IrIntLiteral]){
              IrIntLiteral(newSubExpr.asInstanceOf[IrIntLiteral].value * -1L, subExpr.location)
            } else {
              expr
            }
          }
          case UnaryOpType.NOT => expr
        }
      }
      case expr@IrBinOpExpr(op, leftExpr, rightExpr, location) => {
        val sLeftExpr = simplifyExpression(leftExpr)
        val sRightExpr = simplifyExpression(rightExpr)

        op match {
          case opType: ArithBinOpType => {
            val leftIsIntLiteral = sLeftExpr.isInstanceOf[IrIntLiteral]
            val rightIsIntLiteral = sRightExpr.isInstanceOf[IrIntLiteral]

            var leftIntValue: Long = -9999
            var rightIntValue: Long = -9999
            if (leftIsIntLiteral) {
              leftIntValue = sLeftExpr.asInstanceOf[IrIntLiteral].value
            }
            if (rightIsIntLiteral) {
              rightIntValue = sRightExpr.asInstanceOf[IrIntLiteral].value
            }

            opType match {
              case ArithBinOpType.MUL => {
                if (leftIsIntLiteral && rightIsIntLiteral) {
                  // Evaluate this directly
                  madeSimplification = true
                  IrIntLiteral(leftIntValue * rightIntValue, sLeftExpr.location)
                } else if ((leftIsIntLiteral && leftIntValue == 0) || (rightIsIntLiteral && rightIntValue == 0)){
                  // Either value is 0
                  madeSimplification = true
                  IrIntLiteral(0, sLeftExpr.location)
                } else if (leftIsIntLiteral && leftIntValue == 1){
                  madeSimplification = true
                  sRightExpr
                } else if (rightIsIntLiteral && rightIntValue == 1){
                  madeSimplification = true
                  sLeftExpr
                } else {
                  expr.copy(
                    op, sLeftExpr, sRightExpr, location
                  )
                }
              }
              case ArithBinOpType.DIV => {
                if (leftIsIntLiteral && rightIsIntLiteral) {
                  // Evaluate this directly
                  madeSimplification = true
                  IrIntLiteral(leftIntValue / rightIntValue, sLeftExpr.location)
                } else if ((leftIsIntLiteral && leftIntValue == 0) || (rightIsIntLiteral && rightIntValue == 0)) {
                  // Either value is 0
                  madeSimplification = true
                  IrIntLiteral(0, sLeftExpr.location)
                } else if (leftIsIntLiteral && leftIntValue == 1) {
                  madeSimplification = true
                  sRightExpr
                } else if (rightIsIntLiteral && rightIntValue == 1) {
                  madeSimplification = true
                  sLeftExpr
                } else {
                  expr.copy(
                    op, sLeftExpr, sRightExpr, location
                  )
                }
              }
              case ArithBinOpType.MOD => {
                if (leftIsIntLiteral && rightIsIntLiteral) {
                  // Evaluate this directly
                  madeSimplification = true
                  IrIntLiteral(leftIntValue % rightIntValue, sLeftExpr.location)
                } else {
                  expr.copy(
                    op, sLeftExpr, sRightExpr, location
                  )
                }
              }
              case ArithBinOpType.ADD => {
                if (leftIsIntLiteral && rightIsIntLiteral) {
                  // Evaluate this directly
                  madeSimplification = true
                  IrIntLiteral(leftIntValue + rightIntValue, sLeftExpr.location)
                } else if (leftIsIntLiteral && leftIntValue == 0) {
                  madeSimplification = true
                  sRightExpr
                } else if (rightIsIntLiteral && rightIntValue == 0) {
                  madeSimplification = true
                  sLeftExpr
                } else {
                  expr.copy(
                    op, sLeftExpr, sRightExpr, location
                  )
                }
              }
              case ArithBinOpType.SUB => {
                if (leftIsIntLiteral && rightIsIntLiteral) {
                  // Evaluate this directly
                  madeSimplification = true
                  IrIntLiteral(leftIntValue - rightIntValue, sLeftExpr.location)
                } else if (leftIsIntLiteral && leftIntValue == 0) {
                  madeSimplification = true
                  IrUnaryOpExpr(UnaryOpType.UMINUS, sRightExpr, sLeftExpr.location)
                } else if (rightIsIntLiteral && rightIntValue == 0) {
                  madeSimplification = true
                  sLeftExpr
                } else {
                  expr.copy(
                    op, sLeftExpr, sRightExpr, location
                  )
                }
              }
            }
          }
          case opType: RelationalBinOpType => {
            val sLeftExpr = simplifyExpression(expr.leftExpr)
            val sRightExpr = simplifyExpression(expr.rightExpr)

            val isLeftExprInt = sLeftExpr.isInstanceOf[IrIntLiteral]
            val isRightExprInt = sRightExpr.isInstanceOf[IrIntLiteral]

            opType match {
              case RelationalBinOpType.LT => {
                if (isLeftExprInt && isRightExprInt) {
                  madeSimplification = true
                  IrBoolLiteral(sLeftExpr.asInstanceOf[IrIntLiteral].value < sRightExpr.asInstanceOf[IrIntLiteral].value, sLeftExpr.location)
                } else {
                  expr.copy(
                    op = op, leftExpr = sLeftExpr, rightExpr = sRightExpr, location = location
                  )
                }
              }
              case RelationalBinOpType.LTE => {
                if (isLeftExprInt && isRightExprInt) {
                  madeSimplification = true
                  IrBoolLiteral(sLeftExpr.asInstanceOf[IrIntLiteral].value <= sRightExpr.asInstanceOf[IrIntLiteral].value, sLeftExpr.location)
                } else {
                  expr.copy(
                    op = op, leftExpr = sLeftExpr, rightExpr = sRightExpr, location = location
                  )
                }
              }
              case RelationalBinOpType.GT => {
                if (isLeftExprInt && isRightExprInt) {
                  madeSimplification = true
                  IrBoolLiteral(sLeftExpr.asInstanceOf[IrIntLiteral].value > sRightExpr.asInstanceOf[IrIntLiteral].value, sLeftExpr.location)
                } else {
                  expr.copy(
                    op = op, leftExpr = sLeftExpr, rightExpr = sRightExpr, location = location
                  )
                }
              }
              case RelationalBinOpType.GTE => {
                if (isLeftExprInt && isRightExprInt) {
                  madeSimplification = true
                  IrBoolLiteral(sLeftExpr.asInstanceOf[IrIntLiteral].value >= sRightExpr.asInstanceOf[IrIntLiteral].value, sLeftExpr.location)
                } else {
                  expr.copy(
                    op = op, leftExpr = sLeftExpr, rightExpr = sRightExpr, location = location
                  )
                }
              }
            }
          }
          case opType: EqualityBinOpType => {
            val sLeftExpr = simplifyExpression(expr.leftExpr)
            val sRightExpr = simplifyExpression(expr.rightExpr)

            val isLeftExprInt = sLeftExpr.isInstanceOf[IrIntLiteral]
            val isRightExprInt = sRightExpr.isInstanceOf[IrIntLiteral]

            opType match {
              case EqualityBinOpType.EQ => {
                if (isLeftExprInt && isRightExprInt) {
                  madeSimplification = true
                  IrBoolLiteral(sLeftExpr.asInstanceOf[IrIntLiteral].value == sRightExpr.asInstanceOf[IrIntLiteral].value, sLeftExpr.location)
                } else {
                  expr.copy(
                    op = op, leftExpr = sLeftExpr, rightExpr = sRightExpr, location = location
                  )
                }
              }
              case EqualityBinOpType.NEQ => {
                if (isLeftExprInt && isRightExprInt) {
                  madeSimplification = true
                  IrBoolLiteral(sLeftExpr.asInstanceOf[IrIntLiteral].value != sRightExpr.asInstanceOf[IrIntLiteral].value, sLeftExpr.location)
                } else {
                  expr.copy(
                    op = op, leftExpr = sLeftExpr, rightExpr = sRightExpr, location = location
                  )
                }
              }
            }
          }
          case opType: LogicalBinOpType => {
            val sLeftExpr = simplifyExpression(expr.leftExpr)
            val sRightExpr = simplifyExpression(expr.rightExpr)

            val leftIsBoolLiteral = sLeftExpr.isInstanceOf[IrBoolLiteral]
            val rightIsBoolLiteral = sRightExpr.isInstanceOf[IrBoolLiteral]

            var leftBoolValue: Boolean = false
            var rightBoolValue: Boolean = false
            if (leftIsBoolLiteral) {
              leftBoolValue = sLeftExpr.asInstanceOf[IrBoolLiteral].value
            }
            if (rightIsBoolLiteral) {
              rightBoolValue = sRightExpr.asInstanceOf[IrBoolLiteral].value
            }


            opType match {
              case LogicalBinOpType.AND => {
                if (leftIsBoolLiteral && rightIsBoolLiteral){
                  madeSimplification = true
                  IrBoolLiteral(leftBoolValue && rightBoolValue, sLeftExpr.location)
                } else if ((leftIsBoolLiteral && !leftBoolValue) || (rightIsBoolLiteral && !rightBoolValue)){
                  // false && x
                  // x && false
                  madeSimplification = true
                  IrBoolLiteral(value = false, sLeftExpr.location)
                } else if (leftIsBoolLiteral && leftBoolValue){
                  // true && x -> x
                  madeSimplification = true
                  sRightExpr
                } else if (rightIsBoolLiteral && rightBoolValue){
                  // x && true -> x
                  madeSimplification = true
                  sLeftExpr
                } else {
                  expr.copy(
                    op = op, leftExpr = sLeftExpr, rightExpr = sRightExpr, location = location
                  )
                }
              }
              case LogicalBinOpType.OR => {
                if (leftIsBoolLiteral && rightIsBoolLiteral) {
                  madeSimplification = true
                  IrBoolLiteral(leftBoolValue || rightBoolValue, sLeftExpr.location)
                } else if ((leftIsBoolLiteral && leftBoolValue) || (rightIsBoolLiteral && rightBoolValue)) {
                  // true || x
                  // x || true
                  madeSimplification = true
                  IrBoolLiteral(value = true, sLeftExpr.location)
                } else if (leftIsBoolLiteral && !leftBoolValue) {
                  // true || x -> x
                  madeSimplification = true
                  sRightExpr
                } else if (rightIsBoolLiteral && !rightBoolValue) {
                  // x || true -> x
                  madeSimplification = true
                  sLeftExpr
                } else {
                  expr.copy(
                    op = op, leftExpr = sLeftExpr, rightExpr = sRightExpr, location = location
                  )
                }
              }
            }
          }
        }
      }

      // Don't bother with ternaries as they will be hacked out
      case expr@IrTernaryExpr(condition, trueExpr, falseExpr, location) => expr
    }
  }

}

