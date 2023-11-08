package decaf.compile.cfg

import decaf.compile.ir.{BinOpType, UnaryOpType}

/**
 * Represents an expression. `CfgExpression`s are similar to `IrExpression`s, but with
 * no nesting and a few more granular differences:
 * `CfgVarReadExpr` reads from a `CfgLocation` rather than an `IrLocation`,
 * There is no ternary expression.
 */
sealed abstract class CfgExpression()

sealed trait CfgCondExpression extends CfgExpression

/**
 * Represents a value, either a symbol that points to a variable or an immediate.
 */
sealed trait CfgValue extends CfgExpression with CfgCondExpression

case class CfgImmediate(
  value: Long
) extends CfgExpression with CfgValue with CfgCondExpression{
  override def toString: String = {
    value.toString
  }
}

/**
 * Represents a variable.
 *
 * @param identifier name of the variable.
 */
case class CfgVariable(
  identifier: String,
  number: Int
) extends CfgExpression with CfgValue with CfgCondExpression{
  override def toString: String = {
    val numberString = if (number == 1) "" else s"_$number"
    s"$identifier$numberString"
  }
}

case class CfgArrayReadExpr(
  variable: CfgVariable,
  index: CfgValue
) extends CfgExpression() {
  override def toString: String = {
    s"$variable[$index]"
  }
}

case class CfgLenExpr(
  variable: CfgVariable
) extends CfgExpression() {
  override def toString: String = {
    s"LEN(${variable.toString})"
  }
}

case class CfgFunctionCallExpr(
  identifier: String,
  params: List[CfgFunctionCallParam]
) extends CfgExpression() {

  override def toString: String = {
    val builder = StringBuilder.newBuilder

    params.foreach(p => {
      builder.append(p.toString + ", ")
    })

    if (params.nonEmpty){
      s"$identifier(${builder.substring(0, builder.length() - 2)})"
    } else {
      s"$identifier()"
    }
  }
}

case class CfgUnaryOpExpr(
  op: UnaryOpType,
  expr: CfgValue
) extends CfgExpression() with CfgCondExpression {
  override def toString: String = {
    s"${op.opStr}${expr.toString}"
  }
}

case class CfgBinOpExpr(
  op: BinOpType,
  leftExpr: CfgValue,
  rightExpr: CfgValue
) extends CfgExpression() with CfgCondExpression {
  override def toString: String = {
    s"${leftExpr.toString} ${op.opStr} ${rightExpr.toString}"
  }
}