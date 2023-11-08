package decaf.compile.cfg

/**
 * Represents a location similar to `IrLocation`.
 *
 * @param variable variable being accessed
 * @param index    optional index if the variable is an array
 */
case class CfgLocation(
  variable: CfgVariable,
  index: Option[CfgValue]
) {
  override def toString: String = {
    if (index.isDefined) {
      s"${variable.toString}[${index.get.toString}]"
    } else {
      s"${variable.toString}"
    }
  }
}
