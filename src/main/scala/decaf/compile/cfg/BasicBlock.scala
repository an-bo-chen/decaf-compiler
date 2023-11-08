package decaf.compile.cfg

/**
 * Represents some basic block.
 *
 * @param fieldDecls list of declared fields and initial values.
 * @param statements list of statements in the block.
 */
sealed abstract class BasicBlock[Statement](
  val fieldDecls: List[CfgFieldDecl],
  val statements: List[Statement]
) {
  override def toString: String = {
    val fieldDeclsString = if (fieldDecls.isEmpty) "" else
      "(" + fieldDecls
        .map(_.toString)
        .mkString(", ") + ")\n"

    val statementsString = if (statements.isEmpty) "" else statements
      .map(stmt => s">\t${stmt.toString}")
      .mkString("\n")
    s"""${if (fieldDecls.nonEmpty || statements.nonEmpty) "\n" else ""}$fieldDeclsString$statementsString""".stripMargin
  }
}

/**
 * Represents a basic block with a regular, always-taken transition.
 *
 * @param statements list of statements in the block.
 */
case class RegularBlock[Statement](
  override val fieldDecls: List[CfgFieldDecl],
  override val statements: List[Statement]
) extends BasicBlock(fieldDecls, statements)

/**
 * Represents a conditional basic block.
 *
 * @param statements list of statements in the block.
 * @param condition  the condition to evaluate to determine control flow for this block.
 *                   This condition is NOT included in statements.
 */
case class ConditionalBlock[Statement](
  override val fieldDecls: List[CfgFieldDecl],
  override val statements: List[Statement],
  condition: CfgCondExpression
) extends BasicBlock(fieldDecls, statements) {
  override def toString: String = {
    s"""${super.toString}
       |${condition.toString} ?""".stripMargin
  }
}