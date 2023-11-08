package decaf.compile.codegen

object AssemblyReprToStr {

  private final val asmCommands = List(".data", ".globl", ".text")

  def generateProgram(program: AsmProgram): String = {
    val assemblyOutput = StringBuilder.newBuilder

    program.header.foreach(x => assemblyOutput.append(generateAsm(x)))
    program.program.foreach(func => {
      assemblyOutput.append(func.identifier + ":\n")

      func.before.foreach(x => assemblyOutput.append(generateAsm(x)))
      func.basicBlocks.foreach(basicBlocks => {
        basicBlocks.asm.foreach(x => assemblyOutput.append(generateAsm(x)))
      })
      func.after.foreach(x => assemblyOutput.append(generateAsm(x)))
    })

    assemblyOutput.append(program.panicCode)
    assemblyOutput.toString()
  }

  def generateAsm(asm: Asm): String = {
    asm match {
      case COMMENT(comment) => "     " + asm.parts.head + "\n"
      case DIRECTIVE(directive) => {
        asm.parts.head + "\n"
      }
      case LABEL(label) => asm.parts.head + "\n"
      case TEXT_INSTRUCTION(text) => "     " + asm.parts.head + "\n"
      case instruction: INSTRUCTION => {
        asm.parts.length match {
          case 1 => {
            s"     ${asm.parts.head} \n"
          }
          case 2 => {
            s"     ${asm.parts.head} ${asm.parts(1)} \n"
          }
          case 3 => {
            s"     ${asm.parts.head} ${asm.parts(1)}, ${asm.parts(2)} \n"
          }
          case 4 => {
            s"     ${asm.parts.head} ${asm.parts(1)}, ${asm.parts(2)}, ${asm.parts(3)} \n"
          }
          case _ => throw new UnsupportedOperationException()
        }
      }
    }
  }

}