package decaf.compile.optim.loop_optim

import decaf.compile.{CfgBasicBlock, CfgConditionalBlock, CfgScope}
import decaf.compile.cfg.{CfgArrayAssignStatement, CfgArrayReadExpr, CfgAssignStatement, CfgBinOpExpr, CfgCondExpression, CfgFunctionCallExpr, CfgFunctionCallStatement, CfgFunctionCallValueParam, CfgImmediate, CfgLenExpr, CfgRegAssignStatement, CfgReturnStatement, CfgStatement, CfgUnaryOpExpr, CfgValue, CfgVariable, ConditionalBlock, MutableScope, RegularBlock}
import decaf.compile.optim.dead_code_elimination.Liveness.getExprUsed

import scala.collection.mutable

object LoopInvariant {
  /**
   * Given a loop and other information, identifies all the constant variables.
   * Note this is different from loop invariants.
   *
   * @param scope the scope
   * @param globalVariables globalVariables are not considered constant
   *                        if any function call occurs in the loop.
   * @param loop the loop represented by the set of loop blocks
   * @return tuple of set of all constant variables through this loop i.e. all variables in the
   *         returned set are guaranteed to hold the same value in all blocks of this loop.
   *         and tuple of set of variables that should not be considered constant
   */
  def loopConstantVariables(
    scope: CfgScope,
    globalVariables: Set[CfgVariable],
    loop: Set[String]
  ): (Set[CfgVariable], Set[CfgVariable]) = {
    var functionCallOccurs = false
    val allUsedVariables = mutable.Set.empty[CfgVariable]
    val assignedVariables = mutable.Set.empty[CfgVariable]

    loop.foreach(blockLabel => {
      val block = scope.basicBlocks(blockLabel)
      block.statements.foreach {
        case statement: CfgAssignStatement =>
          // can't use statementAssignedUse here because we do count any array assign
          // as an assign to that array
          assignedVariables += statement.to.variable
          statement.to.index match {
            case Some(x) =>
              allUsedVariables ++= getExprUsed(x)
            case None =>
          }
          statement match {
            case CfgRegAssignStatement(_, value) =>
              allUsedVariables ++= getExprUsed(value)
              value match {
                case CfgFunctionCallExpr(identifier, params) =>
                  functionCallOccurs = true
                case _ =>
              }
            case _ =>
          }
        case CfgFunctionCallStatement(functionName, parameters) =>
          functionCallOccurs = true
          parameters.foreach {
            case CfgFunctionCallValueParam(param) =>
              allUsedVariables ++= getExprUsed(param)
            case _ =>
          }
        case CfgReturnStatement(value) =>
          if (value.isDefined) {
            allUsedVariables ++= getExprUsed(value.get)
          }
      }

      // add used variables in condition of conditional blocks
      block match {
        case ConditionalBlock(_, _, condition) =>
          allUsedVariables ++= getExprUsed(condition)
        case _ =>
      }
    })

    val constantVariables = if (functionCallOccurs)
      allUsedVariables --= globalVariables --= assignedVariables
    else
      allUsedVariables --= assignedVariables

    (constantVariables.toSet, if (functionCallOccurs) globalVariables else Set.empty)
  }

  /**
   * Given scope and loop,
   *
   * @param scope scope
   * @param loop the loop represented by a tuple of loop header and blocks in the loop
   * @return the blocks that are definitely executed on each iteration of the loop
   *         (and thus the only ones that contain loop invariants)
   */
  def loopInvariantBlocks(
    scope: CfgScope,
    loop: (String, Set[String])
  ): Set[String] = {
    val (headerLabel, loopBlocks) = loop
    var loopBranch = true
    var firstBodyBlockLabel: String = ""

    val trueBranch = scope.trueCfg(headerLabel)
    if (loopBlocks.contains(trueBranch)) {
      loopBranch = true
      firstBodyBlockLabel = trueBranch
    }
    val falseBranch = scope.falseCfg(headerLabel)
    if (loopBlocks.contains(falseBranch)) {
      if (firstBodyBlockLabel == "") {
        loopBranch = false
        firstBodyBlockLabel = falseBranch
      } else {
        throw new IllegalStateException("in decaf, one branch is always included in the loop and the other is not")
      }
    }

    if (firstBodyBlockLabel == headerLabel)
      Set.empty
    else
      Set(firstBodyBlockLabel)
  }

  /**
   * Given a loop and necessary information, identify all loop invariant statements.
   *
   * @param scope scopeInformation
   * @param loop the loop represented by a tuple of loop header and blocks in the loop
   * @param variableInfo tuple of all variables constant within this loop, including globals and arrays, and variables definitely not constant
   * @return for every block in the loop, for every statement in the block, a tuple containing the statement
   *         and a boolean indicating whether or not the statement is invariant
   */
  def loopInvariants(
    scope: CfgScope,
    loop: (String, Set[String]),
    variableInfo: (Set[CfgVariable], Set[CfgVariable])
  ): Map[String, List[(CfgStatement, Boolean)]] = {
    // slides word for word

    val (header, loopBlocks) = loop

    // data structure: filter to have only assign statements
    // function calls are never invariant due to possible side effects
    // return statements are similarly never invariant because
    // of their side effect on control flow
    val invariantStatements: mutable.Map[String, List[(CfgStatement, Boolean)]] =
      mutable.Map(loopBlocks.toSeq.map(label => {
        // initialize to not invariant
        (label, scope.basicBlocks(label).statements.map(statement => (statement, false)))
      }): _*)

    // this is correct since this analysis never adds assigns inside the loop blocks
    val allBlocks = loopBlocks
      .toList // can have duplicate assigns, this is important
      .map(block => scope.basicBlocks(block))
    val allStatements: List[CfgStatement] = allBlocks.flatMap(block => block.statements)
    val assigns = mutable.ListBuffer.empty[CfgRegAssignStatement]
    allStatements.foreach({
      case statement: CfgRegAssignStatement => assigns += statement
      case _ =>
    })
    val allAssignStatements = assigns.toList

    val assignCounts = mutable.Map.empty[CfgVariable, Int]
    allAssignStatements.foreach(assignStatement => {
      val variable = assignStatement.to.variable
      assignCounts.update(variable, assignCounts.getOrElse(variable, 0) + 1)
    })

    // find all variables that are assigned to exactly once, and are not array assigns
    // these are all the potential loop invariants
    val uniqueAssigns = allAssignStatements
      .filter(assignStatement => assignStatement.to.index.isEmpty)
      .map(assignStatement => assignStatement.to.variable)
      .toSet
      .filter(variable => assignCounts(variable) == 1)

    val discoveredConstants = mutable.Set.empty[CfgVariable]
    val (constant, neverConstant) = variableInfo

    val invariantBlocks = loopInvariantBlocks(scope, loop)

    // first pass: mark assign statements as invariant
    // iff they use only constants or variables constant in the loop
    // note we never mark conditions in Conditional Blocks as invariant, since we can't
    // directly move them out of the loop
    var changed = true
    while (changed) {
      changed = false
      invariantStatements.foreach({ case (block, statementsInfo) =>
        if (invariantBlocks.contains(block)) {
          val newStatementsInfo = mutable.ArrayBuffer(statementsInfo: _*)

          for (i <- statementsInfo.indices) {
            val (statement, isInvariant) = newStatementsInfo(i)
            statement match {
              case statement: CfgAssignStatement =>
                // only care about unique assigns, and only if marked as not invariant
                if (uniqueAssigns.contains(statement.to.variable) && !isInvariant) {
                  statement.to.index match {
                    case Some(x) => // for now, ignore assigns to arrays
                    case None =>
                      statement match {
                        case CfgRegAssignStatement(to, value) =>
                          value match {
                            // function calls are not invariant
                            case _: CfgFunctionCallExpr =>
                            case _ =>
                              val used = getExprUsed(value)
                              if (used.forall(variable => (constant.contains(variable)
                                || discoveredConstants.contains(variable))
                              ) && !neverConstant.contains(to.variable)) {
                                discoveredConstants += to.variable
                                newStatementsInfo.update(i, (statement, true))
                                changed = true
                              }
                          }
                        case _ =>
                      }
                  }
                }
              case _ =>
            }
          }

          if (changed)
            invariantStatements.update(block, newStatementsInfo.toList)
        }
      })
    }

    invariantStatements.toMap
  }

  /**
   * Given loop, scope and which invariants to remove, does it.
   *
   * @param scope the scope
   * @param loop the loop represented by a tuple of loop header and blocks in the loop
   * @param invariantInfo see `loopInvariants` return value
   * @return new scope with the invariant statements removed (not added back in!)
   */
  def removeLoopInvariants(
    scope: CfgScope,
    loop: (String, Set[String]),
    invariantInfo: Map[String, List[(CfgStatement, Boolean)]]
  ): CfgScope = {
    val (header, loopBlocks) = loop
    val newBlocks = loopBlocks
      .toSeq
      .map(label => label -> scope.basicBlocks(label))
      .map({ case (label, block) =>
        (label, block match {
          case RegularBlock(fieldDecls, _) => RegularBlock[CfgStatement](
            fieldDecls,
            statements = invariantInfo(label).filter(info => !info._2).map(info => info._1)
          )
          case ConditionalBlock(fieldDecls, _, condition) => ConditionalBlock[CfgStatement](
            fieldDecls,
            statements = invariantInfo(label).filter(info => !info._2).map(info => info._1),
            condition
          )
        })
      })
      .toMap

    scope.copy(
      basicBlocks = scope.basicBlocks ++ newBlocks
    )
  }

  def getPreHeaderLabel(headerLabel: String) = s"${headerLabel}_preheader"
  def getInvariantLabel(headerLabel: String) = s"${headerLabel}_invariants"

  def condBlockHasSideEffects(condBlock: CfgConditionalBlock): Boolean = {
    condBlock.statements.exists {
      case statement: CfgAssignStatement => statement match {
        case CfgRegAssignStatement(to, value) => value match {
          case CfgFunctionCallExpr(identifier, params) => true
          case _ => false
        }
        case CfgArrayAssignStatement(to, value) => false
      }
      case CfgFunctionCallStatement(functionName, parameters) => true
      case CfgReturnStatement(value) => false
    }
  }

  /**
   * Adds back the loop invariants removed by `removeLoopInvariants`, but in front of the loop
   * this time. Reference:
   * https://en.wikipedia.org/wiki/Loop-invariant_code_motion#Example
   *
   * @param scope the scope to modify
   * @param loop the loop represented by a tuple of loop header and blocks in the loop
   * @param invariants list of invariants to add back
   * @return
   */
  def addBackLoopInvariants(
    scope: CfgScope,
    loop: (String, Set[String]),
    invariants: List[CfgStatement]
  ): CfgScope = {
    val (headerLabel, loopBlocks) = loop
    val headerBlock: CfgConditionalBlock = scope.basicBlocks(headerLabel) match {
      case block: CfgConditionalBlock => block
      case _ =>
        throw new IllegalStateException("loop header is not a conditional block")
    }

    val preHeader = headerBlock.copy() // see the wikipedia example
    val preHeaderLabel = getPreHeaderLabel(headerLabel)


    // we need to find which branch the loop is on as well as the first block in this branch
    // i.e. the block the while connects to
    var loopBranch = true
    var firstBodyBlockLabel: String = ""

    val trueBranch = scope.trueCfg(headerLabel)
    if (loopBlocks.contains(trueBranch)) {
      loopBranch = true
      firstBodyBlockLabel = trueBranch
    }
    val falseBranch = scope.falseCfg(headerLabel)
    if (loopBlocks.contains(falseBranch)) {
      if (firstBodyBlockLabel == "") {
        loopBranch = false
        firstBodyBlockLabel = falseBranch
      } else {
        throw new IllegalStateException("in decaf, one branch is always included in the loop and the other is not")
      }
    }

    // then add the new blocks and every other edge
    if (invariants.nonEmpty && !condBlockHasSideEffects(headerBlock)) {
      val result = new MutableScope[CfgBasicBlock](scope)
      // first move the predecessors of the header to the preHeader
      scope.trueCfg.filter({ case (from, to) => to == headerLabel && !loopBlocks.contains(from) }).keys
        .foreach(from => result.trueCfg += (from -> preHeaderLabel))
      scope.falseCfg.filter({ case (from, to) => to == headerLabel && !loopBlocks.contains(from) }).keys
        .foreach(from => result.falseCfg += (from -> preHeaderLabel))
      scope.regCfg.filter({ case (from, to) => to == headerLabel && !loopBlocks.contains(from) }).keys
        .foreach(from => result.regCfg += (from -> preHeaderLabel))
      // println("nonempty!", invariants)
      // make a invariants block
      val invariantsBlock = RegularBlock[CfgStatement](
        fieldDecls = List.empty,
        statements = invariants
      )
      val invariantsLabel = getInvariantLabel(headerLabel)
      result.basicBlocks += (preHeaderLabel -> preHeader)
      result.basicBlocks += (invariantsLabel -> invariantsBlock)
      result.entry = if (headerLabel == scope.entry) preHeaderLabel else scope.entry
      result.exit = if (headerLabel == scope.exit) preHeaderLabel else scope.exit
      result.regCfg += (invariantsLabel -> headerLabel)
      if (loopBranch) {
        result.trueCfg += (preHeaderLabel -> invariantsLabel)
        result.falseCfg += (preHeaderLabel -> scope.falseCfg(headerLabel))
      } else {
        result.falseCfg += (preHeaderLabel -> invariantsLabel)
        result.trueCfg += (preHeaderLabel -> scope.trueCfg(headerLabel))
      }

      result.toScope
      // scope.copy(
      //   basicBlocks = scope.basicBlocks + (preHeaderLabel -> preHeader) + (invariantsLabel -> invariantsBlock),
      //   entry = if (headerLabel == scope.entry) preHeaderLabel else scope.entry,
      //   exit = if (headerLabel == scope.exit) preHeaderLabel else scope.exit,
      //   regCfg = scope.regCfg + (preHeaderLabel -> invariantsLabel) + (invariantsLabel -> firstBodyBlockLabel),
      //   trueCfg = if (loopBranch) scope.trueCfg + (preHeaderLabel -> invariantsLabel) else scope.trueCfg,
      //   falseCfg = if (!loopBranch) scope.falseCfg + (preHeaderLabel -> invariantsLabel) else scope.falseCfg
      // )
    } else {
      // // println("empty!")
      // // empty, no need to make a new invariants block
      // result.basicBlocks += (preHeaderLabel -> preHeader)
      // result.entry = if (headerLabel == scope.entry) preHeaderLabel else scope.entry
      // result.exit = if (headerLabel == scope.exit) preHeaderLabel else scope.exit
      // if (loopBranch) {
      //   result.trueCfg += (preHeaderLabel -> firstBodyBlockLabel)
      //   result.falseCfg += (preHeaderLabel -> scope.falseCfg(headerLabel))
      // } else {
      //   result.falseCfg += (preHeaderLabel -> firstBodyBlockLabel)
      //   result.trueCfg += (preHeaderLabel -> scope.trueCfg(headerLabel))
      // }
      // // scope.copy(
      // //   basicBlocks = scope.basicBlocks + (preHeaderLabel -> preHeader),
      // //   entry = if (headerLabel == scope.entry) preHeaderLabel else scope.entry,
      // //   exit = if (headerLabel == scope.exit) preHeaderLabel else scope.exit,
      // //   regCfg = scope.regCfg,
      // //   trueCfg = if (loopBranch) scope.trueCfg + (preHeaderLabel -> firstBodyBlockLabel) else scope.trueCfg,
      // //   falseCfg = if (!loopBranch) scope.falseCfg + (preHeaderLabel -> firstBodyBlockLabel) else scope.falseCfg
      // // )
      scope
    }
  }

  /**
   * Performs loop invariant optimization on this scope.
   *
   * @param scope scope to optimize
   * @param globalVariables global variables
   * @return optimized scope
   */
  def optimize(scope: CfgScope, globalVariables: Set[CfgVariable]): CfgScope = {
    val dominators = LoopOptim.dominators(scope)
    val loops = LoopOptim.findLoops(scope, dominators)

    // update loops from dominator order
    var result = scope
    loops.toList
      .sortWith((a, b) =>
        dominators(b._1).contains(a._1)
      )
      .foreach(loop => {
        val variableInfo = LoopInvariant.loopConstantVariables(
          result, globalVariables, loop._2)
        // println("variable info", variableInfo)
        val invariants = LoopInvariant.loopInvariants(result, loop, variableInfo)
        // println("invariants", invariants)
        val removeScope = removeLoopInvariants(result, loop, invariants)
        result = addBackLoopInvariants(removeScope, loop,
          invariants.values.flatten.filter(_._2).map(_._1).toList)
        // println(result)
      })

    result
  }
}
