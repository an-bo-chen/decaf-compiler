package decaf.compile.reg_alloc.types

import decaf.compile.cfg.{CfgCondExpression, CfgFieldDecl, CfgVariable}

abstract sealed class RegBasicBlock (
  val fieldDecls: List[CfgFieldDecl],
  val statements: List[RegStatement[RealRegister]]
)

case class RegRegularBlock (
  override val fieldDecls: List[CfgFieldDecl],
  override val statements: List[RegStatement[RealRegister]]
) extends RegBasicBlock(fieldDecls, statements)

case class RegConditionalBlock (
  override val fieldDecls: List[CfgFieldDecl],
  override val statements: List[RegStatement[RealRegister]],
  condition: (CfgCondExpression, Map[CfgVariable, RealRegister])
) extends RegBasicBlock(fieldDecls, statements)
