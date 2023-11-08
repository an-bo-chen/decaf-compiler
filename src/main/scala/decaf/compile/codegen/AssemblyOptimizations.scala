package decaf.compile.codegen

import decaf.compile.codegen.RegisterDefinitions._

import scala.collection.mutable.ListBuffer

object AssemblyOptimizations {

  private val REMOVE_COMMENTS = true

  def visitProgram(asmProgram: AsmProgram): AsmProgram = {
    asmProgram.copy(
      header = asmProgram.header,
      program = asmProgram.program.map(func => {
        func.copy(
          before = visitAssemblyList(func.before),
          after = visitAssemblyList(func.after),
          basicBlocks = {
            val newBasicBlocks = func.basicBlocks.map(block => {
              block.copy(
                asm = visitAssemblyList(block.asm)
              )
            })

            // Remove useless inter-block jmp instructions
            val buffer: ListBuffer[AsmBasicBlock] = ListBuffer.empty
            var i = 0
            while (i < newBasicBlocks.length - 1) {
              val j = i + 1

              val lastInstruction = newBasicBlocks(i).asm.last
              val labelOfFirst = newBasicBlocks(j).asm.head

              //noinspection TypeCheckCanBeMatch
              if (lastInstruction.isInstanceOf[JMP] && labelOfFirst.isInstanceOf[LABEL]) {
                if (lastInstruction.asInstanceOf[JMP].location.locationStr
                  == labelOfFirst.asInstanceOf[LABEL].label.replace(":", "")) {
                    buffer.append(newBasicBlocks(i).copy(
                      asm = newBasicBlocks(i).asm.dropRight(1)
                    ))
                } else {
                  buffer.append(newBasicBlocks(i))
                }
              } else {
                buffer.append(newBasicBlocks(i))
              }
              i += 1
            }
            buffer.append(newBasicBlocks.last)
            buffer.toList
          }
        )
      })
    )
  }

  private def visitAssemblyList(asm: List[Asm]): List[Asm] = {
    var asmOptim = asm
    if (REMOVE_COMMENTS){
      asmOptim = asmOptim.filterNot(x => x.isInstanceOf[COMMENT])
    }

    asmOptim = performEnterLeave(asmOptim)
    asmOptim = performSameLocationMove(asmOptim)
    asmOptim = performCircularMoves(asmOptim)
    asmOptim = performRemoveUselessMath(asmOptim)
    asmOptim = performChainedMoves(asmOptim)
    return asmOptim
  }

  private def performRemoveUselessMath(asm: List[Asm]): List[Asm] = {
    // Stil noticing some +0, -0, etc. can remove at this point
    val buffer: ListBuffer[Asm] = ListBuffer.empty

    asm.foreach {
      case asm@ADDQ(left, right) => {
        if (!(left.isInstanceOf[IMM] && left.asInstanceOf[IMM].v == 0L)){
          buffer.append(asm)
        }
      }
      case asm@SUBQ(left, right) => {
        if (!(left.isInstanceOf[IMM] && left.asInstanceOf[IMM].v == 0L)) {
          buffer.append(asm)
        }
      }
      case x => buffer.append(x)
    }
    buffer.toList
  }


  private def performEnterLeave(asm: List[Asm]): List[Asm] = {
    // Turns enter and leave instructions into cheaper instructions
    val buffer: ListBuffer[Asm] = ListBuffer.empty

    asm.foreach {
      case ENTER(allocateBytes, offset) => {
        buffer.append(
          PUSH(RBP),
          MOVQ(RSP, RBP)
        )
        if (allocateBytes.v != 0L) {
          buffer.append(SUBQ(allocateBytes, RSP))
        }
      }
      case LEAVE() => {
        buffer.append(
          MOVQ(RBP, RSP),
          POP(RBP)
        )
      }
      case x => buffer.append(x)
    }
    buffer.toList
  }

  private def performSameLocationMove(asm: List[Asm]): List[Asm] = {
    // Examples include:
    // movq %r9, %r9
    // movq -8(%rbp), -8(%rbp)

    val buffer: ListBuffer[Asm] = ListBuffer.empty

    asm.foreach {
      case MOVQ(from, to) => {
        if (from != to) {
          buffer.append(MOVQ(from, to))
        }
      }
      case x => buffer.append(x)
    }
    buffer.toList
  }

  private def performCircularMoves(asm: List[Asm]): List[Asm] = {
    // movq %r10, -32(%rbp)
    // movq -32(%rbp), %r10
    val buffer: ListBuffer[Asm] = ListBuffer.empty
    val bannedIndices: ListBuffer[Long] = ListBuffer.empty

    var i = 0
    while (i < asm.length - 1){
      //noinspection TypeCheckCanBeMatch
      if (asm(i).isInstanceOf[MOVQ] && asm(i + 1).isInstanceOf[MOVQ]){
        val curIns = asm(i).asInstanceOf[MOVQ]
        val nextIns = asm(i + 1).asInstanceOf[MOVQ]

        if (curIns.from.isInstanceOf[MEMLOC] && nextIns.from.isInstanceOf[MEMLOC]){
          if (curIns.from == nextIns.to && curIns.to == nextIns.from){
            if (curIns.to == R10){
              bannedIndices.append(i)
            }
            bannedIndices.append(i+1)
            i += 2
          } else {
            i += 1
          }
        } else {
          i += 1
        }
      } else {
        i += 1
      }
    }

    for (x <- asm.indices) {
      if (!bannedIndices.contains(x)){
        buffer.append(asm(x))
      }
    }
    buffer.toList
  }

  private def performChainedMoves(asm: List[Asm]): List[Asm] = {
    /*
    movq %r10, %rcx
    addq %r8, %r10
    movq %r10, %rcx
    addq $1, %r10
    movq %r10, %rcx

     */

    val buffer: ListBuffer[Asm] = ListBuffer.empty
    val bannedIndices: ListBuffer[Long] = ListBuffer.empty

    var i = 0
    while (i < asm.length - 2) {
      //noinspection TypeCheckCanBeMatch
      if (asm(i).isInstanceOf[MOVQ] && (asm(i + 1).isInstanceOf[ADDQ] || asm(i + 1).isInstanceOf[SUBQ]) && asm(i + 2).isInstanceOf[MOVQ]) {
        val curIns = asm(i).asInstanceOf[MOVQ]
        val nextIns = asm(i + 1).asInstanceOf[INSTRUCTION]
        val thirdIns = asm(i + 2).asInstanceOf[MOVQ]

        if (curIns.from.isInstanceOf[REG] && nextIns.values(0).isInstanceOf[REG] && thirdIns.from.isInstanceOf[REG]) {
          if (curIns.from == nextIns.values(1) && curIns.to != nextIns.values(0) && nextIns.values(1) == thirdIns.from && curIns.to == thirdIns.to) {
            bannedIndices.append(i)
            i += 2
          } else {
            i += 1
          }
        } else {
          i += 1
        }
      } else {
        i += 1
      }
    }

    for (x <- asm.indices) {
      if (!bannedIndices.contains(x)) {
        buffer.append(asm(x))
      }
    }
    buffer.toList
  }

}

