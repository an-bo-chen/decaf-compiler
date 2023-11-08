package decaf.compile.optim.constant_copy_propagation

import decaf.compile._
import decaf.compile.cfg._
import decaf.compile.Compiler.{OPTIMIZE_CP, OPTIMIZE_CSP}

import scala.collection.{immutable, mutable}

object AvailableCopies {

  /**
   * Finds all copies generated in the block.
   * Kills any generated copies if variable is redefined
   *
   * @param block the current block
   * @param varToCopies a mapping of variable to a set of copy statements
   * @return a set of generated copies in the current block
   */
  private def gen(block: CfgBasicBlock, varToCopies: Map[CfgVariable, Set[CfgRegAssignStatement]]): Set[CfgRegAssignStatement] = {
    val currGenCopies = mutable.Set.empty[CfgRegAssignStatement]
    block.statements.foreach {
      case statement@CfgRegAssignStatement(to, value) =>
        if (to.index.isEmpty) {
          if (varToCopies.contains(to.variable)) {
            val currKilledCopies = (varToCopies(to.variable).intersect(currGenCopies))
            currGenCopies --= currKilledCopies
          }

          value match {
            case CfgImmediate(_) =>
              if (!OptimCp.isGlobalVariable(to.variable) && OPTIMIZE_CSP) {
                currGenCopies.add(statement)
              }
            case variable@CfgVariable(_, _) =>
              if (!OptimCp.isGlobalVariable(to.variable) && !OptimCp.isGlobalVariable(variable) && OPTIMIZE_CP) {
                currGenCopies.add(statement)
              }
            case _ =>
          }
        }
      case _ =>
    }

    currGenCopies.toSet
  }

  /**
   * Finds all killed copies
   *
   * @param block the current block
   * @param varToCopies a mapping of variable to a set of copy statements
   * @return a list of killed copies in the current block
   */
  private def kill(block: CfgBasicBlock, varToCopies: Map[CfgVariable, Set[CfgRegAssignStatement]]): Set[CfgRegAssignStatement] = {
    val currKilledCopies = mutable.Set.empty[CfgRegAssignStatement]

    block.statements.foreach {
      case statement@CfgRegAssignStatement(to, _) =>
        if (to.index.isEmpty) {
          if (varToCopies.contains(to.variable)) {
            val killedCopies = varToCopies(to.variable)
            currKilledCopies ++= killedCopies
          }
        }
      case _ =>
    }

    currKilledCopies.toSet
  }

  /**
   * Computes the available copies for all blocks within a (simplified) scope.
   *
   * @param scope the scope containing information about the blocks.
   * @param allCopies set of copies in the scope
   * @param varToCopies a mapping of variable to a set of copy statements
   * @return the set of available copies at the beginning of each basic block
   */
  def compute(scope: CfgScope, allCopies: Set[CfgRegAssignStatement], varToCopies: Map[CfgVariable, Set[CfgRegAssignStatement]]): Map[String, Set[CfgRegAssignStatement]] = {
    val in = mutable.Map.empty[String, Set[CfgRegAssignStatement]]
    val out = mutable.Map.empty[String, Set[CfgRegAssignStatement]]
    val predecessors = scope.allPredecessors
    val successors = scope.allSuccessors

    scope.basicBlocks.keys.foreach(label =>
      out.update(label, allCopies)
    )

    in.update(scope.entry, Set.empty)
    out.update(scope.entry, gen(scope.basicBlocks(scope.entry), varToCopies))

    var changed: immutable.TreeSet[String] = immutable.TreeSet[String]() ++ (scope.basicBlocks.keySet - scope.entry) // Changed = N - { entry }, where N = all blocks in CFG
    while (changed.nonEmpty) {
      val currBlock = changed.head // choose a block n in changed
      changed -= currBlock

      in.update(currBlock, allCopies)
      for (predecessorBlock <- predecessors(currBlock)) {
        val currIn: Set[CfgRegAssignStatement] = in(currBlock)
        val predOut: Set[CfgRegAssignStatement] = out(predecessorBlock)
        in.update(currBlock, currIn.intersect(predOut))
      }

      val prevOut: Set[CfgRegAssignStatement] = out(currBlock)

      val currIn: Set[CfgRegAssignStatement] = in(currBlock)
      val currGen: Set[CfgRegAssignStatement] = gen(scope.basicBlocks(currBlock), varToCopies)
      val currKill: Set[CfgRegAssignStatement] = kill(scope.basicBlocks(currBlock), varToCopies)
      out.update(currBlock, currGen.union(currIn.diff(currKill)))

      val currOut: Set[CfgRegAssignStatement] = out(currBlock)
      if (currOut != prevOut) {
        for (successorBlock <- successors(currBlock)) {
          changed += successorBlock
        }
      }
    }

    in.toMap
  }
}
