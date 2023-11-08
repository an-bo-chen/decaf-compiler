package decaf.compile.optim.common_subexpression_elmination

import decaf.compile._
import decaf.compile.cfg._
import decaf.compile.ir.{ArithBinOpType, EqualityBinOpType, LogicalBinOpType, RelationalBinOpType}

import scala.collection.{breakOut, immutable, mutable}
object AvailableExpressions {

  /**
   * CfgBinOpExpr:
   * - var x var: leftExpr is lexically smaller than rightExpr
   * - var x imm, imm x var: leftExpr is the variable
   * - imm x imm: leftExpr is the greater number
   *
   * @param expression the CfgExpression
   * @return the canonicalized version of the expression
   */
  def canonicalization(expression: CfgExpression): CfgExpression = {
    expression match {
      case CfgBinOpExpr(op, leftExpr, rightExpr) =>
        op match {
          case opType: ArithBinOpType =>
            opType match {
              case _@(ArithBinOpType.ADD | ArithBinOpType.MUL) =>
                leftExpr match {
                  case leftImm@CfgImmediate(_) =>
                    rightExpr match {
                      case rightImm@CfgImmediate(_) =>
                        if (rightImm.value > leftImm.value) {
                          CfgBinOpExpr(opType, rightImm, leftImm)
                        } else {
                          expression
                        }
                      case rightVar@CfgVariable(_, _) =>
                        CfgBinOpExpr(opType, rightVar, leftImm)
                    }
                  case leftVar@CfgVariable(_, _) =>
                    rightExpr match {
                      case rightImm@CfgImmediate(_) =>
                        expression
                      case rightVar@CfgVariable(_, _) =>
                        if (rightVar.identifier < leftVar.identifier) {
                          CfgBinOpExpr(opType, rightVar, leftVar)
                        } else {
                          expression
                        }
                    }
                }
              case _ => expression
            }
          case opType: LogicalBinOpType =>
            leftExpr match {
              case leftImm@CfgImmediate(_) =>
                rightExpr match {
                  case rightImm@CfgImmediate(_) =>
                    if (rightImm.value > leftImm.value) {
                      CfgBinOpExpr(opType, rightImm, leftImm)
                    } else {
                      expression
                    }
                  case rightVar@CfgVariable(_, _) =>
                    CfgBinOpExpr(opType, rightVar, leftImm)
                }
              case leftVar@CfgVariable(_, _) =>
                rightExpr match {
                  case rightImm@CfgImmediate(_) =>
                    expression
                  case rightVar@CfgVariable(_, _) =>
                    if (rightVar.identifier < leftVar.identifier) {
                      CfgBinOpExpr(opType, rightVar, leftVar)
                    } else {
                      expression
                    }
                }
            }
          case _ => expression
        }
      case _ => expression
    }
  }

  /**
   * Finds all expressions generated in the block.
   * Kills any generated expressions if variable is redefined
   *
   * @param block the current block
   * @param varToExprs a mapping from CfgVariable to set of CfgExpressions that contains the variable
   * @return a list of generated CfgExpressions in the current block
   */
  private def gen(block: CfgBasicBlock, varToExprs: Map[CfgVariable, Set[CfgExpression]]): Set[CfgExpression] = {
    val currGenExpressions = mutable.Set.empty[CfgExpression]

    block.statements.foreach {
      case CfgRegAssignStatement(to, value) =>
        value match {
          case CfgBinOpExpr(_, _, _) =>
            currGenExpressions.add(canonicalization(value))
          case CfgArrayReadExpr(_, _) =>
            currGenExpressions.add(value)
          case _ =>
        }

        to.index match {
          case Some(_) =>
          case None =>
            if (varToExprs.contains(to.variable)) {
              val currKilledExpressions = (varToExprs(to.variable).intersect(currGenExpressions))
              currGenExpressions --= currKilledExpressions
            }
        }
      case _ =>
    }

    currGenExpressions.toSet
  }

  /**
   * Finds all killed expressions
   *
   * @param block the current block
   * @param varToExprs a mapping from CfgVariable to set of CfgExpressions that contains the variable
   * @return a list of killed CfgExpressions in the current block
   */
  private def kill(block: CfgBasicBlock, varToExprs: Map[CfgVariable, Set[CfgExpression]]): Set[CfgExpression] = {
    val currKilledExpressions = mutable.Set.empty[CfgExpression]

    block.statements.foreach {
      case CfgRegAssignStatement(to, _) =>
        to.index match {
          case Some(_) =>
            currKilledExpressions.add(CfgArrayReadExpr(to.variable, to.index.get))
          case None =>
            if (varToExprs.contains(to.variable)) {
              val killedExpressions = varToExprs(to.variable)
              currKilledExpressions ++= killedExpressions
            }
        }
      case _ =>
    }

    currKilledExpressions.toSet
  }

  /**
   * Computes the available expressions for all blocks within a (simplified) scope.
   *
   * @param scope the scope containing information about the blocks.
   * @param allExpressions set of (canonicalize) expressions in the scope
   * @param varToExprs set of variables to the set of expressions that contains the variable
   * @return the set of available expressions at the beginning of each basic block
   */
  def compute(scope: CfgScope, allExpressions: Set[CfgExpression], varToExprs: Map[CfgVariable, Set[CfgExpression]]): Map[String, Set[CfgExpression]] = {
    val in = mutable.Map.empty[String, Set[CfgExpression]]
    val out = mutable.Map.empty[String, Set[CfgExpression]]
    val predecessors = scope.allPredecessors
    val successors = scope.allSuccessors

    scope.basicBlocks.keys.foreach(label =>
      out.update(label, allExpressions)
    )

    in.update(scope.entry, Set.empty)
    out.update(scope.entry, gen(scope.basicBlocks(scope.entry), varToExprs))

    var changed: immutable.TreeSet[String] = immutable.TreeSet[String]() ++ (scope.basicBlocks.keySet - scope.entry) // Changed = N - { entry }, where N = all blocks in CFG
    while (changed.nonEmpty) {
      val currBlock = changed.head // choose a block n in changed
      changed -= currBlock

      in.update(currBlock, allExpressions)
      for (predecessorBlock <- predecessors(currBlock)) {
        val currIn: Set[CfgExpression] = in(currBlock)
        val predOut: Set[CfgExpression] = out(predecessorBlock)
        in.update(currBlock, currIn.intersect(predOut))
      }

      val prevOut: Set[CfgExpression] = out(currBlock)

      val currIn: Set[CfgExpression] = in(currBlock)
      val currGen: Set[CfgExpression] = gen(scope.basicBlocks(currBlock), varToExprs)
      val currKill: Set[CfgExpression] = kill(scope.basicBlocks(currBlock), varToExprs)
      out.update(currBlock, currGen.union(currIn.diff(currKill)))

      val currOut: Set[CfgExpression] = out(currBlock)
      if (currOut != prevOut) {
        for (successorBlock <- successors(currBlock)) {
          changed += successorBlock
        }
      }
    }

    in.toMap
  }
}
