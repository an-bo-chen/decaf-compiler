package decaf.compile.codegen

import decaf.compile._
import decaf.compile.Compiler.OPTIMIZE_ASSEMBLY
import decaf.compile.cfg._
import decaf.compile.codegen.RegisterDefinitions._
import decaf.compile.ir.{ArithBinOpType, EqualityBinOpType, LogicalBinOpType, RelationalBinOpType, UnaryOpType}
import decaf.compile.reg_alloc.types.{MoveStatement, RealRegister, RegArrayAssignStatement, RegAssignStatement, RegBasicBlock, RegConditionalBlock, RegFunctionCallStatement, RegRegularBlock, RegReturnStatement, RegStatement, WithCfgStatement}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object AssemblyReprVisitor {

  private var isMac = false

  private val variableLocations: mutable.HashMap[String, CgSymbol] = mutable.HashMap.empty
  private val arrayLengths: mutable.HashMap[String, Long] = mutable.HashMap.empty
  private var importedFunctions: List[String] = List.empty
  private var mostNegativeStackLocationSeen = 0L
  private var curFunctionIdentifier: String = ""

  // Used for functions that use callee save registers that must restore them
  // before a ret is executed
  private var currentPreRetAsm: List[Asm] = List.empty
  // registersUsed, varToReg, spilled
  private var functionInfo: Map[String, (Set[RealRegister], Map[CfgVariable, RealRegister], Set[CfgVariable])] = Map.empty

  def reset(): Unit = {
    variableLocations.clear()
    arrayLengths.clear()
    importedFunctions = List.empty
    mostNegativeStackLocationSeen = 0L
    curFunctionIdentifier = ""
    currentPreRetAsm = List.empty
    functionInfo = Map.empty
  }

  def getSetOfRegistersUsedByFunction(identifier: String): List[REG] = {
    functionInfo(identifier)._1.toList.map(x => {
      x.register
    })
  }

  def visitProgram(cfgProgram: RegProgram, isMac: Boolean,
                   registerAssignments: mutable.HashMap[String, CgSymbol],
                   functionInfo: Map[String, (Set[RealRegister], Map[CfgVariable, RealRegister], Set[CfgVariable])]): AsmProgram = {
    AssemblyReprVisitor.isMac = isMac
    AssemblyReprVisitor.functionInfo = functionInfo

    val header: ListBuffer[Asm] = ListBuffer.empty

    // Populate the register assignments
    registerAssignments.foreach(pair => {
      val (name, location) = pair
      variableLocations.put(name, location)
    })

    // Globals
    if (cfgProgram.stringData.nonEmpty || cfgProgram.globalVariables.nonEmpty) {
      // We need to emit a data block with all of the string constants
      header.append(DIRECTIVE(".data"))
    }

    val seenStrings: mutable.Map[String, String] = mutable.HashMap.empty
    cfgProgram.stringData.foreach(tuple2 => {
      val identifier = tuple2._1
      val value = tuple2._2

      if (seenStrings.contains(value)){
        variableLocations.put(identifier, CgSymbol(location = STRLOC(seenStrings(value) + "(%rip)"), symbolType = SymbolType.STRING, stackOffset = 0))
      } else {
        seenStrings.put(value, identifier)
        header.append(LABEL(identifier))
        header.append(DIRECTIVE(".string \"" + value + "\""))
        header.append(DIRECTIVE(".align 16"))
        variableLocations.put(identifier, CgSymbol(location = STRLOC(identifier + "(%rip)"), symbolType = SymbolType.STRING, stackOffset = 0))
      }
    })

    cfgProgram.globalVariables.foreach {
      case CfgGlobalRegFieldDecl(variable, initialValue) => {
        val identifier = cfgVariableToScopedIdentifier(variable)
        header.append(LABEL(identifier))
        header.append(DIRECTIVE(s".quad ${initialValue.toString}"))
        variableLocations.put(identifier, CgSymbol(location = STRLOC(identifier + "(%rip)"), symbolType = SymbolType.REGULAR, stackOffset = 0))
      }
      case CfgGlobalArrayFieldDecl(variable, initialValue, length) => {
        val identifier = cfgVariableToScopedIdentifier(variable)
        header.append(LABEL(identifier))
        if (initialValue.isDefined) {
          initialValue.get.foreach(initLong => {
            header.append(DIRECTIVE(s".quad ${initLong.toString}"))
          });
          arrayLengths.put(identifier, initialValue.get.size)
        } else {
          header.append(DIRECTIVE(s".zero ${(8L * length).toString}"))
          arrayLengths.put(identifier, length)
        }
        variableLocations.put(identifier, CgSymbol(location = STRLOC(identifier), symbolType = SymbolType.GLOBAL_ARRAY, stackOffset = 0))
      }
    }

    importedFunctions = cfgProgram.imports

    // Start emitting instructions
    header.append(DIRECTIVE(".text"))

    // Emit the program starting point
    if (AssemblyReprVisitor.isMac) {
      header.append(DIRECTIVE(".globl _main"))
    } else {
      header.append(DIRECTIVE(".globl main"))
    }

    val functions = cfgProgram.functions.map(func => {
      visitFunction(func)
    })

    AsmProgram(
      header = header.toList,
      program = functions,
      panicCode = if (AssemblyReprVisitor.isMac) AssemblyPanic.PANIC_CODE_MAC else AssemblyPanic.PANIC_CODE_LINUX
    )
  }

  private def updateSymbolUse(cgSymbol: CgSymbol): Unit = {
    cgSymbol.location match {
      case STACKLOC(offset, register) => {
        if (cgSymbol.stackOffset < mostNegativeStackLocationSeen) {
          mostNegativeStackLocationSeen = cgSymbol.stackOffset
        }
      }
      case REG(regName) =>
      case JMPLOC(locationStr) =>
      case STRLOC(locationStr) =>
    }
  }

  private def getGlobalSymbol(identifier: String): CgSymbol = {
    val symbol = variableLocations(identifier)
    updateSymbolUse(symbol)
    symbol
  }

  private def getSymbol(cfgVariable: CfgVariable, locationMap: Map[CfgVariable, RealRegister]): CgSymbol = {
    if (locationMap.contains(cfgVariable)) {
      return CgSymbol(locationMap(cfgVariable).register, SymbolType.REGULAR, 0)
    }
    val symbol = variableLocations(cfgVariableToScopedIdentifier(cfgVariable))
    updateSymbolUse(symbol)
    symbol
  }

  private def getSymbol(cfgVariable: CfgVariable, statement: WithCfgStatement[RealRegister]): CgSymbol = {
    return getSymbol(cfgVariable, statement.locationMap)
  }

  private def visitFunction(cfgFunction: Function[RegBasicBlock]): AsmFunction = {
    val parameterAsm: mutable.Buffer[Asm] = ListBuffer.empty
    mostNegativeStackLocationSeen = 0
    curFunctionIdentifier = cfgFunction.identifier
    currentPreRetAsm = List.empty

    val registersUsed = getSetOfRegistersUsedByFunction(curFunctionIdentifier).filter(x => CALLEE_SAVE_REGISTERS.contains(x))
    if (registersUsed.length % 2 != 0){
      parameterAsm.append(PUSH(IMM(0)))
    }
    registersUsed.foreach(r => {
      parameterAsm.append(PUSH(r))
    })

    val pushAsm: ListBuffer[Asm] = ListBuffer.empty
    val popAsm: ListBuffer[Asm] = ListBuffer.empty

    for (i <- cfgFunction.parameters.indices.reverse) {
      if (i < 6) {
        val registerFrom = PARAMETER_REGISTERS(i)
        val parameter = cfgFunction.parameters(i)
        val varName = cfgVariableToScopedIdentifier(parameter)

        parameterAsm.append(COMMENT(varName))

        if (functionInfo(curFunctionIdentifier)._2.contains(parameter)) {
          val parameterRegister = functionInfo(curFunctionIdentifier)._2(parameter)
          if (isRegister(registerFrom)) {
            pushAsm.append(PUSH(registerFrom))
            popAsm.prepend(POP(parameterRegister.register))
          } else {
            parameterAsm.append(MOVQ(registerFrom, parameterRegister.register))
          }
        } else {
          val cgSymbol = getGlobalSymbol(cfgVariableToScopedIdentifier(parameter))
          if (isRegister(registerFrom) && isRegister(cgSymbol.location)) {
            pushAsm.append(PUSH(registerFrom))
            popAsm.prepend(POP(cgSymbol.location.asInstanceOf[REG]))
          } else {
            parameterAsm.append(MOVQ(registerFrom, cgSymbol.location))
          }
        }
      } else {
        val locationOnStack = STACKLOC(((i - 6 + 2) * 8), RBP)
        val parameter = cfgFunction.parameters(i)
        val varName = cfgVariableToScopedIdentifier(parameter)
        var destination: MEMLOC = null;
        if (functionInfo(curFunctionIdentifier)._2.contains(parameter)) {
          destination = functionInfo(curFunctionIdentifier)._2(parameter).register
        } else {
          destination = getGlobalSymbol(cfgVariableToScopedIdentifier(parameter)).location
        }

        parameterAsm.append(COMMENT(cfgVariableToScopedIdentifier(parameter)))

        pushAsm.append(PUSH(locationOnStack))
        popAsm.prepend(MOVQ(R10, destination))
        popAsm.prepend(POP(R10))

//        parameterAsm.append(MOVQ(locationOnStack, R10))
//        parameterAsm.append(MOVQ(R10, cgSymbol.location))
      }
    }

    parameterAsm ++= pushAsm
    parameterAsm ++= popAsm

    cfgFunction.scope.orderedBlocks.foreach(pair => {
      val (label, basicBlock) = {
        pair
      }
      // Since declarations and assignments are separate now, we should not bother pushing initial values
      basicBlock.fieldDecls.foreach {
        case CfgRegFieldDecl(variable) =>
          // Need to still push the legnth
        case CfgArrayFieldDecl(variable, length) => {
          arrayLengths.put(cfgVariableToScopedIdentifier(variable), length)
        }
      }
    })

    //parameterAsm.append(JMP(JMPLOC(cfgFunction.scope.entry)))

    // Pop all callee save registers
    val popBuffer: ListBuffer[Asm] = ListBuffer.empty
    registersUsed.reverse.foreach(r => {
      popBuffer.append(POP(r))
    })
    if (registersUsed.length % 2 != 0) {
      popBuffer.append(ADDQ(IMM(8), RSP))
    }
    currentPreRetAsm = popBuffer.toList

    val basicBlocks = visitScope(cfgFunction.scope, cfgFunction.returnsValue, cfgFunction.identifier == "main")
    val methodLabel = if (!isMac) cfgFunction.identifier else "_" + cfgFunction.identifier

    val before =
      if (mostNegativeStackLocationSeen % 16 != 0) ENTER(IMM(Math.abs(mostNegativeStackLocationSeen - 8)), IMM(0))
      else ENTER(IMM(Math.abs(mostNegativeStackLocationSeen)), IMM(0))

    AsmFunction(
      identifier = methodLabel,
      before = List(before) ::: parameterAsm.toList,
      after = List(
        JMP(JMPLOC("_.falloff_panic"))
      ),
      basicBlocks = basicBlocks
    )
  }

  private def visitScope(cfgScope: Scope[RegBasicBlock], returnsValue: Boolean, isMain: Boolean): List[AsmBasicBlock] = {
    val allBasicBlocks: ListBuffer[AsmBasicBlock] = ListBuffer.empty

    cfgScope.orderedBlocks.foreach(pair => {
      val (label, basicBlock) = pair
      val totalInstructions: ListBuffer[Asm] = ListBuffer.empty

      visitBasicBlock(label, basicBlock, isMain).foreach(asm => {
        totalInstructions.append(asm)
      })

      basicBlock match {
        case RegRegularBlock(fieldDecls, statements) => {
          if (label == cfgScope.exit) {
            // If we are in a void statement, emit a ret. Otherwise, you must explicitly emit a ret
            // using a return expression. This is so we can get fallthrough to the exception handler
            if (!returnsValue) {
              // Emit a leave
              totalInstructions ++= currentPreRetAsm
              totalInstructions.append(
                LEAVE()
              )

              // In case we are in the main function, we have to return 0 at the end
              if (isMain) {
                totalInstructions.append(
                  COMMENT("set return code to 0"),
                  // mov $0, %rax
                  XORQ(RAX, RAX)
                )
              }

              totalInstructions.append(
                RET()
              )
            }
          } else {
            // Emit a jump statement for basic blocks in the middle
            totalInstructions.append(
              JMP(JMPLOC(cfgScope.regCfg(label)))
            )
          }
        }
        case RegConditionalBlock(fieldDecls, statements, withCondition) => {
          withCondition._1 match {
            case condition@CfgVariable(_, _) => {
              val variableName = cfgVariableToScopedIdentifier(condition)
              val cgSymbol = getSymbol(condition, withCondition._2)

              totalInstructions.append(
                MOVQ(cgSymbol.location, R10),
                CMPQ(IMM(1), R10),
                JNE(JMPLOC(cfgScope.falseCfg(label))),
                JE(JMPLOC(cfgScope.trueCfg(label)))
              )
            }
            case CfgImmediate(value) => {
              if (value == 1) {
                totalInstructions.append(JMP(JMPLOC(cfgScope.trueCfg(label))))
              } else {
                totalInstructions.append(JMP(JMPLOC(cfgScope.falseCfg(label))))
              }
            }
            case CfgUnaryOpExpr(op, expr) => {
              op match {
                case UnaryOpType.UMINUS => throw new IllegalStateException()
                case UnaryOpType.NOT => {
                  expr match {
                    case CfgImmediate(value) => {
                      if (value == 1) {
                        totalInstructions.append(JMP(JMPLOC(cfgScope.falseCfg(label))))
                      } else {
                        totalInstructions.append(JMP(JMPLOC(cfgScope.trueCfg(label))))
                      }
                    }
                    case condition@CfgVariable(_, _) => {
                      val variableName = cfgVariableToScopedIdentifier(condition)
                      val cgSymbol = getSymbol(condition, withCondition._2)
                      totalInstructions.append(
                        MOVQ(cgSymbol.location, R10),
                        XORQ(IMM(1), R10),
                        CMPQ(IMM(1), R10),
                        JNE(JMPLOC(cfgScope.falseCfg(label))),
                        JE(JMPLOC(cfgScope.trueCfg(label)))
                      )
                    }
                  }
                }
              }
            }
            case curExpr@CfgBinOpExpr(oldOp, oldLeftExpr, oldRightExpr) => {
              var actualExpr = curExpr
              if (oldLeftExpr.isInstanceOf[CfgImmediate] && oldRightExpr.isInstanceOf[CfgImmediate]) {
                // both immediates, just deal with it
                val leftValue = oldLeftExpr.asInstanceOf[CfgImmediate].value
                val rightValue = oldRightExpr.asInstanceOf[CfgImmediate].value

                var result: Boolean = false

                oldOp match {
                  case opType: ArithBinOpType => throw new IllegalStateException()
                  case opType: LogicalBinOpType => throw new IllegalStateException()
                  case opType: RelationalBinOpType => {
                    opType match {
                      case RelationalBinOpType.LT => {
                        result = leftValue < rightValue
                      }
                      case RelationalBinOpType.LTE => {
                        result = leftValue <= rightValue
                      }
                      case RelationalBinOpType.GT => {
                        result = leftValue > rightValue
                      }
                      case RelationalBinOpType.GTE => {
                        result = leftValue >= rightValue
                      }
                    }
                  }
                  case opType: EqualityBinOpType => {
                    opType match {
                      case EqualityBinOpType.EQ => {
                        result = leftValue == rightValue
                      }
                      case EqualityBinOpType.NEQ => {
                        result = leftValue != rightValue
                      }
                    }
                  }
                }

                if (result) {
                  totalInstructions.append(JMP(JMPLOC(cfgScope.trueCfg(label))))
                } else {
                  totalInstructions.append(JMP(JMPLOC(cfgScope.falseCfg(label))))
                }
              } else {
                if (oldLeftExpr.isInstanceOf[CfgImmediate]) {
                  // Right side is an immediate, but we can't do that, we need to fix it
                  oldOp match {
                    case opType: ArithBinOpType => throw new IllegalStateException()
                    case opType: LogicalBinOpType => throw new IllegalStateException()
                    case opType: RelationalBinOpType => {
                      opType match {
                        case RelationalBinOpType.LT => {
                          actualExpr = curExpr.copy(
                            leftExpr = curExpr.rightExpr,
                            rightExpr = curExpr.leftExpr,
                            op = RelationalBinOpType.GT
                          )
                        }
                        case RelationalBinOpType.LTE => {
                          actualExpr = curExpr.copy(
                            leftExpr = curExpr.rightExpr,
                            rightExpr = curExpr.leftExpr,
                            op = RelationalBinOpType.GTE
                          )
                        }
                        case RelationalBinOpType.GT => {
                          actualExpr = curExpr.copy(
                            leftExpr = curExpr.rightExpr,
                            rightExpr = curExpr.leftExpr,
                            op = RelationalBinOpType.LT
                          )
                        }
                        case RelationalBinOpType.GTE => {
                          actualExpr = curExpr.copy(
                            leftExpr = curExpr.rightExpr,
                            rightExpr = curExpr.leftExpr,
                            op = RelationalBinOpType.LTE
                          )
                        }
                      }
                    }
                    case opType: EqualityBinOpType => {
                      opType match {
                        case EqualityBinOpType.EQ => {
                          actualExpr = curExpr.copy(
                            leftExpr = curExpr.rightExpr,
                            rightExpr = curExpr.leftExpr
                          )
                        }
                        case EqualityBinOpType.NEQ => {
                          actualExpr = curExpr.copy(
                            leftExpr = curExpr.rightExpr,
                            rightExpr = curExpr.leftExpr
                          )
                        }
                      }
                    }
                  }
                }

                var locationLeft: MEMLOC = actualExpr.leftExpr match {
                  case variable@CfgVariable(identifier, number) => {
                    getSymbol(variable, withCondition._2).location
                  }
                  case CfgImmediate(value) => throw new IllegalStateException()
                }
                val locationRight: VALUE = actualExpr.rightExpr match {
                  case variable@CfgVariable(identifier, number) => {
                    getSymbol(variable, withCondition._2).location
                  }
                  case CfgImmediate(value) => {
                    IMM(value)
                  }
                }

                if (!locationLeft.isInstanceOf[REG] && !locationRight.isInstanceOf[REG]) {
                  totalInstructions.append(
                    MOVQ(locationLeft, R10)
                  )
                  locationLeft = R10
                }


                actualExpr.op match {
                  case opType: ArithBinOpType => throw new IllegalStateException()
                  case opType: LogicalBinOpType => throw new IllegalStateException()
                  case opType: RelationalBinOpType => {
                    opType match {
                      case RelationalBinOpType.LT => {
                        totalInstructions.append(
                          COMMENT(f"calculate ${actualExpr.leftExpr.toString} < ${actualExpr.rightExpr.toString}"),
                          CMPQ(locationRight, locationLeft),
                          JL(JMPLOC(cfgScope.trueCfg(label))),
                          JMP(JMPLOC(cfgScope.falseCfg(label)))
                        )
                      }
                      case RelationalBinOpType.LTE => {
                        totalInstructions.append(
                          COMMENT(f"calculate ${actualExpr.leftExpr.toString} <= ${actualExpr.rightExpr.toString}"),
                          CMPQ(locationRight, locationLeft),
                          JLE(JMPLOC(cfgScope.trueCfg(label))),
                          JMP(JMPLOC(cfgScope.falseCfg(label)))
                        )
                      }
                      case RelationalBinOpType.GT => {
                        totalInstructions.append(
                          COMMENT(f"calculate ${actualExpr.leftExpr.toString} > ${actualExpr.rightExpr.toString}"),
                          CMPQ(locationRight, locationLeft),
                          JG(JMPLOC(cfgScope.trueCfg(label))),
                          JMP(JMPLOC(cfgScope.falseCfg(label)))
                        )
                      }
                      case RelationalBinOpType.GTE => {
                        totalInstructions.append(
                          COMMENT(f"calculate ${actualExpr.leftExpr.toString} >= ${actualExpr.rightExpr.toString}"),
                          CMPQ(locationRight, locationLeft),
                          JGE(JMPLOC(cfgScope.trueCfg(label))),
                          JMP(JMPLOC(cfgScope.falseCfg(label)))
                        )
                      }
                    }
                  }
                  case opType: EqualityBinOpType => {
                    opType match {
                      case EqualityBinOpType.EQ => {
                        totalInstructions.append(
                          COMMENT(f"calculate ${actualExpr.leftExpr.toString} == ${actualExpr.rightExpr.toString}"),
                          CMPQ(locationRight, locationLeft),
                          JE(JMPLOC(cfgScope.trueCfg(label))),
                          JMP(JMPLOC(cfgScope.falseCfg(label)))
                        )
                      }
                      case EqualityBinOpType.NEQ => {
                        totalInstructions.append(
                          COMMENT(f"calculate ${actualExpr.leftExpr.toString} != ${actualExpr.rightExpr.toString}"),
                          CMPQ(locationRight, locationLeft),
                          JNE(JMPLOC(cfgScope.trueCfg(label))),
                          JMP(JMPLOC(cfgScope.falseCfg(label)))
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

      allBasicBlocks.append(AsmBasicBlock(
        totalInstructions.toList
      ))
    })
    allBasicBlocks.toList
  }

  private def visitBasicBlock(label: String, cfgBasicBlock: RegBasicBlock, inMain: Boolean): List[Asm] = {
    // Field declarations
    val basicBlockPreInstructions: ListBuffer[Asm] = ListBuffer.empty
    basicBlockPreInstructions.append(LABEL(label))

    // Doesn't matter what type of block, this just outputs all of the instructions within it
    val instructions: List[Asm] = {
      val asm: ListBuffer[Asm] = ListBuffer.empty
      cfgBasicBlock.statements.foreach(statement => {
        val statementResult = visitStatement(statement, inMain)
        // Add all the assembly from each statement
        statementResult.foreach(x => asm.append(x))
      })
      asm.toList
    }
    basicBlockPreInstructions.toList ::: instructions
  }

  private def visitStatement(cfgStatement: RegStatement[RealRegister], inMain: Boolean): List[Asm] = {
    cfgStatement match {
      case MoveStatement(from, to) => throw new UnsupportedOperationException("Not yet implemented")
      case withRegStatement: WithCfgStatement[RealRegister] => {
        withRegStatement.origStatement match {
          case statement: CfgAssignStatement => {
            statement match {
              case CfgRegAssignStatement(to, value) => {
                // Right hand side must already be defined or be an immediate
                val rightEval: (List[Asm], VALUE) = visitExpression(value, withRegStatement, to)

                if (to.index.isDefined) {
                  // NOTE: Writing to arrays

                  val arrayCgSymbol: CgSymbol = getSymbol(to.variable, withRegStatement)
                  val indexLocation = to.index.get match {
                    case variable@CfgVariable(identifier, number) => {
                      val symbol = getSymbol(variable, withRegStatement)
                      symbol.location
                    }
                    case CfgImmediate(value) => {
                      IMM(value)
                    }
                  }


                  if (indexLocation.isInstanceOf[IMM]) {
                    val indexImmediate = indexLocation.asInstanceOf[IMM].v
                    if (0L <= indexImmediate && indexImmediate < arrayLengths(cfgVariableToScopedIdentifier(to.variable))){
                      arrayCgSymbol.symbolType match {
                        case SymbolType.LOCAL_ARRAY => {
                          val location = getSymbol(to.variable, withRegStatement)
                          val arrayLength = arrayLengths(cfgVariableToScopedIdentifier(to.variable))
                          val actualOffset = location.stackOffset + (indexImmediate * 8L) + ((arrayLength - 1) * (-8L))

                          rightEval._1 ::: List(
                            COMMENT(s"${cfgVariableToScopedIdentifier(to.variable)}[${to.index.get}] = value"),
//                            LEAQ(STRLOC(getSymbol(to.variable, withRegStatement).location.toString), R11),
                            MOVQ(rightEval._2, STACKLOC(actualOffset, RBP))
                          )
                        }
                        case SymbolType.GLOBAL_ARRAY => {
                          rightEval._1 ::: List(
                            COMMENT(s"${cfgVariableToScopedIdentifier(to.variable)}[${to.index.get}] = value"),
                            LEAQ(STRLOC(getSymbol(to.variable, withRegStatement).location.toString + "(%rip)"), R11),
                            MOVQ(rightEval._2, STRLOC((indexImmediate * 8L) + "(%r11)"))
                          )
                        }
                        case SymbolType.STRING => throw new IllegalStateException()
                        case SymbolType.REGULAR => throw new IllegalStateException()
                      }
                    } else {
                      rightEval._1 ::: List(JMP(JMPLOC("_.bounds_panic")))
                    }
                  } else {
                    arrayCgSymbol.symbolType match {
                      case SymbolType.LOCAL_ARRAY => {
                        var location = getSymbol(to.variable, withRegStatement).location.asInstanceOf[STACKLOC]
                        val arrayLength = arrayLengths(cfgVariableToScopedIdentifier(to.variable))
                        location = location.copy(
                          offset = location.offset + ((arrayLength - 1) * (-8L))
                        )

                        if (isRegister(indexLocation)) {
                          rightEval._1 ::: List(
                            COMMENT(s"${cfgVariableToScopedIdentifier(to.variable)}[${to.index.get}] = value"),
                            CMPQ(IMM(arrayLength), indexLocation.asInstanceOf[REG]),
                            JAE(JMPLOC("_.bounds_panic")),

                            LEAQ(location, R11),
                            MOVQ(rightEval._2, STRLOC("(%r11, " + indexLocation.asInstanceOf[REG].regName + ", 8)"))
                          )
                        } else {
                          rightEval._1 ::: List(
                            COMMENT(s"${cfgVariableToScopedIdentifier(to.variable)}[${to.index.get}] = value"),
                            PUSH(RAX),
                            MOVQ(indexLocation, R11),
                            CMPQ(IMM(arrayLength), R11),
                            JAE(JMPLOC("_.bounds_panic")),

                            LEAQ(location, RAX),
                            MOVQ(rightEval._2, STRLOC("(%rax, %r11, 8)")),
                            POP(RAX)
                          )
                        }
                      }
                      case SymbolType.GLOBAL_ARRAY => {
                        // We are assuming that the rightEval will go into %rcx, so we will use %rdx for this
                        // since we need three registers to complete this operation

                        if (isRegister(indexLocation)) {
                          rightEval._1 ::: List(
                            COMMENT(s"${cfgVariableToScopedIdentifier(to.variable)}[${to.index.get}] = value"),
                            CMPQ(IMM(arrayLengths(cfgVariableToScopedIdentifier(to.variable))), indexLocation.asInstanceOf[REG]),
                            JAE(JMPLOC("_.bounds_panic")),
                            LEAQ(STRLOC(getSymbol(to.variable, withRegStatement).location.toString + "(%rip)"), R11),
                            MOVQ(rightEval._2, STRLOC("(%r11, " + indexLocation.asInstanceOf[REG].regName + ", 8)"))
                          )
                        } else {
                          rightEval._1 ::: List(
                            COMMENT(s"${cfgVariableToScopedIdentifier(to.variable)}[${to.index.get}] = value"),
                            PUSH(RAX),
                            MOVQ(indexLocation, R11),
                            CMPQ(IMM(arrayLengths(cfgVariableToScopedIdentifier(to.variable))), R11),
                            JAE(JMPLOC("_.bounds_panic")),
                            LEAQ(STRLOC(getSymbol(to.variable, withRegStatement).location.toString + "(%rip)"), RAX),
                            MOVQ(rightEval._2, STRLOC("(%rax, %r11, 8)")),
                            POP(RAX)
                          )
                        }
                      }
                      case SymbolType.STRING => throw new IllegalStateException()
                      case SymbolType.REGULAR => throw new IllegalStateException()
                    }
                  }
                } else {
                  // Just moving a regular variable
                  val leftLocation = getSymbol(to.variable, withRegStatement)
                  rightEval._1 ::: List(
                    COMMENT(cfgVariableToScopedIdentifier(to.variable) + " = " + value),
                    // Assign right hand side to the left side location, left hand side must be a register
                    MOVQ(rightEval._2, leftLocation.location)
                  )
                }
              }
              case CfgArrayAssignStatement(to, value) => {
                // This MUST be a local array, and must be dynamically generated by the CFG
                if (to.index.isDefined) throw new IllegalStateException();
                val location = to.variable
                val stackLocation = getSymbol(location, withRegStatement)

                val assignAsm: ListBuffer[Asm] = ListBuffer.empty

                var allZeros = true
                value.foreach(x => {
                  if (x != 0L) {
                    allZeros = false
                  }
                })

                // Since arrays are really big and have a length
                val maximalStackOffset = stackLocation.stackOffset + (-8L * value.length)
                if (maximalStackOffset < mostNegativeStackLocationSeen) {
                  mostNegativeStackLocationSeen = maximalStackOffset
                }

                if (!allZeros) {
                  // Assign them using movq if they are not all zeros
                  for (i <- value.indices) {
                    val curValueOffset = stackLocation.stackOffset + (-8L * i)
                    assignAsm.append(
                      MOVQ(IMM(value(value.length - 1 - i)), STACKLOC(curValueOffset, RBP))
                    )
                  }
                } else {
                  // Assign them to all zeros using rep
                  val startStackLocation = stackLocation.stackOffset + (-8L * (value.length - 1))
                  // These unfortunately must be the used registers, so we need to push/pop
                  assignAsm.append(
                    PUSH(RDI),
                    PUSH(RAX),
                    PUSH(RCX),
                    LEAQ(STACKLOC(startStackLocation, RBP), RDI),
                    XORQ(RAX, RAX),
                    MOVQ(IMM(value.length), RCX),
                    TEXT_INSTRUCTION("rep stosq"),
                    POP(RCX),
                    POP(RAX),
                    POP(RDI)
                  )
                }

                assignAsm.toList
              }
            }
          }
          case CfgFunctionCallStatement(methodName, parameters) => {
            visitMethodCall(methodName, parameters, None, withRegStatement.locationMap)
          }
          case CfgReturnStatement(value) => {
            if (value.isDefined) {
              val cgValue: CfgValue = value.get

              val returnLocation = cgValue match {
                case CfgImmediate(value) => {
                  IMM(value)
                }
                case variable@CfgVariable(identifier, number) => {
                  getSymbol(variable, withRegStatement).location
                }
              }

              List(
                MOVQ(returnLocation, RAX)
              ) ::: currentPreRetAsm ::: List(
                LEAVE(),
                RET()
              )
            } else {
              val retAsm: ListBuffer[Asm] = ListBuffer.empty
              retAsm ++= currentPreRetAsm
              retAsm.append(LEAVE())

              if (inMain) {
                // For main where we need to emit a return code, move a $0 into %rax
                // before emitting the ret
                retAsm.append(COMMENT("set return code to 0"))
                retAsm.append(XORQ(RAX, RAX))
              }
              retAsm.append(RET())
              retAsm.toList
            }
          }
        }

      }
    }
  }

  private def findStackLocationForParam(inputReg: VALUE, pushAsm: ListBuffer[Asm], otherLocationsToPush: Long): VALUE = {
    // If other locations is 1, offset 8 bytes
    if (!inputReg.isInstanceOf[REG]) {
      return inputReg
    }

    val startingOffset = otherLocationsToPush * 8L

    var forwardReg = 0
    for (i <- pushAsm.indices.reverse) {
      if (inputReg == pushAsm(i).asInstanceOf[PUSH].value.asInstanceOf[REG]){
        return STACKLOC(startingOffset + forwardReg * 8L, RSP)
      }
      forwardReg += 1
    }
    return inputReg
  }

  private def visitMethodCall(methodName: String, parameters: List[CfgFunctionCallParam], write: Option[MEMLOC], locationMap: Map[CfgVariable, RealRegister]): List[Asm] = {
    val parameterAsm: ListBuffer[Asm] = ListBuffer.empty
    val pushAsm: ListBuffer[Asm] = ListBuffer.empty
    val popAsm: ListBuffer[Asm] = ListBuffer.empty
    var otherLocationsToPush = 0L

    pushAsm.append(PUSH(RAX))
    val usedRegs = getSetOfRegistersUsedByFunction(curFunctionIdentifier).filter(x => CALLER_SAVE_REGISTERS.contains(x))
    usedRegs.foreach(reg => {
      pushAsm.append(PUSH(reg))
    })

    for (i <- parameters.indices.reverse) {
      val parameter = parameters(i)
      parameter match {
        case CfgFunctionCallValueParam(param) => {
          param match {
            case CfgImmediate(value) => {
              if (i < 6){
                if (!usedRegs.contains(PARAMETER_REGISTERS(i)) && CALLER_SAVE_REGISTERS.contains(PARAMETER_REGISTERS(i))) {
                  pushAsm.append(PUSH(PARAMETER_REGISTERS(i)))
                }
              }
            }
            case variable@CfgVariable(identifier, number) => {
              if (i < 6) {
                if (!usedRegs.contains(PARAMETER_REGISTERS(i)) && CALLER_SAVE_REGISTERS.contains(PARAMETER_REGISTERS(i))) {
                  pushAsm.append(PUSH(PARAMETER_REGISTERS(i)))
                }
              }
            }
          }
        }
        case CfgFunctionCallStringParam(param) => {
          if (i < 6){
            if (!usedRegs.contains(PARAMETER_REGISTERS(i)) && CALLER_SAVE_REGISTERS.contains(PARAMETER_REGISTERS(i))) {
              pushAsm.append(PUSH(PARAMETER_REGISTERS(i)))
            }
          }
        }
      }
    }

    for (i <- parameters.indices.reverse) {
      val parameter = parameters(i)
      parameter match {
        case CfgFunctionCallValueParam(param) => {
          param match {
            case variable@CfgVariable(identifier, number) => {
              // Must be regular symbol
              val paramLocation = param match {
                case variable@CfgVariable(identifier, number) => {
                  getSymbol(variable, locationMap).location
                }
                case CfgImmediate(value) => {
                  IMM(value)
                }
              }
              parameterAsm.append(COMMENT("move " + cfgVariableToScopedIdentifier(variable) + " to parameter register " + i))
              if (i >= 6) {
                parameterAsm.append(PUSH(paramLocation))
                otherLocationsToPush += 1
              } else {
                if (paramLocation != PARAMETER_REGISTERS(i)) {
                  parameterAsm.append(MOVQ(findStackLocationForParam(paramLocation, pushAsm, otherLocationsToPush),
                    PARAMETER_REGISTERS(i)))
                }
              }
            }
            case CfgImmediate(value) => {
              parameterAsm.append(COMMENT("move " + value + " to parameter register " + i))
              if (i >= 6) {
                parameterAsm.append(PUSH(IMM(value)))
                otherLocationsToPush += 1
              } else {
                parameterAsm.append(MOVQ(IMM(value), PARAMETER_REGISTERS(i)))
              }
            }
          }
        }
        case CfgFunctionCallStringParam(param) => {
          if (i >= 6) {
            parameterAsm.append(LEAQ(getGlobalSymbol(param).location, R10))
            parameterAsm.append(PUSH(R10))
            otherLocationsToPush += 1
          } else {
            parameterAsm.append(LEAQ(getGlobalSymbol(param).location, PARAMETER_REGISTERS(i)))
          }
        }
      }
    }

    if ((pushAsm.length + otherLocationsToPush) % 2 != 0) {
      pushAsm.prepend(PUSH(IMM(0)))
    }

    var stackLocationsToPush = 0L
    pushAsm.foreach {
      case instruction: INSTRUCTION => {
        instruction match {
          case LEAQ(from, right) => // ignore
          case PUSH(value) => {
            value match {
              case IMM(v) => {
                stackLocationsToPush += 1L
              }
              case memloc: MEMLOC => {
                memloc match {
                  case reg@REG(regName) => {
                    if (stackLocationsToPush > 0L) {
                      popAsm.prepend(ADDQ(IMM(stackLocationsToPush * 8L), RSP))
                      stackLocationsToPush = 0L
                    }
                    popAsm.prepend(POP(reg))
                  }
                  case _ => throw new IllegalStateException()
                }
              }
            }
          }
          case _ => throw new IllegalStateException()
        }
      }
      case _ => throw new IllegalStateException()
    }
    if (stackLocationsToPush > 0L) {
      popAsm.prepend(ADDQ(IMM(stackLocationsToPush * 8L), RSP))
      stackLocationsToPush = 0L
    }
    if(otherLocationsToPush > 0L){
      popAsm.prepend(ADDQ(IMM(otherLocationsToPush * 8L), RSP))
    }

    if (importedFunctions.contains(methodName)) {
      // movq $0, %rax
      // This must be done before calling into C
      parameterAsm.append(
        COMMENT("external call %rax = 0"),
        XORQ(RAX, RAX)
      )
    }

    val callLabel = if (isMac) "_" + methodName else methodName

    val writeAsm = if (write.isDefined) List(
      MOVQ(RAX, write.get)
    ) else List()

    List(
      COMMENT("call to " + methodName)
    ) ::: pushAsm.toList ::: parameterAsm.toList :::
      List(
        CALL(JMPLOC(callLabel))) :::
      writeAsm ::: popAsm.toList
  }

  private def visitExpression(cfgExpression: CfgExpression, withStatement: WithCfgStatement[RealRegister], to: CfgLocation): (List[Asm], VALUE) = {
    cfgExpression match {
      case CfgArrayReadExpr(arrVariable, index) => {
        index match {
          case indexVariable@CfgVariable(identifier, number) => {
            val cgSymbol: CgSymbol = getSymbol(arrVariable, withStatement)
            val locationTo = getMemLocFromValue(to.variable, withStatement)

            cgSymbol.symbolType match {
              // NOTE: READING FROM ARRAYS
              case SymbolType.LOCAL_ARRAY => {
                var location = getSymbol(arrVariable, withStatement).location.asInstanceOf[STACKLOC]
                val arrayLength = arrayLengths(cfgVariableToScopedIdentifier(arrVariable))
                location = location.copy(
                  offset = location.offset + ((arrayLength - 1) * (-8L))
                )

                if (isRegister(locationTo)) {
                  val indexLocation = getSymbol(indexVariable, withStatement).location
                  if (isRegister(indexLocation)){
                    (List(
                      COMMENT(s"load from local array ${cfgVariableToScopedIdentifier(arrVariable)}[${cfgVariableToScopedIdentifier(indexVariable)}]"),
                      CMPQ(IMM(arrayLength), indexLocation),
                      JAE(JMPLOC("_.bounds_panic")),

                      LEAQ(location, R11),
                      MOVQ(STRLOC("(%r11, " + indexLocation.asInstanceOf[REG].regName + ", 8)"), locationTo.asInstanceOf[REG])
                    ),
                      locationTo
                    )
                  } else {
                    (List(
                      COMMENT(s"load from local array ${cfgVariableToScopedIdentifier(arrVariable)}[${cfgVariableToScopedIdentifier(indexVariable)}]"),
                      MOVQ(indexLocation, R10),
                      CMPQ(IMM(arrayLength), R10),
                      JAE(JMPLOC("_.bounds_panic")),

                      LEAQ(location, R11),
                      MOVQ(STRLOC("(%r11, %r10, 8)"), locationTo.asInstanceOf[REG])
                    ),
                      locationTo
                    )
                  }
                } else {
                  val indexLocation = getSymbol(indexVariable, withStatement).location
                  if (isRegister(indexLocation)) {
                    (List(
                      COMMENT(s"load from local array ${cfgVariableToScopedIdentifier(arrVariable)}[${cfgVariableToScopedIdentifier(indexVariable)}]"),
                      CMPQ(IMM(arrayLength), indexLocation),
                      JAE(JMPLOC("_.bounds_panic")),

                      LEAQ(location, R11),
                      MOVQ(STRLOC("(%r11, " + indexLocation.asInstanceOf[REG].regName + ", 8)"), R10)
                    ),
                      R10
                    )
                  } else {
                    (List(
                      COMMENT(s"load from local array ${cfgVariableToScopedIdentifier(arrVariable)}[${cfgVariableToScopedIdentifier(indexVariable)}]"),
                      MOVQ(getSymbol(indexVariable, withStatement).location, R10),
                      CMPQ(IMM(arrayLength), R10),
                      JAE(JMPLOC("_.bounds_panic")),

                      LEAQ(location, R11),
                      MOVQ(STRLOC("(%r11, %r10, 8)"), R10)
                    ),
                      R10
                    )
                  }
                }
              }
              case SymbolType.GLOBAL_ARRAY => (
                if (isRegister(locationTo)) {
                  val indexLocation = getSymbol(indexVariable, withStatement).location
                  if (isRegister(indexLocation)) {
                    (
                      List(
                        COMMENT(s"load from global array ${cfgVariableToScopedIdentifier(arrVariable)}[${cfgVariableToScopedIdentifier(indexVariable)}]"),
                        CMPQ(IMM(arrayLengths(cfgVariableToScopedIdentifier(arrVariable))), indexLocation.asInstanceOf[REG]),
                        JAE(JMPLOC("_.bounds_panic")),
                        LEAQ(STRLOC(getSymbol(arrVariable, withStatement).location + "(%rip)"), R11),
                        MOVQ(STRLOC("(%r11, " + indexLocation.asInstanceOf[REG].regName + ", 8)"), locationTo.asInstanceOf[REG])
                      ),
                      locationTo
                    )
                  } else {
                    (
                      List(
                        COMMENT(s"load from global array ${cfgVariableToScopedIdentifier(arrVariable)}[${cfgVariableToScopedIdentifier(indexVariable)}]"),
                        MOVQ(indexLocation, R10),
                        CMPQ(IMM(arrayLengths(cfgVariableToScopedIdentifier(arrVariable))), R10),
                        JAE(JMPLOC("_.bounds_panic")),
                        LEAQ(STRLOC(getSymbol(arrVariable, withStatement).location + "(%rip)"), R11),
                        MOVQ(STRLOC("(%r11, %r10, 8)"), locationTo.asInstanceOf[REG])
                      ),
                      locationTo
                    )
                  }
                } else {
                  val indexLocation = getSymbol(indexVariable, withStatement).location

                  if (isRegister(indexLocation)) {
                    (List(
                      COMMENT(s"load from global array ${cfgVariableToScopedIdentifier(arrVariable)}[${cfgVariableToScopedIdentifier(indexVariable)}]"),
                      CMPQ(IMM(arrayLengths(cfgVariableToScopedIdentifier(arrVariable))), indexLocation),
                      JAE(JMPLOC("_.bounds_panic")),
                      LEAQ(STRLOC(getSymbol(arrVariable, withStatement).location + "(%rip)"), R11),
                      MOVQ(STRLOC("(%r11, " + indexLocation.asInstanceOf[REG].regName + ", 8)"), R10)
                    ), R10)
                  } else {
                    (List(
                      COMMENT(s"load from global array ${cfgVariableToScopedIdentifier(arrVariable)}[${cfgVariableToScopedIdentifier(indexVariable)}]"),
                      MOVQ(getSymbol(indexVariable, withStatement).location, R10),
                      CMPQ(IMM(arrayLengths(cfgVariableToScopedIdentifier(arrVariable))), R10),
                      JAE(JMPLOC("_.bounds_panic")),
                      LEAQ(STRLOC(getSymbol(arrVariable, withStatement).location + "(%rip)"), R11),
                      MOVQ(STRLOC("(%r11, %r10, 8)"), R10)
                    ), R10)
                  }
                }
              )
              case SymbolType.STRING => throw new IllegalStateException()
              case SymbolType.REGULAR => throw new IllegalStateException()
            }
          }
          case CfgImmediate(indexImmediate) => {
            val cgSymbol: CgSymbol = getSymbol(arrVariable, withStatement)
            val arrayLength: Long = arrayLengths(cfgVariableToScopedIdentifier(arrVariable))
            val locationTo = getMemLocFromValue(to.variable, withStatement)

            cgSymbol.symbolType match {
              case SymbolType.LOCAL_ARRAY => {
                if (0L <= indexImmediate && indexImmediate < arrayLength) {
                  val location = getSymbol(arrVariable, withStatement)
                  val arrayLength = arrayLengths(cfgVariableToScopedIdentifier(arrVariable))
                  val actualOffset = location.stackOffset + (indexImmediate * 8L) + ((arrayLength - 1) * (-8L))

                  if (isRegister(locationTo)) {
                    (List(
                      COMMENT(s"load from local array ${cfgVariableToScopedIdentifier(arrVariable)}[${indexImmediate}]"),
//                      LEAQ(getSymbol(arrVariable, withStatement).location, R11),
                      MOVQ(STACKLOC(actualOffset, RBP), locationTo.asInstanceOf[REG])
                    ), locationTo)
                  } else {
                    (List(
                      COMMENT(s"load from local array ${cfgVariableToScopedIdentifier(arrVariable)}[${indexImmediate}]"),
//                      LEAQ(getSymbol(arrVariable, withStatement).location, R11),
                      MOVQ(STACKLOC(actualOffset, RBP), R10)
                    ), R10)
                  }
                } else {
                  (List(
                    JMP(JMPLOC("_.bounds_panic"))
                  ), R10)
                }
              }
              case SymbolType.GLOBAL_ARRAY => {
                if (0L <= indexImmediate && indexImmediate < arrayLength) {
                  if (isRegister(locationTo)) {
                    (
                      List(
                        COMMENT(s"load from global array ${cfgVariableToScopedIdentifier(arrVariable)}[${indexImmediate}]"),
                        LEAQ(STRLOC(getSymbol(arrVariable, withStatement).location + "(%rip)"), R11),
                        MOVQ(STRLOC((indexImmediate * 8L) + "(%r11)"), locationTo.asInstanceOf[REG])
                      ),
                      locationTo
                    )
                  } else {
                    (
                      List(
                        COMMENT(s"load from global array ${cfgVariableToScopedIdentifier(arrVariable)}[${indexImmediate}]"),
                        LEAQ(STRLOC(getSymbol(arrVariable, withStatement).location + "(%rip)"), R11),
                        MOVQ(STRLOC((indexImmediate * 8L) + "(%r11)"), R10)
                      ),
                      R10
                    )
                  }
                } else {
                  (List(
                    JMP(JMPLOC("_.bounds_panic"))
                  ), R10)
                }
              }
              case SymbolType.STRING => throw new IllegalStateException()
              case SymbolType.REGULAR => throw new IllegalStateException()
            }
          }
        }
      }
      case value: CfgValue => {
        value match {
          case variable@CfgVariable(identifier, number) => {
            getSymbol(variable, withStatement).symbolType match {
              case SymbolType.REGULAR => {
                val location = getSymbol(variable, withStatement).location
                if (isRegister(location)){
                  (List.empty, location)
                }else {
                  (List(MOVQ(location, R10)), R10)
                }
              }
              case SymbolType.GLOBAL_ARRAY => {
                (List(LEAQ(STRLOC(getSymbol(variable, withStatement).location + "(%rip)"), R10)), R10)
              }
              case SymbolType.LOCAL_ARRAY => {
                (List(LEAQ(getSymbol(variable, withStatement).location, R10)), R10)
              }
              case SymbolType.STRING => throw new IllegalStateException()
            }
          }
          case CfgImmediate(immediate) => (List.empty, IMM(immediate))
        }
      }
      case CfgBinOpExpr(op, leftExpr, rightExpr) => {
        val locationLeft = getMemLocFromValue(leftExpr, withStatement)
        val locationRight = getMemLocFromValue(rightExpr, withStatement)

        val locationTo = getMemLocFromValue(to.variable, withStatement)

        op match {
          case opType: ArithBinOpType => {
            opType match {
              case ArithBinOpType.ADD => {
                if (to.variable == leftExpr && isRegister(locationTo)) {
                  (List(
                    COMMENT(f"calculate ${leftExpr.toString} + ${rightExpr.toString}"),
                    ADDQ(locationRight, locationTo.asInstanceOf[REG])
                  ), locationTo)
                } else {
                  (
                    List(
                      COMMENT(f"calculate ${leftExpr.toString} + ${rightExpr.toString}"),
                      MOVQ(locationLeft, R10),
                      ADDQ(locationRight, R10)
                    ),
                    R10
                  )
                }
              }
              case ArithBinOpType.SUB => {
                if (to.variable == leftExpr && isRegister(locationTo)){
                  (
                    List(
                      COMMENT(f"calculate ${leftExpr.toString} - ${rightExpr.toString}"),
                      SUBQ(locationRight, locationTo.asInstanceOf[REG])
                    ), locationTo
                  )
                } else {
                  (
                    List(
                      COMMENT(f"calculate ${leftExpr.toString} - ${rightExpr.toString}"),
                      MOVQ(locationLeft, R10),
                      SUBQ(locationRight, R10)
                    ),
                    R10
                  )
                }
              }
              case ArithBinOpType.MUL => {

                locationRight match {
                  case imm: IMM => {
                    val n = imm.v
                    val isPowerOfTwo = (n & n - 1) == 0

                    if (isPowerOfTwo && OPTIMIZE_ASSEMBLY) {
                      val shiftAmt = (Math.log(imm.v) / Math.log(2L)).toLong
                      (
                        List(
                          COMMENT(f"calculate ${leftExpr.toString} * ${rightExpr.toString}"),
                          // Use %rcx as our temporary register
                          MOVQ(locationLeft, R10),
                          SHL(IMM(shiftAmt), R10)
                        ),
                        R10
                      )
                    } else {
                      (
                        List(
                          COMMENT(f"calculate ${leftExpr.toString} * ${rightExpr.toString}"),
                          // Use %rcx as our temporary register
                          MOVQ(locationLeft, R10),
                          IMULQ_IMM(imm, R10, R10)
                        ),
                        R10
                      )
                    }
                  }
                  case memloc: MEMLOC => {
                    if (to.variable == leftExpr && isRegister(locationTo)) {
                      (
                        List(
                          COMMENT(f"calculate ${leftExpr.toString} * ${rightExpr.toString}"),
                          IMULQ(memloc, locationTo.asInstanceOf[REG])
                        ),
                        locationTo
                      )
                    } else {
                      (
                        List(
                          COMMENT(f"calculate ${leftExpr.toString} * ${rightExpr.toString}"),
                          MOVQ(locationLeft, R10),
                          IMULQ(memloc, R10)
                        ),
                        R10
                      )
                    }

                  }
                }

              }
              case ArithBinOpType.DIV => {
                var isPowerOfTwo = false
                var shiftAmt = -999L

                locationRight match {
                  case IMM(v) => {
                    isPowerOfTwo = (v & v - 1) == 0
                    shiftAmt = (Math.log(v) / Math.log(2L)).toLong
                  }
                  case memloc: MEMLOC =>
                }

                if (!isPowerOfTwo || !OPTIMIZE_ASSEMBLY){
                  (
                    List(
                      COMMENT(f"calculate ${leftExpr.toString} / ${rightExpr.toString}"),
                      // Clobbers %rax, %rdx, %rdi
                      PUSH(RAX),
                      PUSH(RDX),
                      PUSH(RDI),

                      MOVQ(locationRight, R11),
                      // Move the dividend to %rax
                      MOVQ(locationLeft, RAX),
                      CQTO(),
                      // Now do the division with the divisor
                      MOVQ(R11, RDI),
                      IDIVQ(RDI),
                      // Answer is now in %rax, move it to where we want it to go
                      MOVQ(RAX, R10),

                      POP(RDI),
                      POP(RDX),
                      POP(RAX)
                    ),
                    R10
                  )
                } else {
                  (
                    List(
                      COMMENT(f"calculate ${leftExpr.toString} / ${rightExpr.toString}"),
                      MOVQ(locationLeft, R10),
                      SAR(IMM(shiftAmt), R10)
                    ), R10
                  )
                }
              }
              case ArithBinOpType.MOD => {
                (
                  List(
                    COMMENT(f"calculate ${leftExpr.toString} mod ${rightExpr.toString}"),
                    // Clobbers %rax, %rdx, %rdi
                    PUSH(RAX),
                    PUSH(RDX),
                    PUSH(RDI),

                    MOVQ(locationRight, R11),
                    // Move the dividend to %rax
                    MOVQ(locationLeft, RAX),
                    CQTO(),
                    // Now do the division with the divisor
                    MOVQ(R11, RDI),
                    IDIVQ(RDI),
                    // Answer is now in %rax, move it to where we want it to go
                    MOVQ(RDX, R10),

                    POP(RDI),
                    POP(RDX),
                    POP(RAX)
                  ),
                  R10
                )
              }
            }
          }
          case opType: LogicalBinOpType => {
            opType match {
              case LogicalBinOpType.AND => {
                if (to.variable == leftExpr && isRegister(locationTo)) {
                  (
                    List(
                      COMMENT(f"calculate ${leftExpr.toString} && ${rightExpr.toString}"),
                      ANDQ(locationRight, locationTo.asInstanceOf[REG])
                    ),
                    locationTo
                  )
                } else {
                  (
                    List(
                      COMMENT(f"calculate ${leftExpr.toString} && ${rightExpr.toString}"),
                      MOVQ(locationLeft, R10),
                      ANDQ(locationRight, R10)
                    ),
                    R10
                  )
                }
              }
              case LogicalBinOpType.OR => {
                if (to.variable == leftExpr && isRegister(locationTo)) {
                  (
                    List(
                      COMMENT(f"calculate ${leftExpr.toString} || ${rightExpr.toString}"),
                      ORQ(locationRight, locationTo.asInstanceOf[REG])
                    ),
                    locationTo
                  )
                } else {
                  (
                    List(
                      COMMENT(f"calculate ${leftExpr.toString} || ${rightExpr.toString}"),
                      MOVQ(locationLeft, R10),
                      ORQ(locationRight, R10)
                    ),
                    R10
                  )
                }
              }
            }
          }
          case opType: RelationalBinOpType => {
            opType match {
              case RelationalBinOpType.LT => {
                (
                  List(
                    COMMENT(f"calculate ${leftExpr.toString} < ${rightExpr.toString}"),
                    MOVQ(locationRight, R10),
                    CMPQ(locationLeft, R10),
                    MOVQ(IMM(1), R11),
                    MOVQ(IMM(0), R10),
                    CMOVG(R11, R10)
                  ),
                  R10
                )
              }
              case RelationalBinOpType.LTE => {
                (
                  List(
                    COMMENT(f"calculate ${leftExpr.toString} <= ${rightExpr.toString}"),
                    MOVQ(locationRight, R10),
                    CMPQ(locationLeft, R10),
                    MOVQ(IMM(1), R11),
                    MOVQ(IMM(0), R10),
                    CMOVGE(R11, R10)
                  ),
                  R10
                )
              }
              case RelationalBinOpType.GT => {
                (
                  List(
                    COMMENT(f"calculate ${leftExpr.toString} > ${rightExpr.toString}"),
                    MOVQ(locationRight, R10),
                    CMPQ(locationLeft, R10),
                    MOVQ(IMM(1), R11),
                    MOVQ(IMM(0), R10),
                    CMOVL(R11, R10)
                  ),
                  R10
                )
              }
              case RelationalBinOpType.GTE => {
                (
                  List(
                    COMMENT(f"calculate ${leftExpr.toString} >= ${rightExpr.toString}"),
                    MOVQ(locationRight, R10),
                    CMPQ(locationLeft, R10),
                    MOVQ(IMM(1), R11),
                    MOVQ(IMM(0), R10),
                    CMOVLE(R11, R10)
                  ),
                  R10
                )
              }
            }
          }
          case opType: EqualityBinOpType => {
            opType match {
              case EqualityBinOpType.EQ => {
                (
                  List(
                    COMMENT(f"calculate ${leftExpr.toString} == ${rightExpr.toString}"),
                    MOVQ(locationRight, R10),
                    CMPQ(locationLeft, R10),
                    MOVQ(IMM(1), R11),
                    MOVQ(IMM(0), R10),
                    CMOVE(R11, R10)
                  ),
                  R10
                )
              }
              case EqualityBinOpType.NEQ => {
                (
                  List(
                    COMMENT(f"calculate ${leftExpr.toString} != ${rightExpr.toString}"),
                    MOVQ(locationRight, R10),
                    CMPQ(locationLeft, R10),
                    MOVQ(IMM(1), R11),
                    MOVQ(IMM(0), R10),
                    CMOVNE(R11, R10)
                  ),
                  R10
                )
              }
            }
          }
        }
      }
      case CfgUnaryOpExpr(op, expr) => {
        val location = getMemLocFromValue(expr, withStatement)

        op match {
          case UnaryOpType.UMINUS => {
            (
              List(
                COMMENT(f"calculate -${location}"),
                MOVQ(location, R10),
                NEGQ(R10)
              ),
              R10
            )
          }
          case UnaryOpType.NOT => {
            (
              List(
                COMMENT(f"calculate !${location}"),
                MOVQ(location, R10),
                XORQ(IMM(1), R10)
              ),
              R10
            )
          }
        }
      }
      case CfgLenExpr(variable) => {
        throw new IllegalStateException()
      }
      case CfgFunctionCallExpr(identifier, params) => {
        (
          visitMethodCall(identifier, params, Some(R10), withStatement.locationMap),
          R10
        )
      }
    }
  }

  private def isRegister(value: VALUE): Boolean = {
    value match {
      case IMM(v) => false
      case memloc: MEMLOC => {
        memloc match {
          case REG(regName) => true
          case STACKLOC(offset, register) => false
          case JMPLOC(locationStr) => false
          case STRLOC(locationStr) => false
        }
      }
    }
  }

  private def isImmediate(value: VALUE): Boolean = {
    value match {
      case IMM(v) => true
      case memloc: MEMLOC => false
    }
  }

  private def getMemLocFromValue(value: CfgValue, withStatement: WithCfgStatement[RealRegister]): VALUE = {
    value match {
      case variable@CfgVariable(identifier, number) => {
        getSymbol(variable, withStatement).location
      }
      case CfgImmediate(value) => {
        IMM(value)
      }
    }
  }

  private def cfgVariableToScopedIdentifier(v: CfgVariable): String = {
    s"${v.identifier}_${v.number}"
  }

}
