package decaf.compile.cfg

/**
 * Represents a function that has been destructed under the control flow graph.
 *
 * @param identifier name of the function.
 * @param parameters function parameters.
 * @param scope      the code of the function.
 */
case class Function[Block](
  identifier: String,
  parameters: List[CfgVariable],
  scope: Scope[Block],
  returnsValue: Boolean
) {
  override def toString: String = {
    val parametersString = parameters.map(_.toString).mkString(", ")
    val scopeString = scope.toString.replace("\n", "\n\t") // add indent
    s"""$identifier($parametersString) => ${if (returnsValue) "value" else "void"} {
       |\t$scopeString
       |}""".stripMargin
  }
}
