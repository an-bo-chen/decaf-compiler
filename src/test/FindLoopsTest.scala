import TestUtils._
import decaf.compile.CfgBasicBlock
import decaf.compile.cfg._
import decaf.compile.optim.loop_optim.LoopOptim
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.immutable.Map

class FindLoopsTest extends AnyFunSuite {

  test("no back edges") {
    val scope = Scope[CfgBasicBlock](
      basicBlocks = Map(
        getBlockName(1) -> getTestConditionalBlock(1),
        getBlockName(2) -> getTestRegularBlock(2),
        getBlockName(3) -> getTestRegularBlock(3),
        getBlockName(4) -> getTestRegularBlock(4)
      ),
      entry = getBlockName(1),
      exit = getBlockName(4),
      regCfg = Map(
        getBlockName(3) -> getBlockName(4),
        getBlockName(2) -> getBlockName(4)
      ),
      trueCfg = Map(
        getBlockName(1) -> getBlockName(2)
      ),
      falseCfg = Map(
        getBlockName(1) -> getBlockName(3)
      )
    )
    val dominators = LoopOptim.dominators(scope)
    val result = LoopOptim.findLoops(scope, dominators)
    assert {
      result === Map.empty[String, Set[String]]
    }
  }

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
    val result = LoopOptim.findLoops(scope, dominators)
    assert {
      result === Map(
        getBlockName(1) -> Seq(1,2).map(getBlockName).toSet
      )
    }
  }

  test("empty while") {
    val scope = Scope[CfgBasicBlock](
      basicBlocks = Map(
        getBlockName(1) -> getTestConditionalBlock(1),
        getBlockName(3) -> getTestRegularBlock(3)
      ),
      entry = getBlockName(1),
      exit = getBlockName(3),
      regCfg = Map(
      ),
      trueCfg = Map(
        getBlockName(1) -> getBlockName(1)
      ),
      falseCfg = Map(
        getBlockName(1) -> getBlockName(3)
      )
    )
    val dominators = LoopOptim.dominators(scope)
    val result = LoopOptim.findLoops(scope, dominators)
    assert {
      result === Map(
        getBlockName(1) -> Seq(1).map(getBlockName).toSet
      )
    }
  }

  test("nested while") {
    val scope = Scope[CfgBasicBlock](
      basicBlocks = Map(
        getBlockName(1) -> getTestConditionalBlock(1),
        getBlockName(2) -> getTestConditionalBlock(2),
        getBlockName(3) -> getTestRegularBlock(3),
        getBlockName(4) -> getTestRegularBlock(4)
      ),
      entry = getBlockName(1),
      exit = getBlockName(4),
      regCfg = Map(
        getBlockName(3) -> getBlockName(2)
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
    val result = LoopOptim.findLoops(scope, dominators)
    assert {
      result === Map(
        getBlockName(1) -> Seq(1,2,3).map(getBlockName).toSet,
        getBlockName(2) -> Seq(2,3).map(getBlockName).toSet
      )
    }
  }

  test("loop with multiple back edges") {
    val scope = Scope[CfgBasicBlock](
      basicBlocks = Map(
        getBlockName(1) -> getTestConditionalBlock(1),
        getBlockName(2) -> getTestConditionalBlock(2),
        getBlockName(3) -> getTestConditionalBlock(3),
        getBlockName(4) -> getTestRegularBlock(4),
        getBlockName(5) -> getTestRegularBlock(5),
        getBlockName(6) -> getTestRegularBlock(6),
        getBlockName(7) -> getTestRegularBlock(7)
      ),
      entry = getBlockName(1),
      exit = getBlockName(5),
      regCfg = Map(
        getBlockName(4) -> getBlockName(1),
        getBlockName(6) -> getBlockName(1), // continue
        getBlockName(7) -> getBlockName(5) //break
      ),
      trueCfg = Map(
        getBlockName(1) -> getBlockName(2),
        getBlockName(2) -> getBlockName(6), // continue
        getBlockName(3) -> getBlockName(7) // break
      ),
      falseCfg = Map(
        getBlockName(1) -> getBlockName(5),
        getBlockName(2) -> getBlockName(3),
        getBlockName(3) -> getBlockName(4)
      )
    )
    val dominators = LoopOptim.dominators(scope)
    val result = LoopOptim.findLoops(scope, dominators)
    assert {
      result === Map(
        // includes continue but not break
        getBlockName(1) -> Seq(1,2,3,4,6).map(getBlockName).toSet
      )
    }
  }

  test("complex example") {
    val scope = Scope[CfgBasicBlock](
      basicBlocks = Map(
        getBlockName(1) -> getTestRegularBlock(1), // entry
        getBlockName(2) -> getTestConditionalBlock(2), // loop 1 (empty)
        getBlockName(3) -> getTestConditionalBlock(3), // if after loop 1
        getBlockName(4) -> getTestRegularBlock(4), // then block
        getBlockName(5) -> getTestConditionalBlock(5), // loop 2
        getBlockName(6) -> getTestRegularBlock(6), // loop body
        getBlockName(7) -> getTestConditionalBlock(7), // nested loop
        getBlockName(8) -> getTestConditionalBlock(8), // if in nested loop
        getBlockName(9) -> getTestRegularBlock(9), // break nested loop
        getBlockName(10) -> getTestRegularBlock(10), // continue nested
        getBlockName(11) -> getTestRegularBlock(11) // exit
      ),
      entry = getBlockName(1),
      exit = getBlockName(11),
      regCfg = Map(
        getBlockName(1) -> getBlockName(2),
        getBlockName(4) -> getBlockName(5),
        getBlockName(6) -> getBlockName(7),
        getBlockName(9) -> getBlockName(5),
        getBlockName(10) -> getBlockName(7)
      ),
      trueCfg = Map(
        getBlockName(2) -> getBlockName(3),
        getBlockName(3) -> getBlockName(5),
        getBlockName(5) -> getBlockName(11),
        getBlockName(7) -> getBlockName(8),
        getBlockName(8) -> getBlockName(10)
      ),
      falseCfg = Map(
        getBlockName(2) -> getBlockName(2),
        getBlockName(3) -> getBlockName(4),
        getBlockName(5) -> getBlockName(6),
        getBlockName(7) -> getBlockName(5),
        getBlockName(8) -> getBlockName(9)
      )
    )
    val dominators = LoopOptim.dominators(scope)
    val result = LoopOptim.findLoops(scope, dominators)
    assert {
      result === Map(
        getBlockName(2) -> Seq(2).map(getBlockName).toSet,
        getBlockName(5) -> Seq(5,6,7,8,9,10).map(getBlockName).toSet,
        getBlockName(7) -> Seq(7,8,10).map(getBlockName).toSet
      )
    }
  }
}
