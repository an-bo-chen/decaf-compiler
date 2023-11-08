package decaf.compile.optim.common_subexpression_elmination

import decaf.compile._
import decaf.compile.cfg._
import scala.collection.{immutable, mutable}

object OptimCse{

  var GLOBALS: Set[CfgVariable] = Set.empty
  var UPDATED = false

  def visitProgram(program: CfgProgram): CfgProgram = {
    GLOBALS = program.globalVariables.map(global => global.variable).toSet
    UPDATED = false;

    val optimFunctions = program.functions.map(func => visitFunction(func))

    program.copy(
      functions = optimFunctions
    )
  }

  private def visitFunction(function: CfgFunction): CfgFunction = {
    function.copy(
      scope = visitScope(function.scope)
    )
  }

  /**
   * Filters a set of expressions such that the expression does not contain global variables
   * Global variables are susceptible to side effects, so we chose to not optimize expressions with global variables
   *
   * Note we only care about CfgBinOpExpr for CSE
   *
   * @param expressions a set of CfgExpressions to filter on
   * @return a set of CfgExpressions where all expressions do not contain global variables
   */
  def filterGlobals(expressions: Set[CfgExpression]): Set[CfgExpression] = {
    val filterExpressions = expressions.filter(expr => {
      expr match {
        case CfgBinOpExpr(_, leftExpr, rightExpr) =>
          var noGlobalsLeft = true
          var noGlobalsRight = true

          leftExpr match {
            case CfgImmediate(_) =>
            case variable@CfgVariable(_, _) =>
              if (GLOBALS.contains(variable)) {
                noGlobalsLeft = false
              }
          }

          rightExpr match {
            case CfgImmediate(_) =>
            case variable@CfgVariable(_, _) =>
              if (GLOBALS.contains(variable)) {
                noGlobalsRight = false
              }
          }

          noGlobalsLeft && noGlobalsRight
        case CfgArrayReadExpr(variable, _) =>
          !GLOBALS.contains(variable)
        case _ => throw new IllegalStateException()
      }
    })

    filterExpressions
  }

  /**
   * Get all unique expressions from a list of statements
   *
   * Note we only care about CfgBinOpExpr for CSE
   *
   * @param statements a list of CfgStatements
   * @return a set of CfgExpressions
   */
  private def getExpressions(statements: List[CfgStatement]): Set[CfgExpression] = {
    val expressions = mutable.Set.empty[CfgExpression]

    statements.foreach {
      case CfgRegAssignStatement(to, value) =>
        if (to.index.isEmpty) {
          value match {
            case CfgBinOpExpr(_, _, _) =>
              expressions += AvailableExpressions.canonicalization(value)
            case CfgArrayReadExpr(_, _) =>
              expressions += value
            case _ =>
          }
        }
      case _ =>
    }

    filterGlobals(expressions.toSet)
  }

  /**
   * Maps a variable to associated set of expressions that contain the variable
   * Filters out expressions with global variables
   *
   * Note we only care about CfgBinOpExpr for CSE
   *
   * @param expressions a set of CfgExpressions
   * @return a mapping of CfgVariables to a set of CfgExpressions
   */
  private def getVarToExprs(expressions: Set[CfgExpression]): Map[CfgVariable, Set[CfgExpression]] = {
    val varToExpr = mutable.Map.empty[CfgVariable, Set[CfgExpression]]
    expressions.foreach {
      case binOp@CfgBinOpExpr(_, leftExpr, rightExpr) =>
        leftExpr match {
          case CfgImmediate(_) =>
          case variable@CfgVariable(_, _) =>
            if (!GLOBALS.contains(variable)) {
              if (varToExpr.contains(variable)) {
                varToExpr.update(variable, varToExpr(variable) + binOp)
              } else {
                varToExpr.update(variable, Set(binOp))
              }
            }
        }

        rightExpr match {
          case CfgImmediate(_) =>
          case variable@CfgVariable(_, _) =>
            if (!GLOBALS.contains(variable)) {
              if (varToExpr.contains(variable)) {
                varToExpr.update(variable, varToExpr(variable) + binOp)
              } else {
                varToExpr.update(variable, Set(binOp))
              }
            }
        }
      case _ =>
    }

    varToExpr.toMap
  }
  private def visitScope(scope: CfgScope): CfgScope = {
    val allStatements = scope.basicBlocks.values.flatMap(block => block.statements).toList
    val allExpressions = getExpressions(allStatements)
    val varToExprs: Map[CfgVariable, Set[CfgExpression]] = getVarToExprs(allExpressions)
    val exprToTemp: Map[CfgExpression, CfgVariable] = allExpressions.map(expr => (expr, getNewTemp())).toMap

    val availableExpressions = AvailableExpressions.compute(scope, allExpressions, varToExprs)

    val optimizedBlocks = scope.basicBlocks.map({
      case (label, block) =>
        (label, visitBlock(
          block = block,
          availableBlockExpressions = availableExpressions(label),
          exprToTemp = exprToTemp,
          varToExprs = varToExprs
        ))
    })

    scope.copy(
      basicBlocks = optimizedBlocks
    )
  }

  private def visitBlock(
   block: CfgBasicBlock,
   availableBlockExpressions: Set[CfgExpression],
   exprToTemp: Map[CfgExpression, CfgVariable],
   varToExprs: Map[CfgVariable, Set[CfgExpression]]
  ): CfgBasicBlock = {
      val cfgStatements = mutable.ListBuffer.empty[CfgStatement]
      val killedBlockExpressions = mutable.Set.empty[CfgExpression]
      val localKilledBlockExpressions = mutable.Set.empty[CfgExpression]
      val seenBlockExpressions = mutable.Set.empty[CfgExpression]

      block.statements.foreach {
        case statement@CfgRegAssignStatement(to, value) =>
//          to.index match {
//            case Some(_) => cfgStatements.append(statement) // for assignments to array locations
//            case None =>
              val canonicalValue = AvailableExpressions.canonicalization(value)
              if (exprToTemp.contains(canonicalValue)) {
                val isAvailableGlobal = availableBlockExpressions.contains(canonicalValue) && !killedBlockExpressions.contains(canonicalValue)
                val isAvailableLocal = seenBlockExpressions.contains(canonicalValue) && !localKilledBlockExpressions.contains(canonicalValue)
                if (isAvailableGlobal || isAvailableLocal) {
                  UPDATED = true
                  cfgStatements.append(
                    CfgRegAssignStatement(
                      to,
                      exprToTemp(canonicalValue)
                    )
                  )
                } else {
                  cfgStatements.append(CfgRegAssignStatement(to, value))

                  to.index match {
                    case Some(_) =>
                    case None =>
                      cfgStatements.append(
                        CfgRegAssignStatement(
                          to = CfgLocation(exprToTemp(canonicalValue), None),
                          value = to.variable)
                      )
                      seenBlockExpressions += canonicalValue
                  }
                }
              } else { // for expressions with global variables and non binOps
                cfgStatements.append(statement)
              }
//          }

          to.index match {
            case Some(_) =>
              localKilledBlockExpressions.add(CfgArrayReadExpr(to.variable, to.index.get))
              killedBlockExpressions.add(CfgArrayReadExpr(to.variable, to.index.get))
            case None =>
              if (varToExprs.contains(to.variable)) {
                val killedExpressions = varToExprs(to.variable)

                localKilledBlockExpressions ++= (killedExpressions.intersect(seenBlockExpressions))
                killedBlockExpressions ++= killedExpressions
              }
          }
        case statement => // for non reg assign statements
          cfgStatements.append(statement)
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

  private val prefixMap = mutable.HashMap.empty[String, Int]

  private def _getNewVariable(identifier: String): CfgVariable = {
    val prefixNumber = prefixMap.getOrElse(identifier, 1)
    prefixMap.update(identifier, prefixNumber + 1)
    CfgVariable(
      identifier = identifier,
      number = prefixNumber
    )
  }

  private val getNewTemp = () => {
    _getNewVariable(s"_.tcse")
  }

}
