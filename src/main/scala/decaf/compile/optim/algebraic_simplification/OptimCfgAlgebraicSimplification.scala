package decaf.compile.optim.algebraic_simplification

import decaf.compile._
import decaf.compile.cfg._
import decaf.compile.ir._

object OptimCfgAlgebraicSimplification {

  var UPDATED = false

  //noinspection DuplicatedCode
  def visitProgram(program: CfgProgram): CfgProgram = {
    UPDATED = false
    val optimFunctions = program.functions.map(func => visitFunction(func))
    program.copy(
      functions = optimFunctions
    )
  }

  //noinspection DuplicatedCode
  def visitFunction(function: CfgFunction): CfgFunction = {
    function.copy(
      scope = visitScope(function.scope)
    )
  }

  def visitScope(scope: CfgScope): CfgScope = {
    scope.copy(
      basicBlocks = scope.basicBlocks.map(pair => {
        val (label, basicBlock) = pair
        val newBasicBlock = basicBlock match {
          case basicBlock@RegularBlock(fieldDecls, statements) => {
            basicBlock.copy(statements = (
              statements.map(x => visitStatement(x))
            ))
          }
          case basicBlock@ConditionalBlock(fieldDecls, statements, condition) => {
            basicBlock.copy(statements = (
              statements.map(x => visitStatement(x))
            ))
          }
        }
        (label, newBasicBlock)
      })
    )
  }

  def visitStatement(cfgStatement: CfgStatement): CfgStatement = {
    cfgStatement match {
      case statement: CfgAssignStatement => {
        statement match {
          case statement@CfgRegAssignStatement(to, value) => {
            statement.copy(
              value = visitExpression(value)
            )
          }
          case CfgArrayAssignStatement(to, value) => cfgStatement
        }
      }
      case CfgFunctionCallStatement(functionName, parameters) => {
        cfgStatement
      }
      case CfgReturnStatement(value) => {
        cfgStatement
      }
    }
  }


  def visitExpression(cfgExpression: CfgExpression): CfgExpression = {
    cfgExpression match {
      case value: CfgValue => return value
      case CfgArrayReadExpr(variable, index) => return cfgExpression
      case CfgLenExpr(variable) => return cfgExpression
      case CfgFunctionCallExpr(identifier, params) => return cfgExpression

      case opExpr@CfgUnaryOpExpr(op, value) => {
        op match {
          case UnaryOpType.UMINUS => {
            value match {
              case CfgImmediate(value) => {
                UPDATED = true
                return CfgImmediate(value * -1L)
              }
              case CfgVariable(identifier, number) => return opExpr
            }
          }
          case UnaryOpType.NOT => return opExpr
        }
      }
      case opExpr@CfgBinOpExpr(op, leftValue, rightValue) => {
        op match {
          case opType: ArithBinOpType => {
            var isLeftImmediate = false
            var isRightImmediate = false
            var leftLongValue: Long = -999L
            var rightLongValue: Long = -999L

            leftValue match {
              case CfgImmediate(value) => {
                isLeftImmediate = true
                leftLongValue = value
              }
              case CfgVariable(identifier, number) => {
                isLeftImmediate = false
              }
            }

            rightValue match {
              case CfgImmediate(value) => {
                isRightImmediate = true
                rightLongValue = value
              }
              case CfgVariable(identifier, number) => {
                isRightImmediate = false
              }
            }

            opType match {
              case ArithBinOpType.ADD => {
                if (isLeftImmediate && isRightImmediate){
                  // a + b where a and b are both immediates
                  UPDATED = true
                  return CfgImmediate(leftLongValue + rightLongValue)
                } else if (isLeftImmediate && leftLongValue == 0L){
                  // 0 + a
                  UPDATED = true
                  return rightValue
                } else if (isRightImmediate && rightLongValue == 0L){
                  // a + 0
                  UPDATED = true
                  return leftValue
                } else {
                  return opExpr
                }
              }
              case ArithBinOpType.SUB => {
                if (isLeftImmediate && isRightImmediate) {
                  // a - b where a and b are both immediates
                  UPDATED = true
                  return CfgImmediate(leftLongValue - rightLongValue)
                } else if (isLeftImmediate && leftLongValue == 0L) {
                  // 0 - a
                  UPDATED = true
                  return CfgUnaryOpExpr(op = UnaryOpType.UMINUS, expr = rightValue)
                } else if (isRightImmediate && rightLongValue == 0L) {
                  // a + 0
                  UPDATED = true
                  return leftValue
                } else {
                  return opExpr
                }
              }
              case ArithBinOpType.MUL => {
                if (isLeftImmediate && isRightImmediate) {
                  // a * b where a and b are both immediates
                  UPDATED = true
                  return CfgImmediate(leftLongValue * rightLongValue)
                } else if (isLeftImmediate && leftLongValue == 0L) {
                  // 0 * a
                  UPDATED = true
                  return CfgImmediate(0L)
                } else if (isRightImmediate && rightLongValue == 0L) {
                  // a * 0
                  UPDATED = true
                  return CfgImmediate(0L)
                } else if (isLeftImmediate && leftLongValue == 1L) {
                  // 1 * a
                  UPDATED = true
                  return rightValue
                } else if (isRightImmediate && rightLongValue == 1L) {
                  // a * 1
                  UPDATED = true
                  return leftValue
                } else {
                  return opExpr
                }
              }
              case ArithBinOpType.DIV => {
                if (isLeftImmediate && isRightImmediate) {
                  // a / b where a and b are both immediates
                  UPDATED = true
                  return CfgImmediate(leftLongValue / rightLongValue)
                } else if (isLeftImmediate && leftLongValue == 0L) {
                  // 0 / a
                  UPDATED = true
                  return CfgImmediate(0L)
                } else if (isRightImmediate && rightLongValue == 0L) {
                  // a / 0
                  UPDATED = true
                  // undefined, so we'll just put 0
                  return CfgImmediate(0L)
                } else if (isRightImmediate && rightLongValue == 1L) {
                  // a / 1
                  UPDATED = true
                  return leftValue
                } else {
                  return opExpr
                }
              }
              case ArithBinOpType.MOD => {
                if (isLeftImmediate && isRightImmediate) {
                  // a % b where a and b are both immediates
                  UPDATED = true
                  return CfgImmediate(leftLongValue % rightLongValue)
                } else if (isRightImmediate && rightLongValue == 1L) {
                  // a % 1
                  UPDATED = true
                  return CfgImmediate(0L)
                } else {
                  return opExpr
                }
              }
            }
          }
          case opType: RelationalBinOpType => {
            var isLeftImmediate = false
            var isRightImmediate = false
            var leftLongValue: Long = -999L
            var rightLongValue: Long = -999L

            leftValue match {
              case CfgImmediate(value) => {
                isLeftImmediate = true
                leftLongValue = value
              }
              case CfgVariable(identifier, number) => {
                isLeftImmediate = false
              }
            }

            rightValue match {
              case CfgImmediate(value) => {
                isRightImmediate = true
                rightLongValue = value
              }
              case CfgVariable(identifier, number) => {
                isRightImmediate = false
              }
            }

            opType match {
              case RelationalBinOpType.LT => {
                if(isLeftImmediate && isRightImmediate) {
                  UPDATED = true
                  CfgImmediate(if (leftLongValue < rightLongValue) 1L else 0L)
                } else {
                  opExpr
                }
              }
              case RelationalBinOpType.LTE => {
                if (isLeftImmediate && isRightImmediate) {
                  UPDATED = true
                  CfgImmediate(if (leftLongValue <= rightLongValue) 1L else 0L)
                } else {
                  opExpr
                }
              }
              case RelationalBinOpType.GT => {
                if (isLeftImmediate && isRightImmediate) {
                  UPDATED = true
                  CfgImmediate(if (leftLongValue > rightLongValue) 1L else 0L)
                } else {
                  opExpr
                }
              }
              case RelationalBinOpType.GTE => {
                if (isLeftImmediate && isRightImmediate) {
                  UPDATED = true
                  CfgImmediate(if (leftLongValue >= rightLongValue) 1L else 0L)
                } else {
                  opExpr
                }
              }
            }
          }
          case opType: EqualityBinOpType => {
            var isLeftImmediate = false
            var isRightImmediate = false
            var leftLongValue: Long = -999L
            var rightLongValue: Long = -999L

            leftValue match {
              case CfgImmediate(value) => {
                isLeftImmediate = true
                leftLongValue = value
              }
              case CfgVariable(identifier, number) => {
                isLeftImmediate = false
              }
            }

            rightValue match {
              case CfgImmediate(value) => {
                isRightImmediate = true
                rightLongValue = value
              }
              case CfgVariable(identifier, number) => {
                isRightImmediate = false
              }
            }

            opType match {
              case EqualityBinOpType.EQ => {
                if (isLeftImmediate && isRightImmediate) {
                  UPDATED = true
                  CfgImmediate(if (leftLongValue == rightLongValue) 1L else 0L)
                } else {
                  opExpr
                }
              }
              case EqualityBinOpType.NEQ => {
                if (isLeftImmediate && isRightImmediate) {
                  UPDATED = true
                  CfgImmediate(if (leftLongValue != rightLongValue) 1L else 0L)
                } else {
                  opExpr
                }
              }
            }
          }
          case opType: LogicalBinOpType => {
            var isLeftImmediate = false
            var isRightImmediate = false
            var leftBooleanValue: Boolean = false
            var rightBooleanValue: Boolean = false

            leftValue match {
              case CfgImmediate(value) => {
                isLeftImmediate = true
                leftBooleanValue = value == 1L
              }
              case CfgVariable(identifier, number) => {
                isLeftImmediate = false
              }
            }

            rightValue match {
              case CfgImmediate(value) => {
                isRightImmediate = true
                rightBooleanValue = value == 1L
              }
              case CfgVariable(identifier, number) => {
                isRightImmediate = false
              }
            }

            opType match {
              case LogicalBinOpType.AND => {
                if (isLeftImmediate && isRightImmediate) {
                  UPDATED = true
                  CfgImmediate(if (leftBooleanValue && rightBooleanValue) 1L else 0L)
                } else if ((isLeftImmediate && !leftBooleanValue) || (isRightImmediate && !rightBooleanValue)) {
                  // false && x or x && false
                  UPDATED = true
                  CfgImmediate(0L)
                } else if (isLeftImmediate && leftBooleanValue){
                  // true && x
                  UPDATED = true
                  rightValue
                } else if (isRightImmediate && rightBooleanValue){
                  // x && true
                  UPDATED = true
                  leftValue
                } else {
                  opExpr
                }
              }
              case LogicalBinOpType.OR => {
                if (isLeftImmediate && isRightImmediate) {
                  UPDATED = true
                  CfgImmediate(if (leftBooleanValue || rightBooleanValue) 1L else 0L)
                } else if ((isLeftImmediate && leftBooleanValue) || (isRightImmediate && rightBooleanValue)) {
                  // true || x or x || true
                  UPDATED = true
                  CfgImmediate(1L)
                } else if (isLeftImmediate && !leftBooleanValue) {
                  // false || x
                  UPDATED = true
                  rightValue
                } else if (isRightImmediate && !rightBooleanValue) {
                  // x || false
                  UPDATED = true
                  leftValue
                } else {
                  opExpr
                }
              }
            }
          }
        }
      }
    }
  }

}

