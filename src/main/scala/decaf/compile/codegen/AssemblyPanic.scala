package decaf.compile.codegen

object AssemblyPanic {

  val PANIC_CODE_MAC: String =
    """
      |.data
      |_.falloff_msg:
      |    .string "Execution fell off of method\n"
      |    .align 16
      |_.bounds_msg:
      |    .string "Array access out of bounds\n"
      |    .align 16
      |.text
      |_.falloff_panic:
      |    leaq _.falloff_msg(%rip), %rdi
      |    xorq %rax, %rax
      |    call _printf
      |    movl $0x2000001, %eax
      |    movl $-2, %edi
      |    syscall
      |_.bounds_panic:
      |    leaq _.bounds_msg(%rip), %rdi
      |    xorq %rax, %rax
      |    call _printf
      |    movl $0x2000001, %eax
      |    movl $-1, %edi
      |    syscall
      |""".stripMargin

  val PANIC_CODE_LINUX: String =
    """
      |.data
      |_.falloff_msg:
      |    .string "Execution fell off of method\n"
      |    .align 16
      |_.bounds_msg:
      |    .string "Array access out of bounds\n"
      |    .align 16
      |.text
      |_.falloff_panic:
      |    leaq _.falloff_msg(%rip), %rdi
      |    xorq %rax, %rax
      |    call printf
      |    movq $60, %rax
      |    movq $-2, %rdi
      |    syscall
      |_.bounds_panic:
      |    leaq _.bounds_msg(%rip), %rdi
      |    xorq %rax, %rax
      |    call printf
      |    movq $60, %rax
      |    movq $-1, %rdi
      |    syscall
      |""".stripMargin

}