package decaf.compile.cfg

/**
 * Represents a declaration of a field.
 *
 * @param variable variable being declared.
 */
sealed abstract class CfgFieldDecl(
  val variable: CfgVariable
)

/**
 * Represents a declaration of a non-array variable.
 *
 * @param variable     variable being declared.
 */
case class CfgRegFieldDecl(
  override val variable: CfgVariable
) extends CfgFieldDecl(variable) {
  override def toString: String = {
    variable.toString
  }
}

/**
 * Represents a declaration of an array variable.
 * The declared length is contained in initialValue.
 *
 * @param variable variable being declared.
 */
case class CfgArrayFieldDecl(
  override val variable: CfgVariable,
  length: Long
) extends CfgFieldDecl(variable) {
  override def toString: String = {
    s"${variable.toString}[$length]"
  }
}

/**
 * Represents a declaration of a field.
 *
 * @param variable variable being declared.
 */
sealed abstract class CfgGlobalFieldDecl(
  val variable: CfgVariable,
  val initialValue: Any
)

/**
 * Represents a declaration of a non-array variable.
 *
 * @param variable     variable being declared.
 */
case class CfgGlobalRegFieldDecl(
  override val variable: CfgVariable,
  override val initialValue: Long
) extends CfgGlobalFieldDecl(variable, initialValue) {
  override def toString: String = {
    s"${variable.toString} -> $initialValue"
  }
}

/**
 * Represents a declaration of an array variable.
 * The declared length is contained in initialValue.
 *
 * @param variable variable being declared.
 */
case class CfgGlobalArrayFieldDecl(
  override val variable: CfgVariable,
  override val initialValue: Option[List[Long]],
  length: Long
) extends CfgGlobalFieldDecl(variable, initialValue) {
  override def toString: String = {
    val initialValueString = initialValue match {
      case Some(x) =>
        val valuesString = x.length match {
          case 0 => ""
          case _ => x
            .map(_.toString)
            .reduceLeft((prev, str) => s"$prev, $str")
        }
        s"{$valuesString}"
      case None => "_"
    }
    s"${variable.toString}[$length] = $initialValueString"
  }
}
