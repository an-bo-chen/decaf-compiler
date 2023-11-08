package decaf.compile

import decaf.compile.cfg.Scope
import decaf.compile.reg_alloc.types.{RegStatement, VirtualLocation, VirtualRegister}

package object reg_alloc {
  type VirtualRegisterScope = Scope[RegStatement[VirtualRegister]]
  type SplitScope = Scope[RegStatement[VirtualLocation]]
}
