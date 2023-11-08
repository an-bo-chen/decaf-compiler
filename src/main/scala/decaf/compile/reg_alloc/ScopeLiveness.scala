package decaf.compile.reg_alloc

import decaf.compile.{CfgRegularBlock, CfgScope}
import decaf.compile.cfg._
import decaf.compile.optim.dead_code_elimination.LivenessInfo
import decaf.compile.reg_alloc.types._
import decaf.compile.optim.dead_code_elimination.Liveness.{compute, getExprUsed, getStatementAssignedUsed}

import scala.collection.mutable

object ScopeLiveness {
  type LivenessScope = Scope[InfoBasicBlock[Set[CfgVariable]]]
  /**
   * For every statement in a scope, compute the variables
   * live both before and after the statement.
   *
   * @param scope the scope to compute liveness on
   * @return scope with the liveness information
   */
  def getScopeLiveness(scope: CfgScope, globalVariables: Set[CfgVariable]): LivenessScope = {
    val livenessInfoWithGlobals = compute(scope, globalVariables)

    val allArrayVariables = scope.basicBlocks
      .flatMap({ case (_, block) => block.fieldDecls})
      .filter(fieldDecl => fieldDecl.isInstanceOf[CfgArrayFieldDecl])
      .map(fieldDecl => fieldDecl.variable).toSet

    val excludeVariables = globalVariables ++ allArrayVariables

    val livenessInfo = livenessInfoWithGlobals.map({ case (block, info) =>
      // filter out globals and arrays
      def filterSet(set: Set[CfgVariable]): Set[CfgVariable] = {
        set.diff(excludeVariables)
      }
      (block, LivenessInfo(filterSet(info.before), filterSet(info.after)))
    })

    val newBlocks: Map[String, InfoBasicBlock[Set[CfgVariable]]] = scope.basicBlocks.map({
      case (label, block) =>
        val statementLivenessInfo = livenessInfo(label)

        val newBlock: InfoBasicBlock[Set[CfgVariable]] = block match {
          case RegularBlock(fieldDecls, statements) =>
            val afterInfos = computeStatementAfterInfo(
              block.statements,
              statementLivenessInfo.before,
              statementLivenessInfo.after,
              excludeVariables
            )
            InfoRegularBlock(
              fieldDecls,
              statements.zip(afterInfos)
            )
          case ConditionalBlock(fieldDecls, statements, condition) =>
            val afterInfos = computeStatementAfterInfo(
              block.statements,
              statementLivenessInfo.before,
              statementLivenessInfo.after ++ getExprUsed(condition),
              excludeVariables
            )
            InfoConditionalBlock(
              fieldDecls,
              statements.zip(afterInfos),
              (condition, computeConditionAfterInfo(
                condition,
                if (afterInfos.isEmpty) statementLivenessInfo.before else afterInfos.last,
                statementLivenessInfo.after,
                excludeVariables
              ))
            )
        }
        (label, newBlock)
    })

    scope.copy(basicBlocks = newBlocks)
  }

  /**
   * For every statement, compute the live variables directly after those statements.
   *
   * @param statements the list of statements
   * @param liveBefore variables live before this list of statements
   * @param liveAfter variables live after
   * @param exclude variables to exclude from this analysis
   * @return the above mentioned information as a parallel list
   */
  def computeStatementAfterInfo(
    statements: List[CfgStatement],
    liveBefore: Set[CfgVariable],
    liveAfter: Set[CfgVariable],
    exclude: Set[CfgVariable]): List[Set[CfgVariable]] = {
    val afterSetList = mutable.ListBuffer.empty[Set[CfgVariable]]

    // because liveness analysis goes backwards, there's a bit of awkwardness here
    // we start with liveAfter and then go backwards, computing the variables used before each statement
    var nextStatementRequires = liveAfter
    statements.reverseIterator.foreach(statement => {
      val (maybeAssignVariable, used) = getStatementAssignedUsed(statement)

      // we have to be very careful about the ordering of these operations
      // first, this statement needs the variables required by the next statement,
      // except for the variable that this statement assigns to
      // and also variables that this statement uses. This must be added last
      // since it has greatest priority- imagine statements that assign and use the same variable.

      val statementLiveBefore = (maybeAssignVariable match {
        case Some(x) => nextStatementRequires - x
        case None => nextStatementRequires
      }) ++ used -- exclude

      nextStatementRequires = statementLiveBefore
      // prepend since we are iterating backwards
      afterSetList.+=:(nextStatementRequires)
    })

    // liveBefore should only be needed for this sanity check
    // assert(liveBefore == afterSetList.head)

    afterSetList.toList
  }

  /**
   * See `computeStatementAfterInfo`. This is similar, except for a condition rather than
   * a list of statements.
   *
   * @return live variables directly after this condition.
   */
  def computeConditionAfterInfo(
    condition: CfgCondExpression,
    liveBefore: Set[CfgVariable],
    liveAfter: Set[CfgVariable],
    exclude: Set[CfgVariable]): Set[CfgVariable] = {
    (getExprUsed(condition).toSet ++ liveBefore ++ liveAfter) -- exclude
  }
}
