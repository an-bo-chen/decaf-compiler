package decaf.compile.optim.dead_code_elimination

import decaf.compile._
import decaf.compile.cfg._

import scala.collection.mutable

object OptimDeadCode {
  def optimizeProgram(program: CfgProgram): CfgProgram = {
    program.copy(
      functions = program.functions.map(func => func.copy(
        scope = optimizeScope(func.scope, program.globalVariables.map(decl => decl.variable).toSet))
      )
    )
  }

  def optimizeScope(scope: CfgScope, globalVariables: Set[CfgVariable]): CfgScope = {
    var shouldOptimize = true
    var optimizedBasicBlocks = scope.basicBlocks

    while (shouldOptimize) {
      shouldOptimize = false
      val livenessInfo = Liveness.compute(scope.copy(basicBlocks = optimizedBasicBlocks), globalVariables)
      optimizedBasicBlocks = optimizedBasicBlocks.map({
        case (label, block) =>
          val (optimizedBlock, changed) = optimizeBlock(block, livenessInfo(label).after)
          shouldOptimize = shouldOptimize || changed
          (label, optimizedBlock)
      })
    }
    scope.copy(
      basicBlocks = optimizedBasicBlocks
    )
  }

  /**
   * Remove dead code from a block.
   *
   * @param block the unoptimized block
   * @param liveAfter variables live after this block
   * @return Tuple2 containing the optimized block with unnecessary statements removed, and boolean indicating
   *         whether the block was already optimal.
   */
  def optimizeBlock(block: CfgBasicBlock, liveAfter: Set[CfgVariable]): (CfgBasicBlock, Boolean) = {
    // note the condition for conditional blocks are never dead code, so no need to distinguish
    val newStatements = mutable.ListBuffer.empty[CfgStatement]
    val currentUsedVariables = liveAfter.to[mutable.Set]

    block match {
      case ConditionalBlock(_, _, condition) =>
        currentUsedVariables ++= Liveness.getExprUsed(condition)
      case _ =>
    }

    var optimized = false

    def _updateStatementUsed(stmt: CfgStatement, used: Iterable[CfgVariable]): Unit = {
      newStatements.+=:(stmt)
      currentUsedVariables ++= used
    }

    // traverse in reverse order for dead code elimination
    block.statements.reverseIterator.foreach(statement => {
      // note we prepend (newStatements +=:) because traverse in reverse
      val (assigned, used) = Liveness.getStatementAssignedUsed(statement)
      assigned match {
        case Some(x) =>
          if (currentUsedVariables.contains(x)) {
            // used, therefore this assignment is kept
            currentUsedVariables -= x
            // important that this is done last due to use and assign in the same statement
            _updateStatementUsed(statement, used)
          } else {
            // function call exprs can't simply be removed because they may have side effects
            statement match {
              case statement: CfgAssignStatement => statement match {
                case CfgRegAssignStatement(_, value) => value match {
                  case CfgFunctionCallExpr(identifier, params) =>
                     val newFunctionStatement = CfgFunctionCallStatement(
                      functionName = identifier,
                      parameters = params
                    )
                    _updateStatementUsed(newFunctionStatement, used)
                  case _ =>
                }
                case _ =>
              }
              case _ =>
            }
            optimized = true
          }
        case None =>
          _updateStatementUsed(statement, used)
      }
    })

    block match {
      case regularBlock: CfgRegularBlock =>
        (regularBlock.copy(statements = newStatements.toList), optimized)
      case conditionalBlock: CfgConditionalBlock =>
        (conditionalBlock.copy(statements = newStatements.toList), optimized)
    }
  }

}
