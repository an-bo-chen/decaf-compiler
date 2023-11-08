import TestUtils._
import decaf.compile.CfgBasicBlock
import decaf.compile.cfg._
import decaf.compile.optim.loop_optim.{LoopInvariant, LoopOptim}
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.immutable.Map

class LoopInvariantTest extends AnyFunSuite {
  // reserve 0 and 1 for global variables
  private val GLOBAL_X = getVariable(0)
  private val GLOBAL_Y = getVariable(1)
  private val GLOBALS = Set(
    GLOBAL_X, GLOBAL_Y
  )

  test("simple while") {
    val scope = Scope[CfgBasicBlock](
      basicBlocks = Map(
        getBlockName(1) -> getTestConditionalBlock(1),
        getBlockName(2) -> getTestRegularBlock(2),
        getBlockName(3) -> getTestRegularBlock(3)
      ),
      entry = getBlockName(1),
      exit = getBlockName(3),
      regCfg = Map(
        getBlockName(2) -> getBlockName(1)
      ),
      trueCfg = Map(
        getBlockName(1) -> getBlockName(2)
      ),
      falseCfg = Map(
        getBlockName(1) -> getBlockName(3)
      )
    )
    val dominators = LoopOptim.dominators(scope)
    val loop = LoopOptim.findLoops(scope, dominators)(getBlockName(1))
    val constantVariables = LoopInvariant.loopConstantVariables(scope, GLOBALS, loop)
    val result = LoopInvariant.loopInvariants(scope, (getBlockName(1), loop), constantVariables)
    assert {
      result === Map(
        getBlockName(1) -> List(
          (getTestStatement(1), false)
        ),
        getBlockName(2) -> List(
          (getTestStatement(2), false)
        )
      )
    }
  }

  test("should identify all invariant statements") {
    val scope = Scope[CfgBasicBlock](
      basicBlocks = Map(
        getBlockName(1) -> ConditionalBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            getAssignUsesStatement_NoFunction(4, Iterable(5))
          ),
          condition = getExpressionUses(Iterable(2,3))
        ),
        getBlockName(2) -> RegularBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            getAssignUsesStatement_NoFunction(2, Iterable(3,0)),
            getAssignUsesStatement_NoFunction(5, Iterable(2,1))
          )
        ),
        getBlockName(3) -> getTestRegularBlock(3)
      ),
      entry = getBlockName(1),
      exit = getBlockName(3),
      regCfg = Map(
        getBlockName(2) -> getBlockName(1)
      ),
      trueCfg = Map(
        getBlockName(1) -> getBlockName(2)
      ),
      falseCfg = Map(
        getBlockName(1) -> getBlockName(3)
      )
    )
    val dominators = LoopOptim.dominators(scope)
    val loop = LoopOptim.findLoops(scope, dominators)(getBlockName(1))
    val constantVariables = LoopInvariant.loopConstantVariables(scope, GLOBALS, loop)
    val result = LoopInvariant.loopInvariants(scope, (getBlockName(1), loop), constantVariables)
    assert {
      result === Map(
        getBlockName(1) -> List(
          (getAssignUsesStatement_NoFunction(4, Iterable(5)), true)
        ),
        getBlockName(2) -> List(
          (getAssignUsesStatement_NoFunction(2, Iterable(3,0)), true),
          (getAssignUsesStatement_NoFunction(5, Iterable(2,1)), true)
        )
      )
    }
  }

  test("function calls/ array assigns are not invariant") {
    val scope = Scope[CfgBasicBlock](
      basicBlocks = Map(
        getBlockName(1) -> ConditionalBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List.empty,
          condition = getExpressionUses(Iterable(2,3))
        ),
        getBlockName(2) -> RegularBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            getAssignUsesStatement_NoFunction(2, Iterable(3)),
            getAssignUsesStatement_NoFunction(4, Iterable(5)),
            getAssignUsesStatement(5, Iterable(2,1)),
            getAssignUsesIndex_NoFunction(6, CfgImmediate(0), Iterable())
          )
        ),
        getBlockName(3) -> getTestRegularBlock(3)
      ),
      entry = getBlockName(1),
      exit = getBlockName(3),
      regCfg = Map(
        getBlockName(2) -> getBlockName(1)
      ),
      trueCfg = Map(
        getBlockName(1) -> getBlockName(2)
      ),
      falseCfg = Map(
        getBlockName(1) -> getBlockName(3)
      )
    )
    val dominators = LoopOptim.dominators(scope)
    val loop = LoopOptim.findLoops(scope, dominators)(getBlockName(1))
    val constantVariables = LoopInvariant.loopConstantVariables(scope, GLOBALS, loop)
    val result = LoopInvariant.loopInvariants(scope, (getBlockName(1), loop), constantVariables)
    assert {
      result === Map(
        getBlockName(1) -> List.empty,
        getBlockName(2) -> List(
          (getAssignUsesStatement_NoFunction(2, Iterable(3)), true),
          (getAssignUsesStatement_NoFunction(4, Iterable(5)), false),
          (getAssignUsesStatement(5, Iterable(2,1)), false),
          (getAssignUsesIndex_NoFunction(6, CfgImmediate(0), Iterable()), false)
        )
      )
    }
  }

  test("mix with other statements") {
    val scope = Scope[CfgBasicBlock](
      basicBlocks = Map(
        getBlockName(1) -> ConditionalBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List.empty,
          condition = getExpressionUses(Iterable(2,3))
        ),
        getBlockName(2) -> RegularBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            CfgFunctionCallStatement("hello", Seq(3,4)
              .map(getVariable).map(variable => CfgFunctionCallValueParam(variable)).toList),
            CfgArrayAssignStatement(CfgLocation(getVariable(7), None), List(4L,5L,6L)),
            getAssignUsesStatement_NoFunction(5, Iterable(2,2)),
            CfgReturnStatement(Some(getVariable(0)))
          )
        ),
        getBlockName(3) -> getTestRegularBlock(3)
      ),
      entry = getBlockName(1),
      exit = getBlockName(3),
      regCfg = Map(
        getBlockName(2) -> getBlockName(1)
      ),
      trueCfg = Map(
        getBlockName(1) -> getBlockName(2)
      ),
      falseCfg = Map(
        getBlockName(1) -> getBlockName(3)
      )
    )
    val dominators = LoopOptim.dominators(scope)
    val loop = LoopOptim.findLoops(scope, dominators)(getBlockName(1))
    val constantVariables = LoopInvariant.loopConstantVariables(scope, GLOBALS, loop)
    val result = LoopInvariant.loopInvariants(scope, (getBlockName(1), loop), constantVariables)
    assert {
      result === Map(
        getBlockName(1) -> List.empty,
        getBlockName(2) -> List(
          (CfgFunctionCallStatement("hello", Seq(3,4)
            .map(getVariable).map(variable => CfgFunctionCallValueParam(variable)).toList), false),
          (CfgArrayAssignStatement(CfgLocation(getVariable(7), None), List(4L,5L,6L)), false),
          (getAssignUsesStatement_NoFunction(5, Iterable(2,2)), true),
          (CfgReturnStatement(Some(getVariable(0))), false)
        )
      )
    }
  }

  test("assign that uses assign variable is not invariant") {
    val scope = Scope[CfgBasicBlock](
      basicBlocks = Map(
        getBlockName(1) -> ConditionalBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List.empty,
          condition = getExpressionUses(Iterable(2,3))
        ),
        getBlockName(2) -> RegularBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            getAssignUsesStatement_NoFunction(3, Iterable(0,3))
          )
        ),
        getBlockName(3) -> getTestRegularBlock(3)
      ),
      entry = getBlockName(1),
      exit = getBlockName(3),
      regCfg = Map(
        getBlockName(2) -> getBlockName(1)
      ),
      trueCfg = Map(
        getBlockName(1) -> getBlockName(2)
      ),
      falseCfg = Map(
        getBlockName(1) -> getBlockName(3)
      )
    )
    val dominators = LoopOptim.dominators(scope)
    val loop = LoopOptim.findLoops(scope, dominators)(getBlockName(1))
    val constantVariables = LoopInvariant.loopConstantVariables(scope, GLOBALS, loop)
    val result = LoopInvariant.loopInvariants(scope, (getBlockName(1), loop), constantVariables)
    assert {
      result === Map(
        getBlockName(1) -> List.empty,
        getBlockName(2) -> List(
          (getAssignUsesStatement_NoFunction(3, Iterable(0,3)), false)
        )
      )
    }
  }

  test("multiple assign to same variable is not invariant") {
    val scope = Scope[CfgBasicBlock](
      basicBlocks = Map(
        getBlockName(1) -> ConditionalBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List.empty,
          condition = getExpressionUses(Iterable(2,3))
        ),
        getBlockName(2) -> ConditionalBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            getAssignUsesStatement_NoFunction(1, Iterable(0,1)),
            getAssignUsesStatement_NoFunction(0, Iterable(3))
          ),
          condition = getExpressionUses(Iterable(0,1))
        ),
        getBlockName(3) -> RegularBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            getAssignUsesStatement_NoFunction(3, Iterable())
          )
        ),
        getBlockName(4) -> RegularBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            getAssignUsesStatement_NoFunction(3, Iterable())
          )
        ),
        getBlockName(5) -> getTestRegularBlock(5)
      ),
      entry = getBlockName(1),
      exit = getBlockName(3),
      regCfg = Map(
        getBlockName(3) -> getBlockName(1),
        getBlockName(4) -> getBlockName(1)
      ),
      trueCfg = Map(
        getBlockName(1) -> getBlockName(2),
        getBlockName(2) -> getBlockName(3)
      ),
      falseCfg = Map(
        getBlockName(1) -> getBlockName(5),
        getBlockName(2) -> getBlockName(4)
      )
    )
    val dominators = LoopOptim.dominators(scope)
    val loop = LoopOptim.findLoops(scope, dominators)(getBlockName(1))
    val constantVariables = LoopInvariant.loopConstantVariables(scope, GLOBALS, loop)
    val result = LoopInvariant.loopInvariants(scope, (getBlockName(1), loop), constantVariables)
    assert {
      result === Map(
        getBlockName(1) -> List.empty,
        getBlockName(2) -> List(
          (getAssignUsesStatement_NoFunction(1, Iterable(0,1)), false),
          (getAssignUsesStatement_NoFunction(0, Iterable(3)), false)
        ),
        getBlockName(3) -> List(
          (getAssignUsesStatement_NoFunction(3, Iterable()), false)
        ),
        getBlockName(4) -> List(
          (getAssignUsesStatement_NoFunction(3, Iterable()), false)
        )
      )
    }
  }

  test("multiple rounds of invariant marking") {
    val scope = Scope[CfgBasicBlock](
      basicBlocks = Map(
        getBlockName(1) -> ConditionalBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List.empty,
          condition = getExpressionUses(Iterable(2,3))
        ),
        getBlockName(2) -> RegularBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            getAssignUsesStatement_NoFunction(0, Iterable()),
            getAssignUsesStatement_NoFunction(1, Iterable(0)),
            getAssignUsesStatement_NoFunction(2, Iterable(1))
          )
        ),
        getBlockName(3) -> getTestRegularBlock(3)
      ),
      entry = getBlockName(1),
      exit = getBlockName(3),
      regCfg = Map(
        getBlockName(2) -> getBlockName(1)
      ),
      trueCfg = Map(
        getBlockName(1) -> getBlockName(2)
      ),
      falseCfg = Map(
        getBlockName(1) -> getBlockName(3)
      )
    )
    val dominators = LoopOptim.dominators(scope)
    val loop = LoopOptim.findLoops(scope, dominators)(getBlockName(1))
    val constantVariables = LoopInvariant.loopConstantVariables(scope, GLOBALS, loop)
    val result = LoopInvariant.loopInvariants(scope, (getBlockName(1), loop), constantVariables)
    assert {
      result === Map(
        getBlockName(1) -> List.empty,
        getBlockName(2) -> List(
          (getAssignUsesStatement_NoFunction(0, Iterable()), true),
          (getAssignUsesStatement_NoFunction(1, Iterable(0)), true),
          (getAssignUsesStatement_NoFunction(2, Iterable(1)), true)
        )
      )
    }
  }

  test("function call kills globals") {
    val scope = Scope[CfgBasicBlock](
      basicBlocks = Map(
        getBlockName(1) -> ConditionalBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List.empty,
          condition = getExpressionUses(Iterable(2,3))
        ),
        getBlockName(2) -> RegularBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            getAssignUsesStatement_NoFunction(0, Iterable()),
            getAssignUsesStatement_NoFunction(2, Iterable(1)),
            getAssignUsesStatement(3, Iterable(4,5)),
            getAssignUsesStatement_NoFunction(4, Iterable())
          )
        ),
        getBlockName(3) -> getTestRegularBlock(3)
      ),
      entry = getBlockName(1),
      exit = getBlockName(3),
      regCfg = Map(
        getBlockName(2) -> getBlockName(1)
      ),
      trueCfg = Map(
        getBlockName(1) -> getBlockName(2)
      ),
      falseCfg = Map(
        getBlockName(1) -> getBlockName(3)
      )
    )
    val dominators = LoopOptim.dominators(scope)
    val loop = LoopOptim.findLoops(scope, dominators)(getBlockName(1))
    val constantVariables = LoopInvariant.loopConstantVariables(scope, GLOBALS, loop)
    val result = LoopInvariant.loopInvariants(scope, (getBlockName(1), loop), constantVariables)
    assert {
      result === Map(
        getBlockName(1) -> List.empty,
        getBlockName(2) -> List(
          (getAssignUsesStatement_NoFunction(0, Iterable()), false),
          (getAssignUsesStatement_NoFunction(2, Iterable(1)), false),
          (getAssignUsesStatement(3, Iterable(4,5)), false),
          (getAssignUsesStatement_NoFunction(4, Iterable()), true)
        )
      )
    }
  }
}
