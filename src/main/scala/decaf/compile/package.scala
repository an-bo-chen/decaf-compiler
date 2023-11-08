package decaf

import decaf.compile.cfg._
import decaf.compile.reg_alloc.types.RegBasicBlock

package object compile {
  type CfgProgram = Program[CfgBasicBlock]
  type CfgFunction = Function[CfgBasicBlock]
  type CfgScope = Scope[CfgBasicBlock]
  type CfgBasicBlock = BasicBlock[CfgStatement]
  type CfgRegularBlock = RegularBlock[CfgStatement]
  type CfgConditionalBlock = ConditionalBlock[CfgStatement]

  type RegProgram = Program[RegBasicBlock]
  type RegFunction = Function[RegBasicBlock]
  type RegScope = Scope[RegBasicBlock]
}
