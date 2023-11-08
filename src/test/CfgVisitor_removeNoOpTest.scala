import TestUtils._
import decaf.compile.CfgBasicBlock
import decaf.compile.cfg._
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.immutable.TreeMap

class CfgVisitor_removeNoOpTest extends AnyFunSuite {

  test("should not change one regular block") {
    val result = CfgVisitorUtil.removeNoOp(Scope[CfgBasicBlock](
      basicBlocks = TreeMap(getBlockName(1) -> getTestRegularBlock(1)),
      entry = getBlockName(1),
      exit = getBlockName(1),
      regCfg = TreeMap.empty,
      trueCfg = TreeMap.empty,
      falseCfg = TreeMap.empty
    ))
    assert {
      result === Scope[CfgBasicBlock](
        basicBlocks = TreeMap(getBlockName(1) -> getTestRegularBlock(1)),
        entry = getBlockName(1),
        exit = getBlockName(1),
        regCfg = TreeMap.empty,
        trueCfg = TreeMap.empty,
        falseCfg = TreeMap.empty
      )
    }
  }

  test("should not change if branch") {
    val result = CfgVisitorUtil.removeNoOp(Scope[CfgBasicBlock](
      basicBlocks = TreeMap(
        getBlockName(1) -> getTestConditionalBlock(1),
        getBlockName(2) -> getTestRegularBlock(2),
        getBlockName(3) -> getTestRegularBlock(3)
      ),
      entry = getBlockName(1),
      exit = getBlockName(3),
      regCfg = TreeMap(
        getBlockName(2) -> getBlockName(3)
      ),
      trueCfg = TreeMap(
        getBlockName(1) -> getBlockName(2)
      ),
      falseCfg = TreeMap(
        getBlockName(1) -> getBlockName(3)
      )
    ))
    assert {
      result === Scope[CfgBasicBlock](
        basicBlocks = TreeMap(
          getBlockName(1) -> getTestConditionalBlock(1),
          getBlockName(2) -> getTestRegularBlock(2),
          getBlockName(3) -> getTestRegularBlock(3)
        ),
        entry = getBlockName(1),
        exit = getBlockName(3),
        regCfg = TreeMap(
          getBlockName(2) -> getBlockName(3)
        ),
        trueCfg = TreeMap(
          getBlockName(1) -> getBlockName(2)
        ),
        falseCfg = TreeMap(
          getBlockName(1) -> getBlockName(3)
        )
      )
    }
  }

  test("should not change for conditional with a nested conditional in true/false branch") {
    val test = Scope[CfgBasicBlock](
      basicBlocks = TreeMap(
        getBlockName(1) -> getTestConditionalBlock(1), // condition 1
        getBlockName(2) -> getTestConditionalBlock(2), // conditional 2 in true branch
        getBlockName(3) -> getTestRegularBlock(3), // true branch for conditional 2
        getBlockName(4) -> getTestRegularBlock(4), // false branch for conditional 2
        getBlockName(5) -> getTestConditionalBlock(5), // conditional 3 in false branch
        getBlockName(6) -> getTestRegularBlock(6), // true branch for conditional 3
        getBlockName(7) -> getTestRegularBlock(7), // false branch for conditional 3
        getBlockName(8) -> getTestRegularBlock(8) // exit
      ),
      entry = getBlockName(1),
      exit = getBlockName(8),
      regCfg = TreeMap(
        getBlockName(3) -> getBlockName(8),
        getBlockName(4) -> getBlockName(8),
        getBlockName(6) -> getBlockName(8),
        getBlockName(7) -> getBlockName(8)
      ),
      trueCfg = TreeMap(
        getBlockName(1) -> getBlockName(2),
        getBlockName(2) -> getBlockName(3),
        getBlockName(5) -> getBlockName(6)
      ),
      falseCfg = TreeMap(
        getBlockName(1) -> getBlockName(5),
        getBlockName(2) -> getBlockName(4),
        getBlockName(5) -> getBlockName(7)
      )
    )
    val result = CfgVisitorUtil.removeNoOp(test)
    assert {
      result === test
    }
  }

  test("should not change for simple loop") {

    val result = CfgVisitorUtil.removeNoOp(Scope[CfgBasicBlock](
      basicBlocks = TreeMap(
        getBlockName(1) -> getTestConditionalBlock(1), // loop conditional
        getBlockName(2) -> getTestRegularBlock(2), // loop body
        getBlockName(3) -> getTestRegularBlock(3) // exit
      ),
      entry = getBlockName(1),
      exit = getBlockName(3),
      regCfg = TreeMap(
        getBlockName(2) -> getBlockName(1)
      ),
      trueCfg = TreeMap(
        getBlockName(1) -> getBlockName(2)
      ),
      falseCfg = TreeMap(
        getBlockName(1) -> getBlockName(3)
      )
    ))
    assert {
      result === Scope[CfgBasicBlock](
        basicBlocks = TreeMap(
          getBlockName(1) -> getTestConditionalBlock(1), // loop conditional
          getBlockName(2) -> getTestRegularBlock(2), // loop body
          getBlockName(3) -> getTestRegularBlock(3) // exit
        ),
        entry = getBlockName(1),
        exit = getBlockName(3),
        regCfg = TreeMap(
          getBlockName(2) -> getBlockName(1)
        ),
        trueCfg = TreeMap(
          getBlockName(1) -> getBlockName(2)
        ),
        falseCfg = TreeMap(
          getBlockName(1) -> getBlockName(3)
        )
      )
    }
  }

  private val noOpBlock = RegularBlock[CfgStatement](List.empty, List.empty) // aliases are fine b/c immutable

  test("should collapse if else branch with empty, should not change exit") {
    val result = CfgVisitorUtil.removeNoOp(Scope[CfgBasicBlock](
      basicBlocks = TreeMap(
        getBlockName(1) -> getTestConditionalBlock(1),
        getBlockName(2) -> noOpBlock,
        getBlockName(3) -> noOpBlock,
        getBlockName(4) -> noOpBlock
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
    ))
    assert {
      result === Scope[CfgBasicBlock](
        basicBlocks = TreeMap(
          getBlockName(1) -> getTestConditionalBlock(1),
          getBlockName(4) -> noOpBlock
        ),
        entry = getBlockName(1),
        exit = getBlockName(4),
        regCfg = TreeMap(
        ),
        trueCfg = TreeMap(
          getBlockName(1) -> getBlockName(4)
        ),
        falseCfg = TreeMap(
          getBlockName(1) -> getBlockName(4)
        )
      )
    }
  }

  test("should collapse nested if else with empty, multiple noOp chain") {
    val result = CfgVisitorUtil.removeNoOp(Scope[CfgBasicBlock](
      basicBlocks = TreeMap(
        getBlockName(1) -> getTestConditionalBlock(1), // start branch
        getBlockName(2) -> getTestConditionalBlock(2), // 1 true branch
        getBlockName(3) -> noOpBlock, // 2 true
        getBlockName(4) -> noOpBlock, // 2 false
        getBlockName(5) -> noOpBlock, // 3,4 merge
        getBlockName(6) -> noOpBlock // exit
      ),
      entry = getBlockName(1),
      exit = getBlockName(6),
      regCfg = TreeMap(
        getBlockName(3) -> getBlockName(5),
        getBlockName(4) -> getBlockName(5),
        getBlockName(5) -> getBlockName(6)
      ),
      trueCfg = TreeMap(
        getBlockName(1) -> getBlockName(2),
        getBlockName(2) -> getBlockName(3)
      ),
      falseCfg = TreeMap(
        getBlockName(1) -> getBlockName(6),
        getBlockName(2) -> getBlockName(4)
      )
    ))
    assert {
      result === Scope[CfgBasicBlock](
        basicBlocks = TreeMap(
          getBlockName(1) -> getTestConditionalBlock(1),
          getBlockName(2) -> getTestConditionalBlock(2),
          getBlockName(6) -> noOpBlock
        ),
        entry = getBlockName(1),
        exit = getBlockName(6),
        regCfg = TreeMap(
        ),
        trueCfg = TreeMap(
          getBlockName(1) -> getBlockName(2),
          getBlockName(2) -> getBlockName(6)
        ),
        falseCfg = TreeMap(
          getBlockName(1) -> getBlockName(6),
          getBlockName(2) -> getBlockName(6)
        )
      )
    }
  }

  test("should collapse simple noOp in loop") {
    val result = CfgVisitorUtil.removeNoOp(Scope[CfgBasicBlock](
      basicBlocks = TreeMap(
        getBlockName(1) -> getTestConditionalBlock(1), // loop condition
        getBlockName(2) -> noOpBlock, // loop body
        getBlockName(3) -> getTestRegularBlock(3) // exit
      ),
      entry = getBlockName(1),
      exit = getBlockName(3),
      regCfg = TreeMap(
        getBlockName(2) -> getBlockName(1)
      ),
      trueCfg = TreeMap(
        getBlockName(1) -> getBlockName(3)
      ),
      falseCfg = TreeMap(
        getBlockName(1) -> getBlockName(2)
      )
    ))
    assert {
      result === Scope[CfgBasicBlock](
        basicBlocks = TreeMap(
          getBlockName(1) -> getTestConditionalBlock(1), // loop condition
          getBlockName(3) -> getTestRegularBlock(3) // exit
        ),
        entry = getBlockName(1),
        exit = getBlockName(3),
        regCfg = TreeMap(
        ),
        trueCfg = TreeMap(
          getBlockName(1) -> getBlockName(3)
        ),
        falseCfg = TreeMap(
          getBlockName(1) -> getBlockName(1)
        )
      )
    }
  }

  test("should collapse branched noOp in loop") {
    val result = CfgVisitorUtil.removeNoOp(Scope[CfgBasicBlock](
      basicBlocks = TreeMap(
        getBlockName(1) -> getTestConditionalBlock(1), // loop condition
        getBlockName(2) -> getTestConditionalBlock(2), // loop body,
        getBlockName(3) -> noOpBlock, // loop body,
        getBlockName(4) -> getTestRegularBlock(4), // loop body,
        getBlockName(5) -> noOpBlock, // loop body merge noOp,
        getBlockName(6) -> getTestRegularBlock(6) // exit
      ),
      entry = getBlockName(1),
      exit = getBlockName(6),
      regCfg = TreeMap(
        getBlockName(5) -> getBlockName(1),
        getBlockName(3) -> getBlockName(5),
        getBlockName(4) -> getBlockName(5)
      ),
      trueCfg = TreeMap(
        getBlockName(1) -> getBlockName(2),
        getBlockName(2) -> getBlockName(3)
      ),
      falseCfg = TreeMap(
        getBlockName(1) -> getBlockName(6),
        getBlockName(2) -> getBlockName(4)
      )
    ))
    assert {
      result === Scope[CfgBasicBlock](
        basicBlocks = TreeMap(
          getBlockName(1) -> getTestConditionalBlock(1), // loop condition
          getBlockName(2) -> getTestConditionalBlock(2),
          getBlockName(4) -> getTestRegularBlock(4),
          getBlockName(6) -> getTestRegularBlock(6) // exit
        ),
        entry = getBlockName(1),
        exit = getBlockName(6),
        regCfg = TreeMap(
          getBlockName(4) -> getBlockName(1)
        ),
        trueCfg = TreeMap(
          getBlockName(1) -> getBlockName(2),
          getBlockName(2) -> getBlockName(1)
        ),
        falseCfg = TreeMap(
          getBlockName(1) -> getBlockName(6),
          getBlockName(2) -> getBlockName(4)
        )
      )
    }
  }
}
