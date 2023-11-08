package decaf.compile.optim.constant_copy_propagation

import decaf.compile.Compiler.{OPTIMIZE_CP, OPTIMIZE_CSP}
import decaf.compile._
import decaf.compile.cfg._

import scala.collection.mutable

object OptimCp {

  var GLOBALS: Set[CfgVariable] = Set.empty
  var UPDATED = false;

  //noinspection DuplicatedCode
  def visitProgram(program: CfgProgram): CfgProgram = {
    GLOBALS = program.globalVariables.map(global => global.variable).toSet
    UPDATED = false

    val optimFunctions = program.functions.map(func => visitFunction(func))
    program.copy(
      functions = optimFunctions
    )
  }

  //noinspection DuplicatedCode
  private def visitFunction(function: CfgFunction): CfgFunction = {
    function.copy(
      scope = visitScope(function.scope)
    )
  }

  def isGlobalVariable(variable: CfgVariable): Boolean = {
    GLOBALS.contains(variable)
  }

  /**
   * Filters a set of copy statements such that the statement does not contain global variables
   * Global variables are susceptible to side effects, so we chose to not optimize expressions with global variables
   *
   * @param copies a set of CfgRegAssignmentStatement to filter on
   * @return a set of copy statements where all copy statements do not contain global variables
   */
  private def filterGlobals(copies: Set[CfgRegAssignStatement]): Set[CfgRegAssignStatement] = {
    val filterCopies = copies.filter(statement =>
      statement.value match {
        case CfgImmediate(value) =>
          !isGlobalVariable(statement.to.variable)
        case variable@CfgVariable(_, _) =>
          !isGlobalVariable(statement.to.variable) && !isGlobalVariable(variable)
        case _ => throw new IllegalStateException()
      }
    )

    filterCopies
  }

  /**
   * Get all copy statements from a list of statements
   *
   * @param statements a list of CfgStatements
   * @return a set of copy statements without global variables
   */
  private def getCopies(statements: List[CfgStatement]): Set[CfgRegAssignStatement] = {
    val copyStatements = mutable.Set.empty[CfgRegAssignStatement]

    statements.foreach {
      case statement@CfgRegAssignStatement(to, value) =>
        if (to.index.isEmpty) {
          value match {
            case CfgImmediate(_) =>
              if (OPTIMIZE_CSP) {
                copyStatements.add(statement)
              }
            case CfgVariable(_, _) =>
              if (OPTIMIZE_CP) {
                copyStatements.add(statement)
              }
            case _ =>
          }
        }
      case _ =>
    }

    filterGlobals(copyStatements.toSet)
  }

  /**
   * Maps a variable to associated set of copies that contain the variable
   *
   * @param copies a set of copies without global variables
   * @return a mapping of variable to a set of copy statements
   */
  private def getVarToCopies(copies: Set[CfgRegAssignStatement]): Map[CfgVariable, Set[CfgRegAssignStatement]] = {
    val varToCopies = mutable.Map.empty[CfgVariable, Set[CfgRegAssignStatement]]
    copies.foreach {
      case statement@CfgRegAssignStatement(to, value) =>
        if (to.index.isEmpty) {
          value match {
            case CfgImmediate(_) =>
              if (OPTIMIZE_CSP) {
                if (varToCopies.contains(to.variable)) {
                  varToCopies.update(to.variable, varToCopies(to.variable) + statement)
                } else {
                  varToCopies.update(to.variable, Set(statement))
                }
              }
            case variable@CfgVariable(_, _) =>
              if (OPTIMIZE_CP) {
                if (varToCopies.contains(to.variable)) {
                  varToCopies.update(to.variable, varToCopies(to.variable) + statement)
                } else {
                  varToCopies.update(to.variable, Set(statement))
                }

                if (varToCopies.contains(variable)) {
                  varToCopies.update(variable, varToCopies(variable) + statement)
                } else {
                  varToCopies.update(variable, Set(statement))
                }
              }
          }
        }
      case _ => throw new IllegalStateException()
    }

    varToCopies.toMap
  }

  private def visitScope(scope: CfgScope): CfgScope = {
    val allStatements = scope.basicBlocks.values.flatMap(block => block.statements).toList
    val allCopies = getCopies(allStatements)
    val varToCopies = getVarToCopies(allCopies)

    val availableCopies = AvailableCopies.compute(scope, allCopies, varToCopies)
    val availableTmpToVal: Map[String, Map[CfgVariable, CfgValue]] = availableCopies.map {
      case (label, copy) =>
        val tmpToVar = copy.map {
          case CfgRegAssignStatement(to, value) =>
            value match {
              case immediate@CfgImmediate(_) =>
                (to.variable, immediate)
              case variable@CfgVariable(_, _) =>
                (to.variable, variable)
            }
          case _ => throw new IllegalStateException()
        }.toMap

        (label, tmpToVar)
    }

    val availableVarToSet: Map[String, Map[CfgVariable, Set[CfgVariable]]] = availableCopies.map {
      case (label, copy) =>
        val varToSet = mutable.Map.empty[CfgVariable, Set[CfgVariable]]
        copy.foreach {
          case CfgRegAssignStatement(to, value) =>
            value match {
              case variable@CfgVariable(_, _) =>
                if (OPTIMIZE_CP) {
                  if (varToSet.contains(variable)) {
                    varToSet.update(variable, varToSet(variable) + to.variable)
                  } else {
                    varToSet.update(variable, Set(to.variable))
                  }
                }
              case _ =>
            }
          case _ => throw new IllegalStateException()
        }

        (label, varToSet.toMap)
    }

    val optimizedBlocks = scope.basicBlocks.map({
      case (label, block) =>
        (label, visitBlock(
          block = block,
          blockTmpToVal = availableTmpToVal(label),
          blockVarToSet = availableVarToSet(label)
        ))
    })

    scope.copy(
      basicBlocks = optimizedBlocks
    )
  }

  private def visitBlock(
    block: CfgBasicBlock,
    blockTmpToVal: Map[CfgVariable, CfgValue],
    blockVarToSet: Map[CfgVariable, Set[CfgVariable]]
  ): CfgBasicBlock = {
    val availableTmpToVal = mutable.Map[CfgVariable, CfgValue](blockTmpToVal.toSeq: _*)
    val availableVarToSet = mutable.Map[CfgVariable, Set[CfgVariable]](blockVarToSet.toSeq: _*)
    val tmpToVal = mutable.Map.empty[CfgVariable, CfgValue]
    val varToSet = mutable.Map.empty[CfgVariable, Set[CfgVariable]]

    val cfgStatements = mutable.ListBuffer.empty[CfgStatement]

    block.statements.foreach {
      case currStatement: CfgAssignStatement =>
        currStatement match {
          case CfgRegAssignStatement(leftLocation, expression) =>
//            leftLocation.index match {
//              case Some(_) => cfgStatements.append(currStatement)
//              case None =>

                if (availableVarToSet.contains(leftLocation.variable)) {
                  availableVarToSet(leftLocation.variable).foreach(temp =>
                    availableTmpToVal.remove(temp)
                  )
                  availableVarToSet.remove(leftLocation.variable)
                }
                if (varToSet.contains(leftLocation.variable)) {
                  varToSet(leftLocation.variable).foreach(temp =>
                    tmpToVal.remove(temp)
                  )
                  varToSet.remove(leftLocation.variable)
                }

                if (availableTmpToVal.contains(leftLocation.variable)) {
                  availableTmpToVal.remove(leftLocation.variable)
                }

                if (tmpToVal.contains(leftLocation.variable)) {
                  tmpToVal.remove(leftLocation.variable)
                }

                expression match {
                  case value: CfgValue =>
                    value match {
                      case rightVariable: CfgVariable =>
                        if (!isGlobalVariable(rightVariable) && !isGlobalVariable(leftLocation.variable)) {
                          if (availableTmpToVal.contains(rightVariable)) {
                            UPDATED = true
                            cfgStatements.append(
                              CfgRegAssignStatement(
                                to = leftLocation,
                                value = availableTmpToVal(rightVariable)
                              )
                            )
                          } else if (tmpToVal.contains(rightVariable)) {
                            UPDATED = true
                            cfgStatements.append(
                              CfgRegAssignStatement(
                                to = leftLocation,
                                value = tmpToVal(rightVariable)
                              )
                            )
                            if (OPTIMIZE_CP) {
                              leftLocation.index match {
                                case Some(_) =>
                                case None =>
                                  tmpToVal.put(leftLocation.variable, tmpToVal(rightVariable))
                              }
                            }
                          } else {
                            cfgStatements.append(currStatement)
                            if (OPTIMIZE_CP) {
                              leftLocation.index match {
                                case Some(_) =>
                                case None =>
                                  tmpToVal.put(leftLocation.variable, rightVariable)
                                  if (varToSet.contains(rightVariable)) {
                                    varToSet.put(rightVariable, varToSet(rightVariable) + leftLocation.variable)
                                  } else {
                                    varToSet.put(rightVariable, Set(leftLocation.variable))
                                  }
                              }
                            }
                          }
                        } else { // for global variables
                          cfgStatements.append(currStatement)
                        }
                      case immediate: CfgImmediate =>
                        cfgStatements.append(currStatement)

                        if (OPTIMIZE_CSP) {
                          leftLocation.index match {
                            case Some(_) =>
                            case None =>
                              tmpToVal.put(leftLocation.variable, immediate)
                          }
                        }
                    }
                  case CfgArrayReadExpr(_, _) => cfgStatements.append(currStatement)
                  case CfgLenExpr(_) => cfgStatements.append(currStatement)
                  case CfgFunctionCallExpr(_, _) => cfgStatements.append(currStatement)
                  case unaryOp@CfgUnaryOpExpr(_, expr) =>
                    val newExpr: CfgValue = expr match {
                      case immediate: CfgImmediate => immediate
                      case variable: CfgVariable =>
                        if (availableTmpToVal.contains(variable)) {
                          UPDATED = true
                          availableTmpToVal(variable)
                        } else if (tmpToVal.contains(variable)) {
                          UPDATED = true
                          tmpToVal(variable)
                        } else {
                          variable
                        }
                    }

                    cfgStatements.append(
                      CfgRegAssignStatement(
                        to = leftLocation,
                        value = unaryOp.copy(
                          expr = newExpr
                        )
                      )
                    )
                  case binOp@CfgBinOpExpr(_, leftExpr, rightExpr) =>
                    val newLeft: CfgValue = leftExpr match {
                      case immediate: CfgImmediate => immediate
                      case leftVariable: CfgVariable =>
                        if (availableTmpToVal.contains(leftVariable)) {
                          UPDATED = true
                          availableTmpToVal(leftVariable)
                        } else if (tmpToVal.contains(leftVariable)) {
                          UPDATED = true
                          tmpToVal(leftVariable)
                        } else {
                          leftVariable
                        }
                    }

                    val newRight: CfgValue = rightExpr match {
                      case immediate: CfgImmediate => immediate
                      case rightVariable: CfgVariable =>
                        if (availableTmpToVal.contains(rightVariable)) {
                          UPDATED = true
                          availableTmpToVal(rightVariable)
                        } else if (tmpToVal.contains(rightVariable)) {
                          UPDATED = true
                          tmpToVal(rightVariable)
                        } else {
                          rightVariable
                        }
                    }

                    cfgStatements.append(
                      CfgRegAssignStatement(
                        to = leftLocation,
                        value = binOp.copy(
                          leftExpr = newLeft,
                          rightExpr = newRight
                        )
                      )
                    )
                }
//            }

          case CfgArrayAssignStatement(_, _) => cfgStatements.append(currStatement)
        }
      case currStatement@CfgFunctionCallStatement(_, _) => cfgStatements.append(currStatement)
      case currStatement@CfgReturnStatement(value) =>
        value match {
          case Some(x) =>
            val newReturn = x match {
              case immediate: CfgImmediate => immediate
              case variable: CfgVariable =>
                if (availableTmpToVal.contains(variable)) {
                  UPDATED = true
                  availableTmpToVal(variable)
                }else if (tmpToVal.contains(variable)) {
                  UPDATED = true
                  tmpToVal(variable)
                } else {
                  variable
                }
            }
            cfgStatements.append(CfgReturnStatement(Some(newReturn)))
          case None => cfgStatements.append(currStatement)
        }
    }

    block match {
      case block@RegularBlock(_, _) =>
        block.copy(
        statements = cfgStatements.toList
      )
      case block@ConditionalBlock(_, _, _) =>
        block.copy(
        statements = cfgStatements.toList
      )
    }
  }
}