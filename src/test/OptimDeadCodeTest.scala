import org.scalatest.funsuite.AnyFunSuite
import TestUtils._
import decaf.compile.cfg._
import decaf.compile.optim.dead_code_elimination._
import org.scalatest.Assertion
import decaf.compile._

class OptimDeadCodeTest extends AnyFunSuite {
  test("optimizeBlock: should not modify optimized block") {
    val testBlock = RegularBlock[CfgStatement](
      fieldDecls = List.empty,
      statements = List(
        getAssignUsesStatement_NoFunction(1, Iterable()),
        getAssignUsesStatement_NoFunction(0, Iterable(1,2)),
        getUsesStatement(Iterable(0,1)),
        getAssignUsesStatement_NoFunction(0, Iterable(1,3)),
        getAssignUsesStatement_NoFunction(3, Iterable(0))
      )
    )
    assert(OptimDeadCode.optimizeBlock(testBlock, Set(Iterable(3).map(getVariable).toSeq: _*)) === (testBlock, false))
  }

  test("optimizeBlock: should optimize immediate dead code") {
    assert(OptimDeadCode.optimizeBlock(
      RegularBlock[CfgStatement](
        fieldDecls = List.empty,
        statements = List(
          getAssignUsesStatement_NoFunction(1, Iterable(2,3)),
          getAssignUsesStatement_NoFunction(2, Iterable(3,4))
        )
      ),
      Set.empty
    ) ===
      (RegularBlock[CfgStatement](
        fieldDecls = List.empty,
        statements = List.empty
      ), true)
    )
  }

  test("optimizeBlock: should optimize dead code before last assign") {
    assert(OptimDeadCode.optimizeBlock(
      RegularBlock[CfgStatement](
        fieldDecls = List.empty,
        statements = List(
          getUsesStatement(Iterable(0,1)),
          getAssignUsesStatement_NoFunction(0, Iterable(2)),
          getAssignUsesStatement_NoFunction(0, Iterable()),
          getAssignUsesStatement_NoFunction(2, Iterable(0))
        )
      ),
      Set(Iterable(0).map(getVariable).toSeq: _*)
    ) ===
      (RegularBlock[CfgStatement](
        fieldDecls = List.empty,
        statements = List(
          getUsesStatement(Iterable(0,1)),
          getAssignUsesStatement_NoFunction(0, Iterable())
        )
      ), true)
    )
  }

  test("optimizeBlock: should optimize dead code layers") {
    assert(OptimDeadCode.optimizeBlock(
      RegularBlock[CfgStatement](
        fieldDecls = List.empty,
        statements = List(
          getAssignUsesStatement_NoFunction(1, Iterable(0)),
          getAssignUsesStatement_NoFunction(2, Iterable(1)),
          getAssignUsesStatement_NoFunction(3, Iterable(2))
        )
      ),
      Set(Iterable(1).map(getVariable).toSeq: _*)
    ) ===
      (RegularBlock[CfgStatement](
        fieldDecls = List.empty,
        statements = List(
          getAssignUsesStatement_NoFunction(1, Iterable(0))
        )
      ), true)
    )
  }

  test("optimizeBlock: should turn dead assignments of function expression into function call") {
    assert(OptimDeadCode.optimizeBlock(
      RegularBlock[CfgStatement](
        fieldDecls = List.empty,
        statements = List(
          getAssignUsesStatement(1, Iterable(0)), // not dead
          getAssignUsesStatement(2, Iterable(1))  // dead
        )
      ),
      Iterable(1).map(getVariable).toSet
    ) ===
      (RegularBlock[CfgStatement](
        fieldDecls = List.empty,
        statements = List(
          getAssignUsesStatement(1, Iterable(0)),
          CfgFunctionCallStatement("", Iterable(1).map(num => CfgFunctionCallValueParam(getVariable(num))).toList)
        )
      ), true)
    )
  }

  private def testOptimizeScope(
    testScope: CfgScope,
    globalVariables: Set[CfgVariable],
    optimizedBlocks: Map[String, CfgBasicBlock]
  ): Assertion = {
    // expect everything else to be the same
    val expectedScope = testScope.copy(basicBlocks = optimizedBlocks)
    assert(
      OptimDeadCode.optimizeScope(testScope, globalVariables) === expectedScope
    )
  }

  test("optimizeScope: should preserve live code across blocks") {
    testOptimizeScope(
      testScope = Scope[CfgBasicBlock](
        basicBlocks = Map(
          getBlockName(0) -> RegularBlock[CfgStatement](
            fieldDecls = List.empty,
            statements = List(
              getAssignUsesStatement_NoFunction(0, Iterable(1))
            )
          ),
          getBlockName(1) -> RegularBlock[CfgStatement](
            fieldDecls = List.empty,
            statements = List(
              getUsesStatement(Iterable(0))
            )
          )
        ),
        entry = getBlockName(0),
        exit = getBlockName(1),
        regCfg = Map(getBlockName(0) -> getBlockName(1)),
        trueCfg = Map.empty,
        falseCfg = Map.empty
      ),
      globalVariables = Set.empty,
      optimizedBlocks = Map(
        getBlockName(0) -> RegularBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            getAssignUsesStatement_NoFunction(0, Iterable(1))
          )
        ),
        getBlockName(1) -> RegularBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            getUsesStatement(Iterable(0))
          )
        )
      )
    )
  }

  test("optimizeScope: should eliminate dead code across blocks") {
    testOptimizeScope(
      testScope = Scope[CfgBasicBlock](
        basicBlocks = Map(
          getBlockName(0) -> RegularBlock[CfgStatement](
            fieldDecls = List.empty,
            statements = List(
              getAssignUsesStatement_NoFunction(0, Iterable(1))
            )
          ),
          getBlockName(1) -> RegularBlock[CfgStatement](
            fieldDecls = List.empty,
            statements = List(
              getAssignUsesStatement_NoFunction(1, Iterable(0))
            )
          )
        ),
        entry = getBlockName(0),
        exit = getBlockName(1),
        regCfg = Map(getBlockName(0) -> getBlockName(1)),
        trueCfg = Map.empty,
        falseCfg = Map.empty
      ),
      globalVariables = Set.empty,
      optimizedBlocks = Map(
        getBlockName(0) -> RegularBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List()
        ),
        getBlockName(1) -> RegularBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List()
        )
      )
    )
  }

  test("optimizeScope: should keep code used in condition") {
    testOptimizeScope(
      testScope = Scope[CfgBasicBlock](
        basicBlocks = Map(
          getBlockName(0) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty,
            statements = List(
              getAssignUsesStatement_NoFunction(0, Iterable())
            ),
            condition = getVariable(0)
          )
        ),
        entry = getBlockName(0),
        exit = getBlockName(0),
        regCfg = Map.empty,
        trueCfg = Map.empty,
        falseCfg = Map.empty
      ),
      globalVariables = Set.empty,
      optimizedBlocks = Map(
        getBlockName(0) -> ConditionalBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            getAssignUsesStatement_NoFunction(0, Iterable())
          ),
          condition = getVariable(0)
        )
      )
    )
  }
}
