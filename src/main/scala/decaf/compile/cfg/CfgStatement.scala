package decaf.compile.cfg

/**
 * Represents a statement. `CfgStatement`s are similar to `IrStatement`s, but not
 * including any control-flow statements except for returns and function calls.
 *
 * In addition, `CfgStatement`s contain `CfgLocation`s instead of `IrLocation`s.
 */
sealed abstract class CfgStatement()

sealed abstract class CfgAssignStatement(
  val to: CfgLocation,
  val value: Any
) extends CfgStatement()

case class CfgRegAssignStatement(
  override val to: CfgLocation,
  override val value: CfgExpression
) extends CfgAssignStatement(to, value) {
  override def toString: String = {
    s"${to.toString} = ${value.toString}"
  }
}

case class CfgArrayAssignStatement(
  override val to: CfgLocation,
  override val value: List[Long]
) extends CfgAssignStatement(to, value) {
  override def toString: String = {
    s"${to.toString} = {${value.map(_.toString).mkString(", ")}}"
  }
}

case class CfgFunctionCallStatement(
  functionName: String,
  parameters: List[CfgFunctionCallParam]
) extends CfgStatement() {
  override def toString: String = {
    s"""$functionName(${parameters.map(_.toString).mkString(", ")})"""
  }
}

case class CfgReturnStatement(
  value: Option[CfgValue]
) extends CfgStatement() {
  override def toString: String = {
    val valueString = value match {
      case Some(x) => x.toString
      case None => ""
    }
    s"RETURN $valueString"
  }
}
