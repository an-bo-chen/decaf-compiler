package decaf.compile.optim.loop_optim

import decaf.compile.cfg.{CfgArrayAssignStatement, CfgArrayReadExpr, CfgAssignStatement, CfgBinOpExpr, CfgCondExpression, CfgFunctionCallExpr, CfgFunctionCallStatement, CfgFunctionCallStringParam, CfgFunctionCallValueParam, CfgImmediate, CfgLenExpr, CfgRegAssignStatement, CfgReturnStatement, CfgUnaryOpExpr, CfgValue, CfgVariable, ConditionalBlock, RegularBlock}
import decaf.compile.optim.dead_code_elimination.Liveness.getExprUsed
import decaf.compile.{CfgProgram, CfgScope}

import collection.mutable

object LoopOptim {
  def visitProgram(program: CfgProgram): CfgProgram = {
    program.copy(
      functions = program.functions.map(func => func.copy(
        scope = visitScope(func.scope, program.globalVariables.map(decl => decl.variable).toSet))
      )
    )
  }

  def visitScope(scope: CfgScope, globalVariables: Set[CfgVariable]): CfgScope = {
    LoopInvariant.optimize(scope, globalVariables)
  }

  /**
   * Given:
   *
   * @param scope the CfgScope
   * @return for every block, all blocks that dominate it
   */
  def dominators(scope: CfgScope): Map[String, Set[String]] = {
    val allBlocks = scope.basicBlocks.keys.toSet
    val result = mutable.Map[String, Set[String]](
      scope.basicBlocks.keys.map(key => (key, allBlocks)).toSeq: _*
    )
    result += (scope.entry -> Set(scope.entry))

    val allPredecessors = scope.allPredecessors
    val allSuccessors = scope.allSuccessors

    val workList = mutable.Stack[String](allSuccessors(scope.entry).toSeq: _*)
    while (workList.nonEmpty) {
      val current = workList.pop()
      // only dominator of scope entry is itself (hidden "predecessor" of entry)
      // necessary because this algorithm doesn't work if there is a loop edge back to entry
      if (current != scope.entry) {
        val currentResult = result(current)
        val nextResult = allPredecessors(current).map(block => result(block))
          .reduce((prev, cur) => prev & cur) + current

        if (nextResult.size < currentResult.size) {
          result += (current -> nextResult)
          workList.pushAll(allSuccessors(current))
        } else if (nextResult.size > currentResult.size) {
          throw new IllegalStateException("There's something wrong with the dominator tree computation")
        }
      }
    }

    result.toMap.map({ case (str, strings) => (str, strings)})
  }

  /**
   * Given a scope and its dominators, compute all the loops in the scope.
   *
   * @param scope the scope
   * @param dominators the dominator tree in the form returned from `dominators`.
   * @return for all loops, the loop header mapped to the blocks in the loop (not including the header).
   */
  def findLoops(scope: CfgScope, dominators: Map[String, Set[String]]): Map[String, Set[String]] = {
    // this follows the algorithm in the slide almost word for word
    // note: convert to list because multiple edges possible for one node, avoid overwriting
    val allBackEdges = (scope.regCfg.toList ++ scope.trueCfg.toList ++ scope.falseCfg.toList).filter({ case (from, to) =>
      dominators(from).contains(to) // head dominates tail
    })

    val allHeaders = allBackEdges.map({ case (_, to) => to}).toSet
    val allPredecessors = scope.allPredecessors

    // put all headers into the result (result also includes header)
    val allLoops = Map[String, mutable.Set[String]](allHeaders.map(header => (header, mutable.Set(header))).toSeq: _*)

    allBackEdges.foreach({ case (tail, header) =>
      // avoid empty loops, they already include the header and would break the algorithm otherwise
      if (tail != header) {
        val headerResult = allLoops(header)
        // put the tail into the result
        headerResult.add(tail)

        val workList = mutable.Stack[String](tail)

        while (workList.nonEmpty) {
          val current = workList.pop()

          def insert(block: String): Unit = {
            if (!headerResult.contains(block)) {
              headerResult.add(block)
              workList.push(block)
            }
          }

          allPredecessors(current).foreach(pred =>
            insert(pred)
          )
        }
      }
    })

    allLoops.map({ case (header, blocks) => (header, blocks.toSet)})
  }
}
