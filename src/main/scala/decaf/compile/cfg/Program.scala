package decaf.compile.cfg

/**
 * Represents a destructed program.
 *
 * @param imports         names of imported functions.
 * @param globalVariables declared global variables including possible initial values.
 * @param stringData      string literals used by the program, keyed by some unique identifier.
 * @param functions       destructed functions.
 */
case class Program[Block](
  imports: List[String],
  globalVariables: List[CfgGlobalFieldDecl],
  stringData: Map[String, String],
  functions: List[Function[Block]]
) {
  override def toString: String = {
    val importString = this.imports
      .foldLeft("")((prev, str) => s"$prev\n\t$str")

    val stringDataString = this.stringData
      .map({ case (id, value) => s"$id -> ${'"'}$value${'"'}" })
      .foldLeft("")((prev, str) => s"$prev\n\t$str")

    val globalsString = this.globalVariables
      .map(_.toString)
      .foldLeft("")((prev, str) => s"$prev\n\t$str")

    val functionsString = this.functions
      .map(_.toString)
      .foldLeft("")((prev, str) => s"$prev\n$str")
      .replace("\n", "\n\t") // add indent

    s"""|IMPORTS:$importString
        |STRINGS:$stringDataString
        |GLOBALS:$globalsString
        |FUNCTIONS:$functionsString
        |""".stripMargin
  }
}
