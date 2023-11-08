import TestUtils._
import decaf.compile.CfgBasicBlock
import decaf.compile.cfg._
import decaf.compile.optim.loop_optim.LoopOptim
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.immutable.Map

class DominatorTest extends AnyFunSuite {

  test("string of blocks") {
    val result = LoopOptim.dominators(Scope[CfgBasicBlock](
      basicBlocks = Map(
        getBlockName(1) -> getTestRegularBlock(1),
        getBlockName(2) -> getTestRegularBlock(2),
        getBlockName(3) -> getTestRegularBlock(3)
      ),
      entry = getBlockName(1),
      exit = getBlockName(3),
      regCfg = Map(
        getBlockName(1) -> getBlockName(2),
        getBlockName(2) -> getBlockName(3)
      ),
      trueCfg = Map.empty,
      falseCfg = Map.empty
    ))
    assert {
      result === Map(
        getBlockName(1) -> Set(getBlockName(1)),
        getBlockName(2) -> Seq(1,2).map(getBlockName).toSet,
        getBlockName(3) -> Seq(1,2,3).map(getBlockName).toSet
      )
    }
  }

  test("if else") {
    val result = LoopOptim.dominators(Scope[CfgBasicBlock](
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
    ))
    assert {
      result === Map(
        getBlockName(1) -> Set(getBlockName(1)),
        getBlockName(2) -> Seq(1,2).map(getBlockName).toSet,
        getBlockName(3) -> Seq(1,3).map(getBlockName).toSet,
        getBlockName(4) -> Seq(1,4).map(getBlockName).toSet
      )
    }
  }

  test("simple while") {
    val result = LoopOptim.dominators(Scope[CfgBasicBlock](
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
    ))
    assert {
      result === Map(
        getBlockName(1) -> Set(getBlockName(1)),
        getBlockName(2) -> Seq(1,2).map(getBlockName).toSet,
        getBlockName(3) -> Seq(1,3).map(getBlockName).toSet
      )
    }
  }

  test("nested while") {
    val result = LoopOptim.dominators(Scope[CfgBasicBlock](
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
    ))
    assert {
      result === Map(
        getBlockName(1) -> Set(getBlockName(1)),
        getBlockName(2) -> Seq(1,2).map(getBlockName).toSet,
        getBlockName(3) -> Seq(1,2,3).map(getBlockName).toSet,
        getBlockName(4) -> Seq(1,4).map(getBlockName).toSet
      )
    }
  }

  test("complex while") {
    val result = LoopOptim.dominators(Scope[CfgBasicBlock](
      basicBlocks = Map(
        getBlockName(1) -> getTestConditionalBlock(1),
        getBlockName(2) -> getTestConditionalBlock(2),
        getBlockName(3) -> getTestConditionalBlock(3),
        getBlockName(4) -> getTestRegularBlock(4),
        getBlockName(5) -> getTestRegularBlock(5)
      ),
      entry = getBlockName(1),
      exit = getBlockName(5),
      regCfg = Map(
        getBlockName(4) -> getBlockName(1)
      ),
      trueCfg = Map(
        getBlockName(1) -> getBlockName(2),
        getBlockName(2) -> getBlockName(1), // continue
        getBlockName(3) -> getBlockName(5) // break
      ),
      falseCfg = Map(
        getBlockName(1) -> getBlockName(5),
        getBlockName(2) -> getBlockName(3),
        getBlockName(3) -> getBlockName(4)
      )
    ))
    assert {
      result === Map(
        getBlockName(1) -> Set(getBlockName(1)),
        getBlockName(2) -> Seq(1,2).map(getBlockName).toSet,
        getBlockName(3) -> Seq(1,2,3).map(getBlockName).toSet,
        getBlockName(4) -> Seq(1,2,3,4).map(getBlockName).toSet,
        getBlockName(5) -> Seq(1,5).map(getBlockName).toSet
      )
    }
  }
}
