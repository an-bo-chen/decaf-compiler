package decaf.compile.cfg

sealed abstract class CfgFunctionCallParam(
  val param: Any
) {

  override def toString: String = {
    param.toString
  }
}

case class CfgFunctionCallValueParam(
  override val param: CfgValue
) extends CfgFunctionCallParam(param)

/**
 * Function param string
 *
 * @param param id of the string literal, see stringData in cfgProgram
 *              (NOT the string itself)
 */
case class CfgFunctionCallStringParam(
  override val param: String
) extends CfgFunctionCallParam(param)