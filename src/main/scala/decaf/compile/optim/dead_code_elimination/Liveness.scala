package decaf.compile.optim.dead_code_elimination

import decaf.compile._
import decaf.compile.cfg._

import scala.collection.mutable

/**
 * Contains liveness information for a block.
 *
 * @param before variables live before the block
 * @param after variables live after the block
 */
case class LivenessInfo (
  before: Set[CfgVariable],
  after: Set[CfgVariable]
)

/**
 * Mutable version of `LivenessInfo`
 */
private[dead_code_elimination] case class MutableLivenessInfo (
  var before: mutable.Set[CfgVariable] = mutable.Set.empty,
  var after: mutable.Set[CfgVariable] = mutable.Set.empty
) {
  def toLivenessInfo: LivenessInfo = LivenessInfo(before.toSet, after.toSet)
}

/**
 * Computes liveness information.
 */
object Liveness {
  /**
   * Computes the liveness information for all blocks within a (simplified) scope.
   *
   * @param scope the scope containing information about the blocks.
   * @param globalVariables the variables global to `scope`.
   * @return block -> `LivenessInfo` for all blocks in scope.basicBlocks.
   */
  def compute(scope: CfgScope, globalVariables: Set[CfgVariable]): Map[String, LivenessInfo] = {
    val result = scope.basicBlocks.map({
      case (label, _) => (label, MutableLivenessInfo())
    })

    val (exitAssigned, exitUsed) = getBlockAssignedUsed(scope.basicBlocks(scope.exit))
    // this is not in the slides, but you have to account for global variables
    result(scope.exit).before ++= exitUsed ++ (globalVariables -- exitAssigned)
    // global variables considered live at end
    result(scope.exit).after ++= globalVariables

    // LIFO Queue because want to visit newly added blocks immediately,
    // to minimize visiting random blocks that changes have not propagated to yet
    var changedQueue = mutable.Stack((scope.basicBlocks.keySet - scope.exit).toSeq: _*)

    val allPredecessors = scope.allPredecessors
    val allSuccessors = scope.allSuccessors

    while (changedQueue.nonEmpty) {
      val currentBlock = changedQueue.pop()
      val (assigned, used) = getBlockAssignedUsed(scope.basicBlocks(currentBlock))

      val successorsBefore = allSuccessors(currentBlock).map(label => result(label).before.toSeq)

      // get all the variables that successor block uses by taking the union
      val successorsUsed = successorsBefore.foldLeft(mutable.Set.empty[CfgVariable])(
        (prev, value) => prev ++= value // union
      )

      val currentBefore = result(currentBlock).before
      // new before includes:
      //  variables it needs from previous blocks
      //  variables subsequent blocks need but are not assigned values in this block
      val newBefore = used.to[mutable.Set] ++= (successorsUsed -- assigned)

      if (currentBefore != newBefore) {
        // before changed, need to update previous blocks with this information
        changedQueue ++= allPredecessors(currentBlock)
      }

      result(currentBlock).before = newBefore
      result(currentBlock).after = successorsUsed
    }


    result.map({
      case (label, info) => (label, info.toLivenessInfo)
    })
  }

  /**
   * Computes the assigned and used variables of a basic block.
   * Assignments to array indices do not count as assignments to the variable.
   * Index accesses of an array do count as uses of the array variable.
   *
   * @param block the basic block.
   * @return Tuple2, where:
   *         the first element is the set of CfgVariables assigned new values in block,
   *         the second element is the set of variables with upward exposed uses in the block.
   */
  def getBlockAssignedUsed(block: CfgBasicBlock): (Set[CfgVariable], Set[CfgVariable]) = {
    val assigned = mutable.Set.empty[CfgVariable]
    val used = mutable.Set.empty[CfgVariable]

    // condition is last, so we look at it first for upwards use
    block match {
      case RegularBlock(_, _) =>
      case ConditionalBlock(_, _, condition) =>
        used ++= getExprUsed(condition)
    }

    // loop reversed for upwards use
    block.statements.reverseIterator.foreach(statement => {
      val (statementAssigned, statementUsed) = getStatementAssignedUsed(statement)
      statementAssigned match {
        case Some(x) =>
          assigned += x
          used -= x
        case None =>
      }
      // it is important this happens last since uses in the same statement occur before assign
      used ++= statementUsed
    })

    (assigned.toSet, used.toSet)
  }

  /**
   * Computes the assigned and used variables of a statement.
   *
   * @param statement the statement
   * @return Tuple2, where:
   *         the first element is the set of CfgVariables assigned new values,
   *         the second element is the set of variables used.
   */
  def getStatementAssignedUsed(statement: CfgStatement): (Option[CfgVariable], Iterable[CfgVariable]) = {
    var assigned: Option[CfgVariable] = None
    val used = mutable.ListBuffer.empty[CfgVariable]

    statement match {
      case statement: CfgAssignStatement => statement match {
        case CfgRegAssignStatement(to, value) =>
          to.index match {
            case Some(x) =>
              // assignments to array indices do not count as assignments to the variable.
              // but the index counts as a use!
              used ++= getExprUsed(x)
            case None =>
              assigned = Some(to.variable)
          }
          used ++= getExprUsed(value)
        case CfgArrayAssignStatement(to, _) =>
          // this is different from earlier because it assigns the entire array
          // (and semantically only occurs at the initial declaration)
          assigned = Some(to.variable)
      }
      case CfgFunctionCallStatement(_, parameters) =>
        parameters.foreach({
          case CfgFunctionCallValueParam(param) =>
            used ++= getExprUsed(param)
          case CfgFunctionCallStringParam(_) =>
        })
      case CfgReturnStatement(value) =>
        value match {
          case Some(x) => used ++= getExprUsed(x)
          case None =>
        }
    }

    (assigned, used)
  }

  /**
   * Computes the used variables of an expression.
   *
   * @param expr the expression.
   * @return set of used variables.
   */
  def getExprUsed(expr: CfgExpression): Iterable[CfgVariable] = {
    val result = mutable.ListBuffer.empty[CfgVariable]

    def _processCfgValue(value: CfgValue): Unit = {
      value match {
        case CfgImmediate(_) =>
        case variable: CfgVariable => result += variable
      }
    }

    expr match {
      case value: CfgValue =>
        _processCfgValue(value)
      case CfgArrayReadExpr(variable, index) =>
        // Index accesses of an array do count as uses of the array variable.
        result += variable
        _processCfgValue(index)
      case CfgLenExpr(variable) =>
        result += variable
      case CfgFunctionCallExpr(_, params) =>
        params.foreach {
          case CfgFunctionCallValueParam(param) => _processCfgValue(param)
          case CfgFunctionCallStringParam(_) =>
        }
      case CfgUnaryOpExpr(_, expr) =>
        _processCfgValue(expr)
      case CfgBinOpExpr(_, leftExpr, rightExpr) =>
        _processCfgValue(leftExpr)
        _processCfgValue(rightExpr)
    }

    result
  }
}
