package decaf.compile.codegen.register

import decaf.compile._
import decaf.compile.cfg._
import decaf.compile.codegen.RegisterDefinitions._
import decaf.compile.codegen._
import decaf.compile.reg_alloc.RegVisitor
import decaf.compile.reg_alloc.types.RealRegister

import scala.collection.mutable

object NullRegisterAllocator {

  private var curAlignment: Long = 1L

  def visitProgram(cfgProgram: CfgProgram): (mutable.HashMap[String, CgSymbol], RegProgram, Map[String, (Set[RealRegister], Map[CfgVariable, RealRegister], Set[CfgVariable])]) = {
    val (regProgram, funcInfo) = RegVisitor.visitProgram(cfgProgram, Some(Set.empty[REG]))
    curAlignment = 1L
    val assignments : mutable.HashMap[String, CgSymbol] = mutable.HashMap.empty
    cfgProgram.functions.foreach(func => {
      func.parameters.foreach(x => {
        val name = cfgVariableToScopedIdentifier(x)
        val offset = curAlignment * -8
        val loc = STACKLOC(offset, RBP)
        assignments.put(name, CgSymbol(loc, SymbolType.REGULAR, offset))
        curAlignment += 1L
      })

      func.scope.basicBlocks.foreach(x => {
        x._2.fieldDecls.foreach {
          case CfgArrayFieldDecl(variable, length) => {
            val name = cfgVariableToScopedIdentifier(variable)
            val offset = curAlignment * -8
            val loc = STACKLOC(offset, RBP)
            assignments.put(name, CgSymbol(loc, SymbolType.LOCAL_ARRAY, offset))
            curAlignment += (length)
          }
          case _ =>
        }
      })
    })

    funcInfo.foreach(pair => {
      val (funcName, info) = pair
      val (_, varToReg, spilled) = {info}

      spilled.foreach(x => {
        val name = cfgVariableToScopedIdentifier(x)

        if (!assignments.contains(name)){
          val offset = curAlignment * -8
          val loc = STACKLOC(offset, RBP)
          assignments.put(name, CgSymbol(loc, SymbolType.REGULAR, offset))
          curAlignment += 1L
        }
      })
    })

    (assignments, regProgram, funcInfo)
  }

  private def cfgVariableToScopedIdentifier(v: CfgVariable): String = {
    s"${v.identifier}_${v.number}"
  }

}


