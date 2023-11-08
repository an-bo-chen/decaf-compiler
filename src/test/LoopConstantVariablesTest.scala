import TestUtils._
import decaf.compile.CfgBasicBlock
import decaf.compile.cfg._
import decaf.compile.optim.loop_optim.{LoopInvariant, LoopOptim}
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.immutable.Map

class LoopConstantVariablesTest extends AnyFunSuite {
  // reserve 0 and 1 for global variables
  private val GLOBAL_X = getVariable(0)
  private val GLOBAL_Y = getVariable(1)
  private val GLOBALS = Set(
    GLOBAL_X, GLOBAL_Y
  )

  test("should identify all constant variables, including arrays") {
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
            getAssignUsesStatement_NoFunction(2, Iterable(3,0)),
            getAssignUsesIndex_NoFunction(0, CfgImmediate(0), Iterable(2, 0)),
            CfgRegAssignStatement(CfgLocation(getVariable(4), None), CfgArrayReadExpr(getVariable(5), getVariable(6)))
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
    val result = LoopInvariant.loopConstantVariables(scope, GLOBALS, loop)
    assert {
      result === (Seq(3, 5, 6).map(getVariable).toSet, Set.empty[CfgVariable])
    }
  }

  test("function calls should destroy global variables, multiple blocks in loop") {
    val scope = Scope[CfgBasicBlock](
      basicBlocks = Map(
        getBlockName(1) -> ConditionalBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List.empty,
          condition = getExpressionUses(Iterable(0,3))
        ),
        getBlockName(2) -> ConditionalBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            getAssignUsesStatement_NoFunction(4, Iterable())
          ),
          condition = getExpressionUses(Iterable(2))
        ),
        getBlockName(3) -> RegularBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            getAssignUsesStatement(4, Iterable(5))
          )
        ),
        getBlockName(4) -> getTestRegularBlock(4)
      ),
      entry = getBlockName(1),
      exit = getBlockName(4),
      regCfg = Map(
        getBlockName(3) -> getBlockName(1)
      ),
      trueCfg = Map(
        getBlockName(1) -> getBlockName(2),
        getBlockName(2) -> getBlockName(3)
      ),
      falseCfg = Map(
        getBlockName(1) -> getBlockName(4),
        getBlockName(2) -> getBlockName(1)
      )
    )
    val dominators = LoopOptim.dominators(scope)
    val loop = LoopOptim.findLoops(scope, dominators)(getBlockName(1))
    val result = LoopInvariant.loopConstantVariables(scope, GLOBALS, loop)
    assert {
      result === (Seq(2,3,5).map(getVariable).toSet, Seq(0, 1).map(getVariable).toSet)
    }
  }
}
