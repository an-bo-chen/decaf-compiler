package decaf.compile.reg_alloc

import decaf.compile.reg_alloc.ScopeLiveness.LivenessScope
import decaf.compile.reg_alloc.types._
import decaf.compile.cfg._
import scala.collection.mutable

object InterferenceGraph {

  private def buildFromLivesnessInfo(graph: mutable.Map[CfgVariable, Set[CfgVariable]], allLivenessInfo: List[Set[CfgVariable]]): Unit = {
    allLivenessInfo.foreach(livenessInfo =>
      for (variable <- livenessInfo) {
        val neighbors: Set[CfgVariable] = livenessInfo - variable
        if (graph.contains(variable)) {
          graph.update(variable, graph(variable) ++ neighbors)
        } else {
          graph.update(variable, neighbors)
        }
      }
    )
  }

  /**
   * Builds the Register Interference Graph where each node represents a web (i.e. the Cfgvariable)
   * and each edge represents an interference between two webs at some program point.
   *
   * @param scope the scope with liveness information for each statement
   * @return the Register Interference Graph represented as an adjacency set
   */
  def buildGraph(scope: LivenessScope): Map[CfgVariable, Set[CfgVariable]] = {
    val graph = mutable.Map.empty[CfgVariable, Set[CfgVariable]]

    scope.orderedBlocks.foreach{ case (_, block) =>
      block match {
        case InfoRegularBlock(_, statements) =>
          val blockLivenessInfo: List[Set[CfgVariable]] = statements.map(s => s._2)
          buildFromLivesnessInfo(graph, blockLivenessInfo)
        case InfoConditionalBlock(_, statements, condition) =>
          val blockLivenessInfo: List[Set[CfgVariable]] = statements.map(s => s._2)  :+ condition._2
          buildFromLivesnessInfo(graph, blockLivenessInfo)
      }
    }

    graph.toMap
  }

  private def heuristic(graph: Map[CfgVariable, Set[CfgVariable]]): (CfgVariable, Set[CfgVariable]) = {
    // Pick node v with the most neighbors in the graph
    graph.maxBy{ case (_, neighbors) => neighbors.size }
  }

  private def removeFromGraph(graph: mutable.Map[CfgVariable, Set[CfgVariable]], node: CfgVariable, neighbors: Set[CfgVariable]): Unit = {
    // Remove the node from each of its neighbor's adjacency set
    neighbors.foreach(neighbor =>
      graph.update(neighbor, graph(neighbor) - node)
    )
    // Remove node and its edges from graph
    graph.remove(node)
  }

  private def addToGraph(graph: mutable.Map[CfgVariable, Set[CfgVariable]], node: CfgVariable, neighbors: Set[CfgVariable]): Unit = {
    // Add the node to each of its neighbor's adjacency set
    neighbors.foreach(neighbor =>
      graph.update(neighbor, graph(neighbor) + node)
    )
    // Add node and its edges to graph
    graph.update(node, neighbors)
  }

  /**
   * Colors a Register Interference Graph from a set of available registers.
   *
   * @param graph the Register Interference Graph represented as an adjacency set
   * @param virtualRegisters the set of available registers to color the Register Interference Graph with
   * @return Tuple2 containing a mapping from each web to register and the set of webs that have been spilled
   */
  def colorGraph(graph: Map[CfgVariable, Set[CfgVariable]], virtualRegisters: Set[VirtualRegister]): (Map[CfgVariable, VirtualRegister], Set[CfgVariable]) = {
    val currGraph = mutable.Map[CfgVariable, Set[CfgVariable]](graph.toSeq: _*)
    val k = virtualRegisters.size
    val stack = mutable.Stack[(CfgVariable, Set[CfgVariable])]()

    val varToReg = mutable.Map.empty[CfgVariable, VirtualRegister]
    val spilled = mutable.Set.empty[CfgVariable]

    while (currGraph.nonEmpty) {
      currGraph.find { case (_, neighbor) => neighbor.size < k } match {

        // Pick a node with fewer than k neighbors. Resulting RIG will always be k-colorable
        case Some((node, neighbors)) =>
          // Place node and its current neighbors on stack to register allocate
          stack.push((node, neighbors))
          // Remove node and its edges from the graph
          removeFromGraph(currGraph, node, neighbors)

        // Pick a node to spill using heuristics
        case None =>
          val (node, neighbors) = heuristic(currGraph.toMap)
          // Place node and its current neighbors on stack to potentially register allocate
          stack.push((node, neighbors))
          // Remove node and its edges from the graph
          removeFromGraph(currGraph, node, neighbors)
      }
    }

    while (stack.nonEmpty) {
      val (node, neighbors) = stack.pop()
      addToGraph(currGraph, node, neighbors)

      val usedRegisters = neighbors.diff(spilled).map(neighbor => varToReg(neighbor))
      val availableRegs = virtualRegisters -- usedRegisters

      if (availableRegs.nonEmpty) {
        // TODO: Optimize on choosing which color
        // TODO: Currently using lowest virtual register number, which tends towards caller-save

        var lowestAvailable = availableRegs.head
        availableRegs.foreach(x => {
          if (x.number < lowestAvailable.number) {
            lowestAvailable = x
          }
        })

        val availableReg = lowestAvailable
        varToReg.update(node, availableReg)
      } else {
        spilled.add(node)
      }
    }

    (varToReg.toMap, spilled.toSet)
  }
}
