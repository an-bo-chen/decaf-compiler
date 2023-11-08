package decaf.compile.cfg

import decaf.compile.{CfgBasicBlock, CfgConditionalBlock, CfgRegularBlock, CfgScope}
import decaf.compile.cfg.CfgVisitor.expandExpressionToExpression
import decaf.compile.ir.{ArithBinOpType, EqualityBinOpType, IrBinOpExpr, IrBoolLiteral, IrExpression, IrFunctionCallExpr, IrIntLiteral, IrLenExpr, IrLiteral, IrTernaryExpr, IrUnaryOpExpr, IrVarReadExpr, LogicalBinOpType, RelationalBinOpType, UnaryOpType}

import scala.annotation.tailrec
import scala.collection.mutable

object CfgVisitorUtil {
  // don't crush
  def irLiteralToLong(irLiteral: IrLiteral): Long = {
    irLiteral match {
      case IrIntLiteral(value, _) => value
      case IrBoolLiteral(value, _) => if (value) 1L else 0L
      case _ =>
        throw new IllegalStateException("Expected int or bool literal")
    }
  }

  /**
   * Given a simple invertible branching condition, invert it. "Simple" means either an immediate,
   * NOT of an expression, or a relational/ equality binary operation. AND and OR are not supported
   * since they should be handled via short-circuiting.
   *
   * @param condition the condition to invert.
   * @return condition that always evaluates false when `condition` evaluates true, and vice versa.
   */
  def invertCondition(condition: CfgCondExpression): CfgCondExpression = {
    condition match {
      case CfgImmediate(value) => CfgImmediate(if (value == 0) 1 else 0)
      case variable: CfgVariable => CfgUnaryOpExpr(UnaryOpType.NOT, variable)
      case CfgUnaryOpExpr(op, expr) => op match {
        case UnaryOpType.UMINUS => throw new IllegalArgumentException("Negate a condition is not semantically valid.")
        case UnaryOpType.NOT => throw new IllegalArgumentException("NOT should be short-circuited, not directly inverted")
      }
      case CfgBinOpExpr(op, leftExpr, rightExpr) => op match {
        case _: ArithBinOpType =>
          throw new IllegalArgumentException("Arithmetic operation as a condition is not semantically valid.")
        case opType: RelationalBinOpType => opType match {
          case RelationalBinOpType.LT => CfgBinOpExpr(RelationalBinOpType.GTE, leftExpr, rightExpr)
          case RelationalBinOpType.LTE => CfgBinOpExpr(RelationalBinOpType.GT, leftExpr, rightExpr)
          case RelationalBinOpType.GT => CfgBinOpExpr(RelationalBinOpType.LTE, leftExpr, rightExpr)
          case RelationalBinOpType.GTE => CfgBinOpExpr(RelationalBinOpType.LT, leftExpr, rightExpr)
        }
        case opType: EqualityBinOpType => opType match {
          case EqualityBinOpType.EQ => CfgBinOpExpr(EqualityBinOpType.NEQ, leftExpr, rightExpr)
          case EqualityBinOpType.NEQ => CfgBinOpExpr(EqualityBinOpType.EQ, leftExpr, rightExpr)
        }
        case _: LogicalBinOpType =>
          throw new IllegalArgumentException("Logical operation should be short-circuited, not directly inverted.")
      }
    }
  }

  /**
   * Generates the short-circuited CFG for a boolean expression.
   *
   * @param expression  the boolean expression.
   * @param trueBranch  name of the branch to be taken if the expression evaluates true.
   * @param falseBranch name of the branch to be taken if the expression evaluates false.
   * @param scope       scope information and utility functions for the scope shortCircuit is evaluated in.
   * @return scope with the CFG, where the true and false CFG graphs have been updated
   *         with trueBranch and falseBranch as appropriate.
   *         The exit field of the scope is meaningless.
   *         Block names are used in reverse level order traversal.
   */
  def shortCircuit(
    expression: IrExpression, trueBranch: String, falseBranch: String, scope: ScopeUtil
  ): CfgScope = {
    val PLACEHOLDER_EXIT = ""

    val resolveVariable = scope.resolveVariable
    val getNewBlockName = scope.getNewBlockName

    def simpleValueToResult(blockCondition: CfgValue): CfgScope = {
      val blockName = getNewBlockName()
      Scope[CfgBasicBlock](
        basicBlocks = Map(blockName -> ConditionalBlock[CfgStatement](
          fieldDecls = List.empty,
          statements = List.empty,
          condition = blockCondition
        )),
        entry = blockName,
        exit = PLACEHOLDER_EXIT,
        regCfg = Map.empty,
        trueCfg = Map(blockName -> trueBranch),
        falseCfg = Map(blockName -> falseBranch)
      )
    }

    def simpleExprToResult(expr: IrExpression): CfgScope = {
      val (exprBlock, exprResult) = expandExpressionToExpression(expr, scope)
      val blockName = getNewBlockName()
      exprResult match {
        case cfgCondExpression: CfgCondExpression =>
          Scope[CfgBasicBlock](
            basicBlocks = Map(blockName -> ConditionalBlock[CfgStatement](
              fieldDecls = exprBlock.fieldDecls,
              statements = exprBlock.statements,
              condition = cfgCondExpression
            )),
            entry = blockName,
            exit = PLACEHOLDER_EXIT,
            regCfg = Map.empty,
            // add the true and false branches to the scope
            trueCfg = Map(blockName -> trueBranch),
            falseCfg = Map(blockName -> falseBranch)
          )
        case _ =>
          val tempVar = scope.getNewTemp()
          val tempLocation = CfgLocation(tempVar, None)
          val newBlock = exprBlock.copy(
            statements = exprBlock.statements.+:(CfgRegAssignStatement(tempLocation, exprResult))
          )
          Scope[CfgBasicBlock](
            basicBlocks = Map(blockName -> ConditionalBlock[CfgStatement](
              fieldDecls = newBlock.fieldDecls,
              statements = newBlock.statements,
              condition = tempVar
            )),
            entry = blockName,
            exit = PLACEHOLDER_EXIT,
            regCfg = Map.empty,
            // add the true and false branches to the scope
            trueCfg = Map(blockName -> trueBranch),
            falseCfg = Map(blockName -> falseBranch)
          )
      }
    }

    expression match {
      case literal: IrLiteral =>
        simpleValueToResult(CfgImmediate(irLiteralToLong(literal)))
      case IrVarReadExpr(readLocation, _) =>
        readLocation.index match {
          case Some(_) =>
            simpleExprToResult(expression)
          case None =>
            val readVariable = resolveVariable(readLocation.identifier)
            simpleValueToResult(readVariable)
        }
      case IrFunctionCallExpr(_, _, _) => simpleExprToResult(expression)
      case IrLenExpr(_, _) => simpleExprToResult(expression)
      case IrUnaryOpExpr(op, expr, _) =>
        op match {
          case UnaryOpType.NOT =>
            // swap true and false branches
            CfgVisitorUtil.shortCircuit(expr, falseBranch, trueBranch, scope)
          case _ => simpleExprToResult(expression)
        }
      case IrBinOpExpr(op, leftExpr, rightExpr, _) =>
        op match {
          case opType: LogicalBinOpType =>
            val rightScope = CfgVisitorUtil.shortCircuit(rightExpr, trueBranch, falseBranch, scope)
            val leftScope = opType match {
              case LogicalBinOpType.AND =>
                CfgVisitorUtil.shortCircuit(leftExpr, rightScope.entry, falseBranch, scope)
              case LogicalBinOpType.OR =>
                CfgVisitorUtil.shortCircuit(leftExpr, trueBranch, rightScope.entry, scope)
            }
            Scope[CfgBasicBlock](
              basicBlocks = leftScope.basicBlocks ++ rightScope.basicBlocks,
              entry = leftScope.entry,
              exit = PLACEHOLDER_EXIT,
              regCfg = leftScope.regCfg ++ rightScope.regCfg,
              trueCfg = leftScope.trueCfg ++ rightScope.trueCfg,
              falseCfg = leftScope.falseCfg ++ rightScope.falseCfg
            )
          case _ => simpleExprToResult(expression)
        }
      case IrTernaryExpr(_, _, _, _) => simpleExprToResult(expression)
    }
  }

  /**
   * Generates the short-circuited CFG for a boolean expression, but optimizing for branching in codegen
   * by causing the `fallThroughBranch` to become the fall-through branch, and the falseBranch
   * to become the branch to jump to from the condition.
   *
   * @param fallThroughBranch the branch to optimize the fallThrough case for
   * For other documentation, see `shortCircuit`.
   */
  def shortCircuitOptimBranch(
    expression: IrExpression,
    trueBranch: String,
    falseBranch: String,
    fallThroughBranch: Boolean,
    scope: ScopeUtil
  ): CfgScope = {
    val PLACEHOLDER_EXIT = ""

    def expressionToCondition(expression: IrExpression): (CfgCondExpression, CfgRegularBlock) = {
      val (exprBlock, exprResult) = expandExpressionToExpression(expression, scope)
      exprResult match {
        case expression: CfgCondExpression => (expression, exprBlock)
        case _ =>
          val tempVar = scope.getNewTemp()
          val tempLocation = CfgLocation(tempVar, None)
          val newBlock = exprBlock.copy(
            statements = exprBlock.statements.+:(CfgRegAssignStatement(tempLocation, exprResult))
          )
          (tempVar, newBlock)
      }
    }

    def simpleConditionToResult(condition: CfgCondExpression) =
      conditionToResult(condition, RegularBlock[CfgStatement](List.empty, List.empty))

    def conditionToResult(params: (CfgCondExpression, CfgRegularBlock)): CfgScope = {
      val (condition, addBlock) = params
      val blockName = scope.getNewBlockName()
      Scope[CfgBasicBlock](
        basicBlocks = Map(blockName -> ConditionalBlock[CfgStatement](
          fieldDecls = addBlock.fieldDecls,
          statements = addBlock.statements,
          condition = if (fallThroughBranch) invertCondition(condition) else condition
        )),
        entry = blockName,
        exit = PLACEHOLDER_EXIT,
        regCfg = Map.empty,
        trueCfg = Map(blockName -> (if (fallThroughBranch) falseBranch else trueBranch)),
        falseCfg = Map(blockName -> (if (fallThroughBranch) trueBranch else falseBranch))
      )
    }

    expression match {
      case literal: IrLiteral => simpleConditionToResult(CfgImmediate(irLiteralToLong(literal)))
      case IrVarReadExpr(readLocation, _) => readLocation.index match {
        case Some(_) => conditionToResult(expressionToCondition(expression))
        case None => simpleConditionToResult(scope.resolveVariable(readLocation.identifier))
      }
      case expression: IrFunctionCallExpr => conditionToResult(expressionToCondition(expression))
      case expression: IrLenExpr => conditionToResult(expressionToCondition(expression))
      case IrUnaryOpExpr(op, expr, _) => op match {
        case UnaryOpType.UMINUS =>
          throw new IllegalArgumentException("unary minus not semantically valid as condition")
        case UnaryOpType.NOT => expr match {
          case IrUnaryOpExpr(op, expr, location) => op match {
            case UnaryOpType.UMINUS =>
              throw new IllegalArgumentException("unary minus not semantically valid as condition")
            case UnaryOpType.NOT =>
              // simplify the double negative
              shortCircuitOptimBranch(expr, trueBranch, falseBranch, fallThroughBranch, scope)
          }
          case IrBinOpExpr(op, leftExpr, rightExpr, location) => op match {
            case opType: LogicalBinOpType =>
              // apply DeMorgan's law
              val newExpression: IrExpression = opType match {
                case LogicalBinOpType.AND =>
                  IrBinOpExpr(LogicalBinOpType.OR,
                    IrUnaryOpExpr(UnaryOpType.NOT, leftExpr, location),
                    IrUnaryOpExpr(UnaryOpType.NOT, rightExpr, location),
                    location)
                case LogicalBinOpType.OR =>
                  IrBinOpExpr(LogicalBinOpType.AND,
                    IrUnaryOpExpr(UnaryOpType.NOT, leftExpr, location),
                    IrUnaryOpExpr(UnaryOpType.NOT, rightExpr, location),
                    location)
              }
              shortCircuitOptimBranch(newExpression, trueBranch, falseBranch, fallThroughBranch, scope)
            case _ =>
              // no nested stuff => swap branches
              shortCircuitOptimBranch(expr, falseBranch, trueBranch, !fallThroughBranch, scope)
          }
          case _ =>
            // no nested stuff => swap branches
            shortCircuitOptimBranch(expr, falseBranch, trueBranch, !fallThroughBranch, scope)
        }
      }
      case expression@IrBinOpExpr(op, leftExpr, rightExpr, location) => op match {
        case opType: LogicalBinOpType =>
          val rightScope = shortCircuitOptimBranch(rightExpr, trueBranch, falseBranch, fallThroughBranch, scope)
          val leftScope = opType match {
            case LogicalBinOpType.AND =>
              shortCircuitOptimBranch(leftExpr, rightScope.entry, falseBranch, fallThroughBranch = true, scope)
            case LogicalBinOpType.OR =>
              shortCircuitOptimBranch(leftExpr, trueBranch, rightScope.entry, fallThroughBranch = false, scope)
          }
          Scope[CfgBasicBlock](
            basicBlocks = leftScope.basicBlocks ++ rightScope.basicBlocks,
            entry = leftScope.entry,
            exit = PLACEHOLDER_EXIT,
            regCfg = leftScope.regCfg ++ rightScope.regCfg,
            trueCfg = leftScope.trueCfg ++ rightScope.trueCfg,
            falseCfg = leftScope.falseCfg ++ rightScope.falseCfg
          )
        case _ => conditionToResult(expressionToCondition(expression))
      }
      case expression: IrTernaryExpr => conditionToResult(expressionToCondition(expression))
    }
  }

  /**
   * Given a scope, simplifies it so that all basic blocks are maximal.
   * When basic blocks are combined, the name of the first block in sequence is kept,
   * and the order of the statements and field declarations are preserved.
   * Also removes scopes/ edges unreachable from the entry node.
   *
   * @param scope the scope to simplify. Must be a valid scope.
   * @return the simplified scope.
   */
  def simplifyScope(scope: CfgScope): CfgScope = {
    val resultScope = new MutableScope[CfgBasicBlock](scope)
    // keep track of what block that blocks eliminated through merging now refers to
    // this is necessary due to the detail of the implementation, which
    // requires blocks that have been eliminated through collapse to still refer
    // a definition in resultScope.basicBlocks
    val refers = mutable.HashMap.empty[String, String]

    @tailrec
    def _nowRefersTo(block: String): String = {
      refers.get(block) match {
        case Some(x) => _nowRefersTo(x)
        case None => block
      }
    }

    def nowRefersTo(block: String): String = {
      refers.get(block) match {
        case Some(x) =>
          val result = _nowRefersTo(x)
          // caching
          refers.update(block, result)
          result
        case None => block
      }
    }

    // note we only ever collapse edge (a -> b)
    //  iff outdegree(a) = 1 and indegree(b) = 1
    // this suggests a is always a regular block, so
    // we only might need to collapse edges in regCfg
    // we only might need to rename some blocks in true/falseCfg

    /**
     * Collapse an edge (from -> to) in the original scope and reassign resultScope
     * to a new `CfgScope` accordingly.
     *
     * @param scopeFrom the label of the from block, outdegree(from) = 1
     * @param scopeTo   the label of the to block, indegree(to) = 1
     */
    def collapseEdge(scopeFrom: String, scopeTo: String): Unit = {
      val from = nowRefersTo(scopeFrom)
      val to = nowRefersTo(scopeTo)
      // println(s"Collapsed ${scopeFrom} -> ${scopeTo}")
      // println(s"Actually collapsed ${from} -> ${to}")

      val fromBlock = resultScope.basicBlocks(from)
      val toBlock = resultScope.basicBlocks(to)
      val collapsedBlock: CfgBasicBlock = toBlock match {
        case RegularBlock(fieldDecls, statements) =>
          RegularBlock[CfgStatement](
            fieldDecls = fromBlock.fieldDecls ++ fieldDecls,
            statements = fromBlock.statements ++ statements
          )
        case ConditionalBlock(fieldDecls, statements, condition) =>
          ConditionalBlock[CfgStatement](
            fieldDecls = fromBlock.fieldDecls ++ fieldDecls,
            statements = fromBlock.statements ++ statements,
            condition
          )
      }
      // println(s"remove scopeFrom ${scopeFrom}")
      // println(s"add from ${from}")
      // mark to as removed
      refers.update(to, from)

      // remove toBlock, overwrite fromBlock/ toBlock with the new block
      resultScope.basicBlocks -= to += (from -> collapsedBlock)
      // entry will never be the toBlock, and if it is the fromBlock, the name won't change
      resultScope.entry = resultScope.entry
      // exit will enver be the fromBlock, but if it is the toBlock, the name will change
      resultScope.exit = if (resultScope.exit == to) from else resultScope.exit
      // remove the (from -> to) edge, change any outgoing edge (to -> any) to (from -> any)
      // also remove the original (scopeFrom -> scopeTo) edge, do it first to account for scopeFrom == from
      resultScope.regCfg.get(to) match {
        case Some(x) => resultScope.regCfg -= scopeFrom -= scopeTo += (from -> x)
        case None => resultScope.regCfg -= scopeFrom -= from
      }
      // change any outgoing edge (to -> any) to (from -> any)
      resultScope.trueCfg.get(to) match {
        case Some(x) => resultScope.trueCfg -= scopeTo += (from -> x)
        case None =>
      }
      // same as trueCfg
      resultScope.falseCfg.get(to) match {
        case Some(x) => resultScope.falseCfg -= scopeTo += (from -> x)
        case None =>
      }
      // resultScope = Scope[CfgBasicBlock](
      //   // remove toBlock, overwrite fromBlock/ toBlock with the new block
      //   basicBlocks = resultScope.basicBlocks - to + (from -> collapsedBlock),
      //   // entry will never be the toBlock, and if it is the fromBlock, the name won't change
      //   entry = resultScope.entry,
      //   // exit will enver be the fromBlock, but if it is the toBlock, the name will change
      //   exit = if (resultScope.exit == to) from else resultScope.exit,
      //   // remove the (from -> to) edge, change any outgoing edge (to -> any) to (from -> any)
      //   // also remove the original (scopeFrom -> scopeTo) edge, do it first to account for scopeFrom == from
      //   regCfg = resultScope.regCfg.get(to) match {
      //     case Some(x) => resultScope.regCfg - scopeFrom - scopeTo + (from -> x)
      //     case None => resultScope.regCfg - scopeFrom - from
      //   },
      //   // change any outgoing edge (to -> any) to (from -> any)
      //   trueCfg = resultScope.trueCfg.get(to) match {
      //     case Some(x) => resultScope.trueCfg - scopeTo + (from -> x)
      //     case None => resultScope.trueCfg
      //   },
      //   // same as trueCfg
      //   falseCfg = resultScope.falseCfg.get(to) match {
      //     case Some(x) => resultScope.falseCfg - scopeTo + (from -> x)
      //     case None => resultScope.falseCfg
      //   }
      // )
    }

    // inDegree = 0 implies never visited before
    // scope.entry initialized to 1 ensures that is never optimized out in a loop
    val inDegree = mutable.HashMap(scope.entry -> 1)
    val visited = mutable.Set.empty[String]

    // compute the indegree of all blocks reachable from the entry block using dfs
    def dfs(start: String): Unit = {
      visited += start
      List(scope.regCfg, scope.falseCfg, scope.trueCfg).foreach(graph =>
        graph.get(start) match {
          case Some(x) =>
            inDegree.update(x, inDegree.getOrElse(x, 0) + 1)
            if (!visited.contains(x)) dfs(x)
          case None =>
        }
      )
    }

    dfs(scope.entry)
    // pprint.pprintln(inDegree)
    // remove all unreachable blocks
    resultScope.basicBlocks.keys.foreach(label =>
      if (!inDegree.contains(label)) {
        // println(s"removed $label")
        resultScope.basicBlocks -= label
        resultScope.regCfg -= label
        resultScope.trueCfg -= label
        resultScope.falseCfg -= label
      }
    )

    // make a copy since we are modifying regCfg
    resultScope.regCfg.toList.foreach({
      case (from, to) =>
        val fromBlock = scope.basicBlocks(from)
        // if scope is valid, iff fromBlock is CfgRegularBlock, outdegree(fromBlock) <= 1
        fromBlock match {
          case RegularBlock(_, _) =>
            if (inDegree(to) <= 1) {
              collapseEdge(from, to)
            }
          case _ =>
        }
    })

    resultScope.toScope
  }

  /**
   * Given a scope, simplifies it by eliminating all useless noop basic blocks, thus saving jump
   * instructions in codegen.
   *
   * @param scope the scope to simplify. Requires scope be maximal (see `simplifyScope`)
   * @return the simplified scope.
   */
  def removeNoOp(scope: CfgScope): CfgScope = {
    // strategy: first find all the blocks that are not noop's ("op" blocks)
    val (fullBlocks, noOpBlocks) = scope.basicBlocks.partition({ case (label, block) =>
      block.isInstanceOf[CfgConditionalBlock] || // conditional blocks are never empty due to condition)
      block.statements.nonEmpty || // by definition
      label == scope.exit || // don't touch the exit,
        // because what successor are we going to collapse it to?
      label == scope.entry // don't touch the entry,
        // because we don't want to mess it up
      // In fact, as long as scope is maximal, we should never need to optimize the entry,
      // and any possible optimizations to the exit should have been done already.
    })

    // strategy: for all the op blocks, find its successor that is not a noop block,
    // and collapse the edge

    // val noOpSuccessorCache

    /**
     * Starting with `block`, finds the first block in its path of successors
     * that is not a no-op block.
     *
     * @param block the starting block to follow
     * @return see above, guaranteed to not be a no-op block.
     */
    @tailrec
    def findNoOpSuccessor(block: String): String = {
      if(fullBlocks.contains(block))
        block
      else
        findNoOpSuccessor(scope.regCfg(block))
    }

    val resultScope = new MutableScope[CfgBasicBlock](Scope(
      basicBlocks = fullBlocks,
      entry = scope.entry,
      exit = scope.exit,
      regCfg = Map.empty,
      trueCfg = Map.empty,
      falseCfg = Map.empty
    ))

    fullBlocks.filter(_._1 != scope.exit).foreach({ case (label, block) =>
      block match {
        case _ : CfgRegularBlock =>
          resultScope.regCfg += (label -> findNoOpSuccessor(scope.regCfg(label)))
        case _ : CfgConditionalBlock =>
          resultScope.trueCfg += (label -> findNoOpSuccessor(scope.trueCfg(label)))
          resultScope.falseCfg += (label -> findNoOpSuccessor(scope.falseCfg(label)))
      }
    })

    resultScope.toScope
  }
}
