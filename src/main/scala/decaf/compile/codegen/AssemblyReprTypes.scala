package decaf.compile.codegen

case class AsmProgram(header: List[Asm], program: List[AsmFunction], panicCode: String)

case class AsmFunction(identifier: String, before: List[Asm], after: List[Asm], basicBlocks: List[AsmBasicBlock])
case class AsmBasicBlock(asm: List[Asm])

// Any value -- immediate, register, or memory location
sealed abstract class VALUE(value: Any)

case class IMM(v: Long) extends VALUE(v) {
  override def toString: String = "$" + v
}

sealed abstract class MEMLOC(location: String) extends VALUE(location) {
  override def toString: String = location
}

case class REG(regName: String) extends MEMLOC(regName)
case class STACKLOC(offset: Long, register: REG) extends MEMLOC(f"${offset.toString}(${register.toString})")
case class JMPLOC(locationStr: String) extends MEMLOC(locationStr)
case class STRLOC(locationStr: String) extends MEMLOC(locationStr)

// Caller-save
object RegisterDefinitions {
  val RAX: REG = REG("%rax")
  val RCX: REG = REG("%rcx")
  val RDX: REG = REG("%rdx")
  val RSI: REG = REG("%rsi")
  val RDI: REG = REG("%rdi")
  val RSP: REG = REG("%rsp")
  val R8: REG = REG("%r8")
  val R9: REG = REG("%r9")
  val R10: REG = REG("%r10")
  val R11: REG = REG("%r11")

  // Callee-save
  val RBX: REG = REG("%rbx")
  val RBP: REG = REG("%rbp")
  val R12: REG = REG("%r12")
  val R13: REG = REG("%r13")
  val R14: REG = REG("%r14")
  val R15: REG = REG("%r15")

  val PARAMETER_REGISTERS: List[REG] = List(RDI, RSI, RDX, RCX, R8, R9)
  val CALLEE_SAVE_REGISTERS: List[REG] = List(RBX, RBP, R12, R13, R14, R15)
  val CALLER_SAVE_REGISTERS: List[REG] = List(RAX, RCX, RDX, RSI, RDI, RSP, R8, R9, R10, R11)
}

sealed abstract class Asm(val parts: List[String])

// comment
case class COMMENT(comment: String) extends Asm(parts = List("//" + comment))
// Represents .data, .string, .quad 0
case class DIRECTIVE(directive: String) extends Asm(parts = List(directive))
// label:
case class LABEL(label: String) extends Asm(parts = List(label + ":"))
// anything special that needs to just be directly put in assembly
case class TEXT_INSTRUCTION(text: String) extends Asm(parts = List(text))

sealed abstract class INSTRUCTION(val ins: String, val values: VALUE*) extends Asm(parts = List(ins) ++ values.map(x => x.toString))
case class MOVQ(from: VALUE, to: MEMLOC) extends INSTRUCTION("movq", from, to)
case class ENTER(allocateBytes: IMM, offset: IMM) extends INSTRUCTION("enter", allocateBytes, offset)
case class PUSH(value: VALUE) extends INSTRUCTION("push", value)
case class POP(popToRegister: REG) extends INSTRUCTION("pop", popToRegister)
case class LEAVE() extends INSTRUCTION("leave")
case class RET() extends INSTRUCTION("ret")
case class CQTO() extends INSTRUCTION("cqto")
case class CMPQ(left: VALUE, right: MEMLOC) extends INSTRUCTION("cmpq", left, right)
case class LEAQ(from: MEMLOC, right: REG) extends INSTRUCTION("leaq", from, right)
case class NEGQ(reg: MEMLOC) extends INSTRUCTION("negq", reg)

// Arithmetic
case class ADDQ(left: VALUE, right: MEMLOC) extends INSTRUCTION("addq", left, right)
case class SUBQ(left: VALUE, right: MEMLOC) extends INSTRUCTION("subq", left, right)
case class IMULQ(left: MEMLOC, right: REG) extends INSTRUCTION("imulq", left, right)
case class IMULQ_IMM(left: IMM, right: MEMLOC, to: REG) extends INSTRUCTION("imulq", left, right, to)
case class ANDQ(left: VALUE, right: MEMLOC) extends INSTRUCTION("andq", left, right)
case class ORQ(left: VALUE, right: MEMLOC) extends INSTRUCTION("orq", left, right)


case class IDIVQ(divisor: MEMLOC) extends INSTRUCTION("idivq", divisor)
case class XORQ(left: VALUE, right: MEMLOC) extends INSTRUCTION("xorq", left, right)

// Conditional moves
case class CMOVG(from: MEMLOC, to: REG) extends INSTRUCTION("cmovg", from, to)
case class CMOVGE(from: MEMLOC, to: REG) extends INSTRUCTION("cmovge", from, to)
case class CMOVL(from: MEMLOC, to: REG) extends INSTRUCTION("cmovl", from, to)
case class CMOVLE(from: MEMLOC, to: REG) extends INSTRUCTION("cmovle", from, to)
case class CMOVE(from: MEMLOC, to: REG) extends INSTRUCTION("cmove", from, to)
case class CMOVNE(from: MEMLOC, to: REG) extends INSTRUCTION("cmovne", from, to)

// Jump Instructions
case class JMP(location: JMPLOC) extends INSTRUCTION("jmp", location)
case class JG(location: JMPLOC) extends INSTRUCTION("jg", location)
case class JGE(location: JMPLOC) extends INSTRUCTION("jge", location)
case class JL(location: JMPLOC) extends INSTRUCTION("jl", location)
case class JLE(location: JMPLOC) extends INSTRUCTION("jle", location)
case class JE(location: JMPLOC) extends INSTRUCTION("je", location)
case class JNE(location: JMPLOC) extends INSTRUCTION("jne", location)
case class CALL(callLocation: JMPLOC) extends INSTRUCTION("call", callLocation)
case class JA(location: JMPLOC) extends INSTRUCTION("ja", location)
case class JAE(location: JMPLOC) extends INSTRUCTION("jae", location)

// Shifts
case class SHL(shiftAmt: IMM, loc: MEMLOC) extends INSTRUCTION("shl", shiftAmt, loc)
case class SAR(shiftAmt: IMM, loc: MEMLOC) extends INSTRUCTION("sar", shiftAmt, loc)

/*
  CgSymbol represents a variable -> MEMLOC
  location: Register or Stack Location or Global Location
  symbolType: SymbolType enum
  stackOffset: 0 if not applicable, a number if location is Stack location
  definedInFunction: where this variable was first defined
 */
case class CgSymbol(val location: MEMLOC, val symbolType: SymbolType, val stackOffset: Long)
sealed abstract class SymbolType()
object SymbolType {
  final case object STRING extends SymbolType
  final case object REGULAR extends SymbolType
  final case object LOCAL_ARRAY extends SymbolType
  final case object GLOBAL_ARRAY extends SymbolType
}