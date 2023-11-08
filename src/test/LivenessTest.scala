import TestUtils.{getAssignUsesStatement, getBlockName, getTestRegularBlock, getUsesStatement, getVariable}
import decaf.compile._
import decaf.compile.cfg._
import decaf.compile.ir.ArithBinOpType
import decaf.compile.optim.dead_code_elimination.{Liveness, LivenessInfo}
import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite

class LivenessTest extends AnyFunSuite {

  private def testBlock(
      block: CfgBasicBlock,
      expectedAssigned: Set[CfgVariable],
      expectedUsed: Set[CfgVariable]
  ): Assertion = {
    val (assigned, used) = Liveness.getBlockAssignedUsed(block)
    assert(expectedAssigned === assigned, "(assigned)")
    assert(expectedUsed === used, "(used)")
  }

  test("getAssignedUsed: should get all uses and declarations in a block, conditional counts as a use") {
    testBlock(
      block = ConditionalBlock[CfgStatement](
        fieldDecls = List.empty,
        statements = List(
          CfgRegAssignStatement(
            CfgLocation(getVariable(0), None),
            CfgBinOpExpr(ArithBinOpType.ADD, getVariable(1), getVariable(2))
          ),
          CfgFunctionCallStatement("", List(
            CfgFunctionCallValueParam(getVariable(3)),
            CfgFunctionCallValueParam(getVariable(4))
          ))
        ),
        condition = getVariable(5)
      ),
      expectedAssigned = Set(Range(0, 1).map(getVariable): _*),
      expectedUsed = Set(Range(1, 6).map(getVariable): _*)
    )
  }

  test("getAssignedUsed: array access count as uses, but array assignment indices do not count as assignments") {
    testBlock(
      block = RegularBlock[CfgStatement](
        fieldDecls = List.empty,
        statements = List(
          CfgArrayAssignStatement(
            CfgLocation(getVariable(0), None),
            List.empty
          ),
          CfgRegAssignStatement(
            CfgLocation(getVariable(1), Some(getVariable(2))),
            CfgArrayReadExpr(
              variable = getVariable(3),
              index = getVariable(4)
            )
          )
        )
      ),
      expectedAssigned = Set(List(0).map(getVariable): _*),
      expectedUsed = Set(List(2, 3, 4).map(getVariable): _*)
    )
  }

  test("getAssignedUsed: should only count upwards use") {
    testBlock(
      block = RegularBlock[CfgStatement](
        fieldDecls = List.empty,
        statements = List(
          CfgRegAssignStatement(
            CfgLocation(getVariable(0), None),
            getVariable(1)
          ),
          CfgRegAssignStatement(
            CfgLocation(getVariable(0), None),
            getVariable(0)
          )
        )
      ),
      expectedAssigned = Set(List(0).map(getVariable): _*),
      expectedUsed = Set(List(1).map(getVariable): _*)
    )
  }

  test("getAssignedUsed: assign happens after use in same statement") {
    testBlock(RegularBlock[CfgStatement](
      fieldDecls = List.empty,
      statements = List(
        getAssignUsesStatement(0,Iterable(0))
      )),
      expectedAssigned = Set(List(0).map(getVariable): _*),
      expectedUsed = Set(List(0).map(getVariable): _*)
    )
  }

  private def testCompute(
    scope: CfgScope,
    globalVariables: Set[CfgVariable],
      expected: Map[String, LivenessInfo]
  ): Assertion = {
    val result = Liveness.compute(scope, globalVariables)
    assert(result === expected)
  }

  test("compute: should work on a single block") {
    testCompute(
      scope = Scope[CfgBasicBlock](
        basicBlocks = Map(getBlockName(0) -> RegularBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            getAssignUsesStatement(0, Iterable(1, 2)),
            getAssignUsesStatement(2, Iterable(0, 3))
          )
        )),
        entry = getBlockName(0),
        exit = getBlockName(0),
        regCfg = Map.empty,
        trueCfg = Map.empty,
        falseCfg = Map.empty
      ),
      globalVariables = Set.empty,
      expected = Map(getBlockName(0) -> LivenessInfo(
        before = Set(List(1,2,3).map(getVariable): _*),
        after = Set.empty
      ))
    )
  }

  test("compute: should work blocks in sequence, global variables are live at end") {
    testCompute(
      scope = Scope[CfgBasicBlock](
        basicBlocks = Map(
          getBlockName(0) -> RegularBlock[CfgStatement](
            fieldDecls = List.empty,
            statements = List(
              getAssignUsesStatement(2, Iterable(0, 3))
            )
          ),
          getBlockName(1) -> RegularBlock[CfgStatement](
            fieldDecls = List.empty,
            statements = List(
              getAssignUsesStatement(0, Iterable(1, 2))
            )
          )
        ),
        entry = getBlockName(0),
        exit = getBlockName(1),
        regCfg = Map(getBlockName(0) -> getBlockName(1)),
        trueCfg = Map.empty,
        falseCfg = Map.empty
      ),
      globalVariables = Set(List(0,1).map(getVariable): _*),
      expected = Map(
        getBlockName(0) -> LivenessInfo(
          before = Set(List(0,1,3).map(getVariable): _*),
          after = Set(List(1, 2).map(getVariable): _*)
        ),
        getBlockName(1) -> LivenessInfo(
          before = Set(List(1,2).map(getVariable): _*),
          after = Set(List(0, 1).map(getVariable): _*)
        )
      )
    )
  }

  test("compute: should work on non-loop control flow (slides example)") {
    testCompute(
      scope = Scope[CfgBasicBlock](
        basicBlocks = Map(
          getBlockName(0) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty,
            statements = List(
              getAssignUsesStatement(0, Iterable(3,4)),
              getAssignUsesStatement(6, Iterable(0)),
              getAssignUsesStatement(2, Iterable(0, 3))
            ),
            condition = getVariable(0)
          ),
          getBlockName(1) -> RegularBlock[CfgStatement](
            fieldDecls = List.empty,
            statements = List(
              getAssignUsesStatement(1, Iterable(6,5))
            )
          ),
          getBlockName(2) -> RegularBlock[CfgStatement](
            fieldDecls = List.empty,
            statements = List(
              getAssignUsesStatement(2, Iterable(4))
            )
          )
        ),
        entry = getBlockName(0),
        exit = getBlockName(2),
        regCfg = Map(
          getBlockName(1) -> getBlockName(2)
        ),
        trueCfg = Map(
          getBlockName(0) -> getBlockName(1)
        ),
        falseCfg = Map(
          getBlockName(0) -> getBlockName(2)
        )
      ),
      globalVariables = Set(List(0,1,2).map(getVariable): _*),
      expected = Map(
        getBlockName(0) -> LivenessInfo(
          before = Set(List(1,3,4,5).map(getVariable): _*),
          after = Set(List(0,1,4,5,6).map(getVariable): _*)
        ),
        getBlockName(1) -> LivenessInfo(
          before = Set(List(0,4,5,6).map(getVariable): _*),
          after = Set(List(0,1,4).map(getVariable): _*)
        ),
        getBlockName(2) -> LivenessInfo(
          before = Set(List(0,1,4).map(getVariable): _*),
          after = Set(List(0,1,2).map(getVariable): _*)
        )
      )
    )
  }

  test("compute: should work on simple loop") {
    testCompute(
      scope = Scope[CfgBasicBlock](
        basicBlocks = Map(
          getBlockName(0) -> RegularBlock[CfgStatement](
            fieldDecls = List.empty,
            statements = List(
              getAssignUsesStatement(1, Iterable()), // not used anywhere, should not appear
              getAssignUsesStatement(2, Iterable()), // used in only conditional
              getAssignUsesStatement(3, Iterable()) // use in only loop body
            )
          ),
          getBlockName(1) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty,
            statements = List(
              getAssignUsesStatement(4, Iterable(2)), // used in exit
              getAssignUsesStatement(5, Iterable()), // not used, should not appear
              getAssignUsesStatement(6, Iterable()), // used in loop body
              getAssignUsesStatement(7, Iterable(7)) // used in conditional again,
              // should appear in both cond and body
            ),
            condition = getVariable(0)
          ),
          getBlockName(2) -> RegularBlock[CfgStatement](
            fieldDecls = List.empty,
            statements = List(
              getAssignUsesStatement(8, Iterable(3, 6)), // not used, should not appear
              getAssignUsesStatement(9, Iterable(9)) // used in loop again,
              // should appear in both cond and body
            )
          ),
          getBlockName(3) -> RegularBlock[CfgStatement](
            fieldDecls = List.empty,
            statements = List(
              getUsesStatement(Iterable(4)) // loop block should have 4 as live
            )
          )
        ),
        entry = getBlockName(0),
        exit = getBlockName(3),
        regCfg = Map(
          getBlockName(0) -> getBlockName(1),
          getBlockName(2) -> getBlockName(1)
        ),
        trueCfg = Map(
          getBlockName(1) -> getBlockName(2)
        ),
        falseCfg = Map(
          getBlockName(2) -> getBlockName(3)
        )
      ),
      globalVariables = Set(List(0).map(getVariable): _*),
      expected = Map(
        getBlockName(0) -> LivenessInfo(
          before = Set(List(0,7,9).map(getVariable): _*),
          after = Set(List(0,2,3,7,9).map(getVariable): _*)
        ),
        getBlockName(1) -> LivenessInfo(
          before = Set(List(0,2,3,7,9).map(getVariable): _*),
          after = Set(List(0,2,3,4,6,7,9).map(getVariable): _*)
        ),
        getBlockName(2) -> LivenessInfo(
          before = Set(List(0,2,3,4,6,7,9).map(getVariable): _*),
          after = Set(List(0,2,3,4,7,9).map(getVariable): _*)
        ),
        getBlockName(3) -> LivenessInfo(
          before = Set(List(0,4).map(getVariable): _*),
          after = Set(List(0).map(getVariable): _*)
        )
      )
    )
  }
}
