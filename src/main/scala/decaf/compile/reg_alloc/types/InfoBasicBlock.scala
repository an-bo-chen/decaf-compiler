package decaf.compile.reg_alloc.types

import decaf.compile.cfg.{CfgCondExpression, CfgFieldDecl, CfgStatement}

/**
 * Represents a basic block with additional information about the statement of the program
 * before and after each statement.
 *
 * @param fieldDecls see `BasicBlock`
 * @param statements see `BasicBlock`
 * @tparam StateInfo The type of information contained in this data type
 */
abstract sealed class InfoBasicBlock[StateInfo](
  val fieldDecls: List[CfgFieldDecl],
  val statements: List[(CfgStatement, StateInfo)]
)

case class InfoRegularBlock[StateInfo](
  override val fieldDecls: List[CfgFieldDecl],
  override val statements: List[(CfgStatement, StateInfo)]
) extends InfoBasicBlock[StateInfo](fieldDecls, statements)

/**
 *
 * @param fieldDecls see `BasicBlock`
 * @param statements see `BasicBlock`
 * @param condition the condition and information about the state
 * @tparam StateInfo The type of information contained in this data type
 */
case class InfoConditionalBlock[StateInfo](
  override val fieldDecls: List[CfgFieldDecl],
  override val statements: List[(CfgStatement, StateInfo)],
  condition: (CfgCondExpression, StateInfo)
) extends InfoBasicBlock[StateInfo](fieldDecls, statements)