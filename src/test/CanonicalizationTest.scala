import org.scalatest.funsuite.AnyFunSuite
import TestUtils._
import decaf.compile._
import decaf.compile.ir._
import decaf.compile.cfg._
import decaf.compile.optim.common_subexpression_elmination.AvailableExpressions

import scala.collection.immutable._

class CanonicalizationTest extends AnyFunSuite {
  test("a + b") {
    val expression = CfgBinOpExpr(op = ArithBinOpType.ADD,
      leftExpr = CfgVariable("a", 0),
      rightExpr = CfgVariable("b", 0)
    )
    assert(AvailableExpressions.canonicalization(expression) === expression)
  }

  test("b + a") {
    val expression = CfgBinOpExpr(op = ArithBinOpType.ADD,
      leftExpr = CfgVariable("b", 0),
      rightExpr = CfgVariable("a", 0)
    )
    assert(AvailableExpressions.canonicalization(expression) === CfgBinOpExpr(op = ArithBinOpType.ADD,
      leftExpr = CfgVariable("a", 0),
      rightExpr = CfgVariable("b", 0)
    ))
  }

  test("a + a") {
    val expression = CfgBinOpExpr(op = ArithBinOpType.ADD,
      leftExpr = CfgVariable("a", 0),
      rightExpr = CfgVariable("a", 0)
    )
    assert(AvailableExpressions.canonicalization(expression) === expression)
  }

  test("a + 1") {
    val expression = CfgBinOpExpr(op = ArithBinOpType.ADD,
      leftExpr = CfgVariable("a", 0),
      rightExpr = CfgImmediate(1L)
    )
    assert(AvailableExpressions.canonicalization(expression) === expression)
  }

  test("1 + a") {
    val expression = CfgBinOpExpr(op = ArithBinOpType.ADD,
      leftExpr = CfgImmediate(1L),
      rightExpr = CfgVariable("a", 0)
    )
    assert(AvailableExpressions.canonicalization(expression) === CfgBinOpExpr(op = ArithBinOpType.ADD,
      leftExpr = CfgVariable("a", 0),
      rightExpr = CfgImmediate(1L)
    ))
  }

  test("1 + 2") {
    val expression = CfgBinOpExpr(op = ArithBinOpType.ADD,
      leftExpr = CfgImmediate(1L),
      rightExpr = CfgImmediate(2L)
    )
    assert(AvailableExpressions.canonicalization(expression) === CfgBinOpExpr(op = ArithBinOpType.ADD,
      leftExpr = CfgImmediate(2L),
      rightExpr = CfgImmediate(1L)
    ))
  }

  test("2 + 1") {
    val expression = CfgBinOpExpr(op = ArithBinOpType.ADD,
      leftExpr = CfgImmediate(2L),
      rightExpr = CfgImmediate(1L)
    )
    assert(AvailableExpressions.canonicalization(expression) === expression)
  }

  test("1 + 1") {
    val expression = CfgBinOpExpr(op = ArithBinOpType.ADD,
      leftExpr = CfgImmediate(1L),
      rightExpr = CfgImmediate(1L)
    )
    assert(AvailableExpressions.canonicalization(expression) === expression)
  }
}
