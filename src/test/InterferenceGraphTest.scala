import org.scalatest.funsuite.AnyFunSuite
import TestUtils._
import decaf.compile._
import decaf.compile.ir._
import decaf.compile.cfg._
import decaf.compile.reg_alloc.types._
import decaf.compile.reg_alloc.InterferenceGraph
import decaf.compile.reg_alloc.ScopeLiveness

import scala.collection.immutable._

class InterferenceGraphTest extends AnyFunSuite {
  implicit val varOrdering: Ordering[CfgVariable] = Ordering.by((_:CfgVariable).identifier)

  test("create graph with only a single node") {
    val scope = Scope[CfgBasicBlock](
      basicBlocks = TreeMap(getBlockName(1) -> RegularBlock[CfgStatement](
        fieldDecls = List.empty,
        statements = List(
          CfgRegAssignStatement(
            to = CfgLocation(getVariable(1), None),
            value = CfgImmediate(1)
          ),
          CfgRegAssignStatement(
            to = CfgLocation(getVariable(2), None),
            value = CfgBinOpExpr(
              op = ArithBinOpType.ADD,
              leftExpr = getVariable(1),
              rightExpr = CfgImmediate(2)
            )
          )
        )
      )),
      entry = getBlockName(1),
      exit = getBlockName(1),
      regCfg = TreeMap.empty,
      trueCfg = TreeMap.empty,
      falseCfg = TreeMap.empty
    )
    val livenessScope =  ScopeLiveness.getScopeLiveness(scope, Set.empty)
    val graph = InterferenceGraph.buildGraph(livenessScope)

    assert(graph === Map(
      getVariable(1) -> Set.empty
    ))
  }

  // Link: https://web.stanford.edu/class/archive/cs/cs143/cs143.1128/lectures/17/Slides17.pdf
  test("create graph from Stanford Slides at 130/234") {
    val scope = Scope[CfgBasicBlock](
      basicBlocks = TreeMap(
        getBlockName(1) -> ConditionalBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            CfgRegAssignStatement(
              to = CfgLocation(getVariable(1), None),
              value = CfgImmediate(1)
            ),
            CfgRegAssignStatement(
              to = CfgLocation(getVariable(2), None),
              value = CfgImmediate(1)
            ),
            CfgRegAssignStatement(
              to = CfgLocation(getVariable(3), None),
              value = CfgImmediate(1)
            ),
            CfgRegAssignStatement(
              to = CfgLocation(getVariable(4), None),
              value = CfgImmediate(1)
            ),
            CfgRegAssignStatement(
              to = CfgLocation(getVariable(5), None),
              value = CfgBinOpExpr(
                op = ArithBinOpType.ADD,
                leftExpr = getVariable(4),
                rightExpr = getVariable(1)
              )
            ),
            CfgRegAssignStatement(
              to = CfgLocation(getVariable(6), None),
              value = CfgBinOpExpr(
                op = ArithBinOpType.ADD,
                leftExpr = getVariable(2),
                rightExpr = getVariable(3)
              )
            ),
            CfgRegAssignStatement(
              to = CfgLocation(getVariable(6), None),
              value = CfgBinOpExpr(
                op = ArithBinOpType.ADD,
                leftExpr = getVariable(6),
                rightExpr = getVariable(2)
              )
            )
          ),
          condition = CfgImmediate(1)
        ),
        getBlockName(2) -> RegularBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            CfgRegAssignStatement(
              to = CfgLocation(getVariable(4), None),
              value = CfgBinOpExpr(
                op = ArithBinOpType.ADD,
                leftExpr = getVariable(5),
                rightExpr = getVariable(6)
              )
            )
          )
        ),
        getBlockName(3) -> RegularBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            CfgRegAssignStatement(
              to = CfgLocation(getVariable(4), None),
              value = CfgBinOpExpr(
                op = ArithBinOpType.SUB,
                leftExpr = getVariable(5),
                rightExpr = getVariable(6)
              )
            )
          )
        ),
        getBlockName(4) -> RegularBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            CfgRegAssignStatement(
              to = CfgLocation(getVariable(7), None),
              value = getVariable(4)
            ),
            CfgRegAssignStatement(
              to = CfgLocation(getVariable(8), None),
              value = getVariable(7)
            )
          )
        )
      ),
      entry = getBlockName(1),
      exit = getBlockName(4),
      regCfg = TreeMap(
        getBlockName(2) -> getBlockName(4),
        getBlockName(3) -> getBlockName(4)
      ),
      trueCfg = TreeMap(
        getBlockName(1) -> getBlockName(2)
      ),
      falseCfg = TreeMap(
        getBlockName(1) -> getBlockName(3)
      )
    )
    val livenessScope = ScopeLiveness.getScopeLiveness(scope, Set.empty)
    var graph = InterferenceGraph.buildGraph(livenessScope)
    graph = TreeMap(graph.toSeq: _*)

    assert(graph === TreeMap(
      getVariable(1) -> Set(
        getVariable(2),
        getVariable(3),
        getVariable(4)
      ),
      getVariable(2) -> Set(
        getVariable(1),
        getVariable(3),
        getVariable(4),
        getVariable(5),
        getVariable(6)
      ),
      getVariable(3) -> Set(
        getVariable(1),
        getVariable(2),
        getVariable(4),
        getVariable(5)
      ),
      getVariable(4) -> Set(
        getVariable(1),
        getVariable(2),
        getVariable(3)
      ),
      getVariable(5) -> Set(
        getVariable(2),
        getVariable(3),
        getVariable(6)
      ),
      getVariable(6) -> Set(
        getVariable(2),
        getVariable(5)
      ),
      getVariable(7) -> Set.empty
    ))
  }

  def checkGraphColoring(graph: Map[CfgVariable, Set[CfgVariable]], varToReg: Map[CfgVariable, VirtualRegister], spilled: Set[CfgVariable]): Boolean = {
    for ((node, neighbors) <- graph) {
      if (!spilled.contains(node)) {
        val color = varToReg(node)
        for (neighbor <- neighbors.diff(spilled)) {
          val neighborColor = varToReg(neighbor)
          if (color == neighborColor) {
            return false
          }
        }
      }
    }
    return true
  }

  test("graph should always be 3-colorable") {
    val graph: Map[CfgVariable, Set[CfgVariable]] = TreeMap(
      getVariable(1) -> Set(getVariable(2), getVariable(3)),
      getVariable(2) -> Set(getVariable(1), getVariable(3)),
      getVariable(3) -> Set(getVariable(1), getVariable(2))
    )

    val registers = Set(VirtualRegister(1), VirtualRegister(2), VirtualRegister(3))

    val (varToReg, spilled) = InterferenceGraph.colorGraph(graph, registers)

    assert(checkGraphColoring(graph, varToReg, spilled))

    assert(spilled.size === 0)
  }

  test("graph should be 4-colorable (1)") {
    val graph: Map[CfgVariable, Set[CfgVariable]] = TreeMap(
      getVariable(1) -> Set(
        getVariable(2),
        getVariable(3),
        getVariable(4)
      ),
      getVariable(2) -> Set(
        getVariable(1),
        getVariable(3),
        getVariable(4),
        getVariable(5),
        getVariable(6)
      ),
      getVariable(3) -> Set(
        getVariable(1),
        getVariable(2),
        getVariable(4),
        getVariable(5)
      ),
      getVariable(4) -> Set(
        getVariable(1),
        getVariable(2),
        getVariable(3)
      ),
      getVariable(5) -> Set(
        getVariable(2),
        getVariable(3),
        getVariable(6)
      ),
      getVariable(6) -> Set(
        getVariable(2),
        getVariable(5)
      ),
      getVariable(7) -> Set.empty
    )

    val registers = Set(VirtualRegister(1), VirtualRegister(2), VirtualRegister(3), VirtualRegister(4))

    val (varToReg, spilled) = InterferenceGraph.colorGraph(graph, registers)

    assert(checkGraphColoring(graph, varToReg, spilled))

    assert(spilled.size === 0)
  }

  test("graph should be 4-colorable (2)") {
    val graph: Map[CfgVariable, Set[CfgVariable]] = TreeMap(
      getVariable(1) -> Set(
        getVariable(2),
        getVariable(4),
        getVariable(6),
        getVariable(7)
      ),
      getVariable(2) -> Set(
        getVariable(1),
        getVariable(3),
        getVariable(4),
        getVariable(5)
      ),
      getVariable(3) -> Set(
        getVariable(2),
        getVariable(4),
        getVariable(5)
      ),
      getVariable(4) -> Set(
        getVariable(1),
        getVariable(2),
        getVariable(3),
        getVariable(6),
        getVariable(7)
      ),
      getVariable(5) -> Set(
        getVariable(2),
        getVariable(3),
        getVariable(6),
        getVariable(7)
      ),
      getVariable(6) -> Set(
        getVariable(1),
        getVariable(4),
        getVariable(5),
        getVariable(7)
      ),
      getVariable(7) -> Set(
        getVariable(1),
        getVariable(4),
        getVariable(5),
        getVariable(6)
      )
    )

    val registers = Set(VirtualRegister(1), VirtualRegister(2), VirtualRegister(3), VirtualRegister(4))

    val (varToReg, spilled) = InterferenceGraph.colorGraph(graph, registers)

    assert(checkGraphColoring(graph, varToReg, spilled))

    assert(spilled.size === 0)
  }
  test("graph should always have a spilled web") {
    val graph: Map[CfgVariable, Set[CfgVariable]] = TreeMap(
      getVariable(1) -> Set(
        getVariable(2),
        getVariable(3),
        getVariable(4)
      ),
      getVariable(2) -> Set(
        getVariable(1),
        getVariable(3),
        getVariable(4),
        getVariable(5),
        getVariable(6)
      ),
      getVariable(3) -> Set(
        getVariable(1),
        getVariable(2),
        getVariable(4),
        getVariable(5)
      ),
      getVariable(4) -> Set(
        getVariable(1),
        getVariable(2),
        getVariable(3)
      ),
      getVariable(5) -> Set(
        getVariable(2),
        getVariable(3),
        getVariable(6)
      ),
      getVariable(6) -> Set(
        getVariable(2),
        getVariable(5)
      ),
      getVariable(7) -> Set.empty
    )

    val registers = Set(VirtualRegister(1), VirtualRegister(2), VirtualRegister(3))

    val (varToReg, spilled) = InterferenceGraph.colorGraph(graph, registers)

    assert(checkGraphColoring(graph, varToReg, spilled))

    assert(spilled.size === 1)
  }

  test("integration from CFG to Reg Assignment from Stanford paper at 130/234") {
    val scope = Scope[CfgBasicBlock](
      basicBlocks = TreeMap(
        getBlockName(1) -> ConditionalBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            CfgRegAssignStatement(
              to = CfgLocation(getVariable(1), None),
              value = CfgImmediate(1)
            ),
            CfgRegAssignStatement(
              to = CfgLocation(getVariable(2), None),
              value = CfgImmediate(1)
            ),
            CfgRegAssignStatement(
              to = CfgLocation(getVariable(3), None),
              value = CfgImmediate(1)
            ),
            CfgRegAssignStatement(
              to = CfgLocation(getVariable(4), None),
              value = CfgImmediate(1)
            ),
            CfgRegAssignStatement(
              to = CfgLocation(getVariable(5), None),
              value = CfgBinOpExpr(
                op = ArithBinOpType.ADD,
                leftExpr = getVariable(4),
                rightExpr = getVariable(1)
              )
            ),
            CfgRegAssignStatement(
              to = CfgLocation(getVariable(6), None),
              value = CfgBinOpExpr(
                op = ArithBinOpType.ADD,
                leftExpr = getVariable(2),
                rightExpr = getVariable(3)
              )
            ),
            CfgRegAssignStatement(
              to = CfgLocation(getVariable(6), None),
              value = CfgBinOpExpr(
                op = ArithBinOpType.ADD,
                leftExpr = getVariable(6),
                rightExpr = getVariable(2)
              )
            )
          ),
          condition = CfgImmediate(1)
        ),
        getBlockName(2) -> RegularBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            CfgRegAssignStatement(
              to = CfgLocation(getVariable(4), None),
              value = CfgBinOpExpr(
                op = ArithBinOpType.ADD,
                leftExpr = getVariable(5),
                rightExpr = getVariable(6)
              )
            )
          )
        ),
        getBlockName(3) -> RegularBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            CfgRegAssignStatement(
              to = CfgLocation(getVariable(4), None),
              value = CfgBinOpExpr(
                op = ArithBinOpType.SUB,
                leftExpr = getVariable(5),
                rightExpr = getVariable(6)
              )
            )
          )
        ),
        getBlockName(4) -> RegularBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            CfgRegAssignStatement(
              to = CfgLocation(getVariable(7), None),
              value = getVariable(4)
            ),
            CfgRegAssignStatement(
              to = CfgLocation(getVariable(8), None),
              value = getVariable(7)
            )
          )
        )
      ),
      entry = getBlockName(1),
      exit = getBlockName(4),
      regCfg = TreeMap(
        getBlockName(2) -> getBlockName(4),
        getBlockName(3) -> getBlockName(4)
      ),
      trueCfg = TreeMap(
        getBlockName(1) -> getBlockName(2)
      ),
      falseCfg = TreeMap(
        getBlockName(1) -> getBlockName(3)
      )
    )
    val livenessScope = ScopeLiveness.getScopeLiveness(scope, Set.empty)
    var graph = InterferenceGraph.buildGraph(livenessScope)
    graph = TreeMap(graph.toSeq: _*)

    val registers1 = Set(VirtualRegister(1), VirtualRegister(2), VirtualRegister(3), VirtualRegister(4))

    val (varToReg1, spilled1) = InterferenceGraph.colorGraph(graph, registers1)

    assert(checkGraphColoring(graph, varToReg1, spilled1))
    assert(spilled1.size === 0)

    val registers2 = Set(VirtualRegister(1), VirtualRegister(2), VirtualRegister(3))

    val (varToReg2, spilled2) = InterferenceGraph.colorGraph(graph, registers2)

    assert(checkGraphColoring(graph, varToReg2, spilled2))
    assert(spilled2.size === 1)
  }
}
