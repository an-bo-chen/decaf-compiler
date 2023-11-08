package decaf.compile.reg_alloc.types

import decaf.compile.codegen.REG

sealed abstract class AsmLocation {

}

/**
 * A virtual location.
 */
sealed abstract class VirtualLocation extends AsmLocation()

case class VirtualRegister(
  number: Long
) extends VirtualLocation()

case class VirtualStack(
  offset: Long
) extends VirtualLocation()

/**
 * Actual location compatible with x86
 */
sealed abstract class RealLocation extends AsmLocation()

case class RealRegister(
  register: REG
) extends  RealLocation()

case class RealStack(
  offset: Long
) extends  RealLocation()