import TestUtils._
import decaf.compile.CfgBasicBlock
import decaf.compile.cfg._
import decaf.compile.optim.loop_optim.{LoopInvariant, LoopOptim}
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.immutable.Map

class OptimLoopInvariantTest extends AnyFunSuite {
  // reserve 0 and 1 for global variables
  private val GLOBAL_X = getVariable(0)
  private val GLOBAL_Y = getVariable(1)
  private val GLOBALS = Set(
    GLOBAL_X, GLOBAL_Y
  )

  private def getPreHeaderLabel(number: Int) = LoopInvariant.getPreHeaderLabel(getBlockName(number))
  private def getInvariantLabel(number: Int) = LoopInvariant.getInvariantLabel(getBlockName(number))

  test("simple while, no invariants") {
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

    val result = LoopInvariant.optimize(scope, GLOBALS)

    assert {
      result === Scope[CfgBasicBlock](
        basicBlocks = Map(
          getPreHeaderLabel(1) -> getTestConditionalBlock(1),
          getBlockName(1) -> getTestConditionalBlock(1),
          getBlockName(2) -> getTestRegularBlock(2),
          getBlockName(3) -> getTestRegularBlock(3)
        ),
        entry = getPreHeaderLabel(1),
        exit = getBlockName(3),
        regCfg = Map(
          getBlockName(2) -> getBlockName(1)
        ),
        trueCfg = Map(
          getPreHeaderLabel(1) -> getBlockName(2),
          getBlockName(1) -> getBlockName(2)
        ),
        falseCfg = Map(
          getPreHeaderLabel(1) -> getBlockName(3),
          getBlockName(1) -> getBlockName(3)
        )
      )
    }
  }

  test("simple while, with invariants") {
    val scope = Scope[CfgBasicBlock](
      basicBlocks = Map(
        getBlockName(1) -> ConditionalBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            getAssignUsesStatement_NoFunction(2, Iterable(3))
          ),
          condition = getExpressionUses(Iterable(2,3))
        ),
        getBlockName(2) -> RegularBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            getAssignUsesStatement_NoFunction(1, Iterable(0))
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
        getBlockName(1) -> getBlockName(3)
      ),
      falseCfg = Map(
        getBlockName(1) -> getBlockName(2)
      )
    )

    val result = LoopInvariant.optimize(scope, GLOBALS)

    assert {
      result === Scope[CfgBasicBlock](
        basicBlocks = Map(
          getPreHeaderLabel(1) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty,
            statements = List.empty,
            condition = getExpressionUses(Iterable(2,3))
          ),
          getInvariantLabel(1) -> RegularBlock[CfgStatement](
            fieldDecls = List.empty,
            statements = List(
              getAssignUsesStatement_NoFunction(2, Iterable(3)),
              getAssignUsesStatement_NoFunction(1, Iterable(0))
            )
          ),
          getBlockName(1) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty,
            statements = List.empty, // removed
            condition = getExpressionUses(Iterable(2,3))
          ),
          getBlockName(2) -> RegularBlock[CfgStatement](
            fieldDecls = List.empty,
            statements = List.empty // removed
          ),
          getBlockName(3) -> getTestRegularBlock(3)
        ),
        entry = getPreHeaderLabel(1),
        exit = getBlockName(3),
        regCfg = Map(
          getInvariantLabel(1) -> getBlockName(2),
          getBlockName(2) -> getBlockName(1)
        ),
        trueCfg = Map(
          getPreHeaderLabel(1) -> getBlockName(3),
          getBlockName(1) -> getBlockName(3)
        ),
        falseCfg = Map(
          getPreHeaderLabel(1) -> getInvariantLabel(1),
          getBlockName(1) -> getBlockName(2)
        )
      )
    }
  }

  test("while, with continue, break") {
    val scope = Scope[CfgBasicBlock](
      basicBlocks = Map(
        getBlockName(1) -> getTestRegularBlock(1),
        getBlockName(2) -> ConditionalBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            // getAssignUsesStatement_NoFunction(2, Iterable(3))
          ),
          condition = getExpressionUses(Iterable(2,3))
        ),
        getBlockName(3) -> ConditionalBlock[CfgStatement]( // break if true
          fieldDecls = List.empty,
          statements = List(
            // getAssignUsesStatement_NoFunction(1, Iterable(0))
          ),
          condition = getExpressionUses(Iterable(2,3))
        ),
        getBlockName(4) -> ConditionalBlock[CfgStatement]( // continue if true
          fieldDecls = List.empty,
          statements = List(
            // getAssignUsesStatement_NoFunction(1, Iterable(0))
          ),
          condition = getExpressionUses(Iterable(2,3))
        ),
        getBlockName(5) -> RegularBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            // getAssignUsesStatement_NoFunction(1, Iterable(0))
          )
        ),
        getBlockName(6) -> getTestRegularBlock(6)
      ),
      entry = getBlockName(1),
      exit = getBlockName(6),
      regCfg = Map(
        getBlockName(1) -> getBlockName(2),
        getBlockName(5) -> getBlockName(2)
      ),
      trueCfg = Map(
        getBlockName(2) -> getBlockName(3),
        getBlockName(3) -> getBlockName(6), // break
        getBlockName(4) -> getBlockName(2) // continue
      ),
      falseCfg = Map(
        getBlockName(2) -> getBlockName(6),
        getBlockName(3) -> getBlockName(4),
        getBlockName(4) -> getBlockName(5)
      )
    )

    val result = LoopInvariant.optimize(scope, GLOBALS)

    assert {
      result === Scope[CfgBasicBlock](
        basicBlocks = Map(
          getBlockName(1) -> getTestRegularBlock(1),
          getPreHeaderLabel(2) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty,
            statements = List(
              // getAssignUsesStatement_NoFunction(2, Iterable(3))
            ),
            condition = getExpressionUses(Iterable(2,3))
          ),
          getBlockName(2) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty,
            statements = List(
              // getAssignUsesStatement_NoFunction(2, Iterable(3))
            ),
            condition = getExpressionUses(Iterable(2,3))
          ),
          getBlockName(3) -> ConditionalBlock[CfgStatement]( // break if true
            fieldDecls = List.empty,
            statements = List(
              // getAssignUsesStatement_NoFunction(1, Iterable(0))
            ),
            condition = getExpressionUses(Iterable(2,3))
          ),
          getBlockName(4) -> ConditionalBlock[CfgStatement]( // continue if true
            fieldDecls = List.empty,
            statements = List(
              // getAssignUsesStatement_NoFunction(1, Iterable(0))
            ),
            condition = getExpressionUses(Iterable(2,3))
          ),
          getBlockName(5) -> RegularBlock[CfgStatement](
            fieldDecls = List.empty,
            statements = List(
              // getAssignUsesStatement_NoFunction(1, Iterable(0))
            )
          ),
          getBlockName(6) -> getTestRegularBlock(6)
        ),
        entry = getBlockName(1),
        exit = getBlockName(6),
        regCfg = Map(
          getBlockName(1) -> getPreHeaderLabel(2),
          getBlockName(5) -> getBlockName(2)
        ),
        trueCfg = Map(
          getPreHeaderLabel(2) -> getBlockName(3),
          getBlockName(2) -> getBlockName(3),
          getBlockName(3) -> getBlockName(6), // break
          getBlockName(4) -> getBlockName(2) // continue
        ),
        falseCfg = Map(
          getPreHeaderLabel(2) -> getBlockName(6),
          getBlockName(2) -> getBlockName(6),
          getBlockName(3) -> getBlockName(4),
          getBlockName(4) -> getBlockName(5)
        )
      )
    }
  }

  test("while, complex invariants/ noninvariants") {
    val scope = Scope[CfgBasicBlock](
      basicBlocks = Map(
        getBlockName(1) -> getTestRegularBlock(1),
        getBlockName(2) -> ConditionalBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List(
            // getAssignUsesStatement_NoFunction(2, Iterable(3))
          ),
          condition = getExpressionUses(Iterable(2,3))
        ),
        getBlockName(3) -> ConditionalBlock[CfgStatement]( // break if true
          fieldDecls = List.empty,
          statements = List(
            getAssignUsesStatement_NoFunction(1, Iterable(0)),
            getAssignUsesStatement_NoFunction(4, Iterable(5))
          ),
          condition = getExpressionUses(Iterable(2,3))
        ),
        getBlockName(4) -> RegularBlock[CfgStatement]( // continue if true
          fieldDecls = List.empty,
          statements = List(
            getAssignUsesStatement_NoFunction(0, Iterable(0))
          )
        ),
        getBlockName(5) -> RegularBlock[CfgStatement]( // continue if true
          fieldDecls = List.empty,
          statements = List(
            getAssignUsesStatement(3, Iterable(4,5))
          )
        ),
        getBlockName(6) -> getTestRegularBlock(6)
      ),
      entry = getBlockName(1),
      exit = getBlockName(6),
      regCfg = Map(
        getBlockName(1) -> getBlockName(2),
        getBlockName(4) -> getBlockName(5),
        getBlockName(5) -> getBlockName(2)
      ),
      trueCfg = Map(
        getBlockName(2) -> getBlockName(3),
        getBlockName(3) -> getBlockName(4)
      ),
      falseCfg = Map(
        getBlockName(2) -> getBlockName(6),
        getBlockName(3) -> getBlockName(5)
      )
    )

    val result = LoopInvariant.optimize(scope, GLOBALS)

    assert {
      result === Scope[CfgBasicBlock](
        basicBlocks = Map(
          getBlockName(1) -> getTestRegularBlock(1),
          getPreHeaderLabel(2) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty,
            statements = List(
              // getAssignUsesStatement_NoFunction(2, Iterable(3))
            ),
            condition = getExpressionUses(Iterable(2,3))
          ),
          getInvariantLabel(2) -> RegularBlock[CfgStatement](
            fieldDecls = List.empty,
            statements = List(
              getAssignUsesStatement_NoFunction(4, Iterable(5))
            )
          ),
          getBlockName(2) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty,
            statements = List(
              // getAssignUsesStatement_NoFunction(2, Iterable(3))
            ),
            condition = getExpressionUses(Iterable(2,3))
          ),
          getBlockName(3) -> ConditionalBlock[CfgStatement]( // break if true
            fieldDecls = List.empty,
            statements = List(
              getAssignUsesStatement_NoFunction(1, Iterable(0))
            ),
            condition = getExpressionUses(Iterable(2,3))
          ),
          getBlockName(4) -> RegularBlock[CfgStatement]( // continue if true
            fieldDecls = List.empty,
            statements = List(
              getAssignUsesStatement_NoFunction(0, Iterable(0))
            )
          ),
          getBlockName(5) -> RegularBlock[CfgStatement]( // continue if true
            fieldDecls = List.empty,
            statements = List(
              getAssignUsesStatement(3, Iterable(4,5))
            )
          ),
          getBlockName(6) -> getTestRegularBlock(6)
        ),
        entry = getBlockName(1),
        exit = getBlockName(6),
        regCfg = Map(
          getBlockName(1) -> getPreHeaderLabel(2),
          getInvariantLabel(2) -> getBlockName(3),
          getBlockName(4) -> getBlockName(5),
          getBlockName(5) -> getBlockName(2)
        ),
        trueCfg = Map(
          getPreHeaderLabel(2) -> getInvariantLabel(2),
          getBlockName(2) -> getBlockName(3),
          getBlockName(3) -> getBlockName(4)
        ),
        falseCfg = Map(
          getPreHeaderLabel(2) -> getBlockName(6),
          getBlockName(2) -> getBlockName(6),
          getBlockName(3) -> getBlockName(5)
        )
      )
    }
  }

  test("simple nested while") {
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

    val result = LoopInvariant.optimize(scope, GLOBALS)

    assert {
      result === Scope[CfgBasicBlock](
        basicBlocks = Map(
          getPreHeaderLabel(1) -> getTestConditionalBlock(1),
          getPreHeaderLabel(2) -> getTestConditionalBlock(2),
          getBlockName(1) -> getTestConditionalBlock(1),
          getBlockName(2) -> getTestConditionalBlock(2),
          getBlockName(3) -> getTestRegularBlock(3),
          getBlockName(4) -> getTestRegularBlock(4)
        ),
        entry = getPreHeaderLabel(1),
        exit = getBlockName(4),
        regCfg = Map(
          getBlockName(3) -> getBlockName(2)
        ),
        trueCfg = Map(
          getPreHeaderLabel(1) -> getPreHeaderLabel(2),
          getPreHeaderLabel(2) -> getBlockName(3),
          getBlockName(1) -> getPreHeaderLabel(2),
          getBlockName(2) -> getBlockName(3)
        ),
        falseCfg = Map(
          getPreHeaderLabel(1) -> getBlockName(4),
          getPreHeaderLabel(2) -> getBlockName(1),
          getBlockName(1) -> getBlockName(4),
          getBlockName(2) -> getBlockName(1)
        )
      )
    }
  }
}
