package decaf.compile.cfg

import decaf.compile.CfgBasicBlock

import scala.collection.mutable.ListBuffer
import scala.collection.{immutable, mutable}

/**
 * Represents a destructed scope.
 *
 * @param basicBlocks maps `String` labels to `CfgBasicBlock`s
 * @param entry       the label of the entry block.
 * @param exit        the label of the exit block.
 * @param regCfg      a graph of the regular edges in the CFG, such that a -> b is an edge iff regCfg[a] = b.
 *                    Note that each block may have at most 1 outgoing regular edge, though they may have unlimited
 *                    incoming edges. All refCfg keys refer to a CfgRegularBlock.
 * @param trueCfg     a graph of the "true" edges in the CFG for conditional blocks,
 *                    to be taken if the expression in the conditional block evaluates true.
 *                    Defined similarly to regCfg. All trueCfg keys refer to a CfgConditionalBlock.
 * @param falseCfg    Similar to trueCfg, but with false instead of true.
 */
case class Scope[Block](
  basicBlocks: immutable.Map[String, Block],
  entry: String,
  exit: String,
  regCfg: immutable.Map[String, String],
  trueCfg: immutable.Map[String, String],
  falseCfg: immutable.Map[String, String]
) {
  /**
   * Returns the topological sort of the CFG (not including back edgeS)
   *
   * @return list of (label) -> block in a topological sort
   */
  def orderedBlocks: List[(String, Block)] = {
    val result = mutable.ListBuffer.empty[String]

    // keeps track of nodes that have been visited
    val visited = mutable.Set.empty[String]

    def topoSort(start: String): Unit = {
      visited += start
      // because we reverse later, visit true before false if we want
      // false to appear before true in the output
      List(regCfg, trueCfg, falseCfg).foreach(graph =>
        graph.get(start) match {
          case Some(x) => if (!visited.contains(x)) topoSort(x)
          case None =>
        }
      )
      result += start
    }

    topoSort(entry)
    assert(visited.size == basicBlocks.size, "Didn't visit all basic blocks")
    result.reverse.map(label => (label, basicBlocks(label))).toList
  }

  /**
   * @return set of predecessors for all blocks in basicBlocks.
   */
  lazy val allPredecessors: Map[String, Iterable[String]] = {
    val result = basicBlocks.map({
      case (label, _) => (label, mutable.ListBuffer.empty[String])
    })
    for ((from, to) <- regCfg.toSeq ++ trueCfg.toSeq ++ falseCfg.toSeq) {
      result(to) += from
    }
    result
  }

  /**
   * @return set of successors for all blocks in basicBlocks.
   */
  lazy val allSuccessors: Map[String, Iterable[String]] = {
    val result = basicBlocks.map({
      case (label, _) => (label, mutable.ListBuffer.empty[String])
    })
    for ((from, to) <- regCfg.toSeq ++ trueCfg.toSeq ++ falseCfg.toSeq) {
      result(from) += to
    }
    result
  }

  override def toString: String = {
    orderedBlocks
      .map({
        case (label, block) =>
          val header = (if (entry == label) " @entry" else "") +
            (if (exit == label) " @exit" else "")
          val regCfgString = regCfg.get(label) match {
            case Some(x) => s"\n--> $x"
            case None => ""
          }
          val trueCfgString = trueCfg.get(label) match {
            case Some(x) => s"\nT-> $x"
            case None => ""
          }
          val falseCfgString = falseCfg.get(label) match {
            case Some(x) => s"\nF-> $x"
            case None => ""
          }
          s"""$label$header${block.toString}$regCfgString$trueCfgString$falseCfgString""".stripMargin
      }).mkString("\n\n")
  }
}

/**
 * Mutable version of CfgScope for performance optimizations
 */
case class MutableScope[Block](
  basicBlocks: mutable.Map[String, Block],
  var entry: String,
  var exit: String,
  regCfg: mutable.Map[String, String],
  trueCfg: mutable.Map[String, String],
  falseCfg: mutable.Map[String, String]
) {

  def this(scope: Scope[Block]) = {
    this(
      mutable.Map.empty[String, Block],
      scope.entry,
      scope.exit,
      mutable.Map(scope.regCfg.toSeq: _*),
      mutable.Map(scope.trueCfg.toSeq: _*),
      mutable.Map(scope.falseCfg.toSeq: _*)
    )
    scope.basicBlocks.foreach({
      case (label, block) => basicBlocks += (label -> block)
    })
  }

  def toScope: Scope[Block] = {
    Scope(
      basicBlocks.toMap,
      entry,
      exit,
      regCfg.toMap,
      trueCfg.toMap,
      falseCfg.toMap
    )
  }
}