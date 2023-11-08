import decaf.compile.CfgBasicBlock
import decaf.compile.cfg._
import decaf.compile.ir._
import org.scalatest.funsuite.AnyFunSuite

class CfgVisitor_shortCircuitOptimBranchTest extends AnyFunSuite {
  val FINAL_TRUE = "FINAL_TRUE"
  val FINAL_FALSE = "FINAL_FALSE"
  val DEFAULT_SCOPE_LEVEL = 1
  val DUMMY_GLOBALS = Map(
    "x" -> CfgVariable("x", 0),
    "y" -> CfgVariable("y", 0),
    "z" -> CfgVariable("z", 0)
  )

  private def getBlockName(number: Int): String = {
    s"block_$number"
  }

  private def getTempName(number: Int): String = {
    s"temp_$number"
  }

  private def createScopeUtil(): ScopeUtil = {
    var lastBlock = 0
    var lastTemp = 0
    ScopeUtil(
      resolveVariable = id => DUMMY_GLOBALS(id),
      getNewBlockName = () => {
        lastBlock += 1
        getBlockName(lastBlock)
      },
      getNewTemp = () => {
        lastTemp += 1
        CfgVariable(
          getTempName(lastTemp),
          number = 1
        )
      }
    )
  }

  test("should work on simple condition (true != false), fallThrough true") {
    val result = CfgVisitorUtil.shortCircuitOptimBranch(
      expression = IrBinOpExpr(
        EqualityBinOpType.NEQ,
        IrBoolLiteral(value = true, null),
        IrBoolLiteral(value = false, null),
        null),
      trueBranch = FINAL_TRUE,
      falseBranch = FINAL_FALSE,
      fallThroughBranch = true,
      scope = createScopeUtil()
    )
    assert {
      result === Scope[CfgBasicBlock](
        basicBlocks = Map(getBlockName(1) -> ConditionalBlock[CfgStatement](
          fieldDecls = List.empty, statements = List.empty,
          condition = CfgBinOpExpr(EqualityBinOpType.EQ, CfgImmediate(1), CfgImmediate(0)) // note inverted
        )),
        entry = getBlockName(1),
        exit = result.exit, // don't care
        regCfg = Map.empty,
        trueCfg = Map(getBlockName(1) -> FINAL_FALSE), // note inverted
        falseCfg = Map(getBlockName(1) -> FINAL_TRUE)
      )
    }
  }

  test("should work on simple condition (true != false), fallThrough false") {
    val result = CfgVisitorUtil.shortCircuitOptimBranch(
      expression = IrBinOpExpr(
        EqualityBinOpType.NEQ,
        IrBoolLiteral(value = true, null),
        IrBoolLiteral(value = false, null),
        null),
      trueBranch = FINAL_TRUE,
      falseBranch = FINAL_FALSE,
      fallThroughBranch = false,
      scope = createScopeUtil()
    )
    assert {
      result === Scope[CfgBasicBlock](
        basicBlocks = Map(getBlockName(1) -> ConditionalBlock[CfgStatement](
          fieldDecls = List.empty, statements = List.empty,
          condition = CfgBinOpExpr(EqualityBinOpType.NEQ, CfgImmediate(1), CfgImmediate(0)) // note not inverted
        )),
        entry = getBlockName(1),
        exit = result.exit, // don't care
        regCfg = Map.empty,
        trueCfg = Map(getBlockName(1) -> FINAL_TRUE), // note not inverted
        falseCfg = Map(getBlockName(1) -> FINAL_FALSE)
      )
    }
  }

  test("should work on NOT, (! a()) fallThrough branch true") {
    val result = CfgVisitorUtil.shortCircuitOptimBranch(
      expression = IrUnaryOpExpr(
        UnaryOpType.NOT,
        IrFunctionCallExpr("a", List.empty, null),
        null),
      trueBranch = FINAL_TRUE,
      falseBranch = FINAL_FALSE,
      fallThroughBranch = true,
      scope = createScopeUtil()
    )
    assert {
      result === Scope[CfgBasicBlock](
        basicBlocks = Map(getBlockName(1) -> ConditionalBlock[CfgStatement](
          fieldDecls = List.empty, statements = List(
            CfgRegAssignStatement(CfgLocation(CfgVariable("temp_2", 1), None), CfgFunctionCallExpr("a", List.empty))
          ),
          condition = CfgVariable("temp_2", 1)
        )),
        entry = getBlockName(1),
        exit = result.exit, // don't care
        regCfg = Map.empty,
        trueCfg = Map(getBlockName(1) -> FINAL_FALSE),
        falseCfg = Map(getBlockName(1) -> FINAL_TRUE)
      )
    }
  }

  test("should work on NOT, (!(x == 2)) fallThrough branch false") {
    val result = CfgVisitorUtil.shortCircuitOptimBranch(
      expression = IrUnaryOpExpr(
        UnaryOpType.NOT,
        IrBinOpExpr(EqualityBinOpType.EQ,
          IrVarReadExpr(IrLocation("x", None, null), null),
          IrIntLiteral(2, null),
          null),
        null),
      trueBranch = FINAL_TRUE,
      falseBranch = FINAL_FALSE,
      fallThroughBranch = false,
      scope = createScopeUtil()
    )
    assert {
      result === Scope[CfgBasicBlock](
        basicBlocks = Map(getBlockName(1) -> ConditionalBlock[CfgStatement](
          fieldDecls = List.empty, statements = List.empty,
          condition = CfgBinOpExpr(EqualityBinOpType.NEQ, CfgVariable("x", 0), CfgImmediate(2)) // not inverted
        )),
        entry = getBlockName(1),
        exit = result.exit, // don't care
        regCfg = Map.empty,
        trueCfg = Map(getBlockName(1) -> FINAL_TRUE),
        falseCfg = Map(getBlockName(1) -> FINAL_FALSE)
      )
    }
  }

  test("should work on AND: (x > 2 && y <= 3) fallThrough true") {
    val result = CfgVisitorUtil.shortCircuitOptimBranch(
      expression = IrBinOpExpr(
        op = LogicalBinOpType.AND,
        leftExpr = IrBinOpExpr(RelationalBinOpType.GT,
          IrVarReadExpr(IrLocation("x", None, null), null),
          IrIntLiteral(2, null),
          null),
        rightExpr = IrBinOpExpr(RelationalBinOpType.LTE,
          IrVarReadExpr(IrLocation("y", None, null), null),
          IrIntLiteral(3, null),
          null),
        null
      ),
      trueBranch = FINAL_TRUE,
      falseBranch = FINAL_FALSE,
      fallThroughBranch = true,
      scope = createScopeUtil()
    )
    assert {
      result === Scope[CfgBasicBlock](
        basicBlocks = Map(
          // switched number order because shortCircuit has to visit the last condition, first
          // hence the numbering
          getBlockName(2) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty, statements = List.empty,
            condition = CfgBinOpExpr(RelationalBinOpType.LTE, CfgVariable("x", 0), CfgImmediate(2)) // inverted
          ),
          getBlockName(1) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty, statements = List.empty,
            condition = CfgBinOpExpr(RelationalBinOpType.GT, CfgVariable("y", 0), CfgImmediate(3)) // inverted
          )
        ),
        entry = getBlockName(2),
        exit = result.exit, // don't care
        regCfg = Map.empty,
        trueCfg = Map(
          getBlockName(1) -> FINAL_FALSE, // inverted
          getBlockName(2) -> FINAL_FALSE
        ),
        falseCfg = Map(
          getBlockName(1) -> FINAL_TRUE, // inverted
          getBlockName(2) -> getBlockName(1)
        )
      )
    }
  }

  test("should work on AND: (x > 2 && y <= 3) fallThrough false") {
    val result = CfgVisitorUtil.shortCircuitOptimBranch(
      expression = IrBinOpExpr(
        op = LogicalBinOpType.AND,
        leftExpr = IrBinOpExpr(RelationalBinOpType.GT,
          IrVarReadExpr(IrLocation("x", None, null), null),
          IrIntLiteral(2, null),
          null),
        rightExpr = IrBinOpExpr(RelationalBinOpType.LTE,
          IrVarReadExpr(IrLocation("y", None, null), null),
          IrIntLiteral(3, null),
          null),
        null
      ),
      trueBranch = FINAL_TRUE,
      falseBranch = FINAL_FALSE,
      fallThroughBranch = false,
      scope = createScopeUtil()
    )
    assert {
      result === Scope[CfgBasicBlock](
        basicBlocks = Map(
          // switched number order because shortCircuit has to visit the last condition, first
          // hence the numbering
          getBlockName(2) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty, statements = List.empty,
            condition = CfgBinOpExpr(RelationalBinOpType.LTE, CfgVariable("x", 0), CfgImmediate(2)) // inverted
          ),
          getBlockName(1) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty, statements = List.empty,
            condition = CfgBinOpExpr(RelationalBinOpType.LTE, CfgVariable("y", 0), CfgImmediate(3)) // not inverted
          )
        ),
        entry = getBlockName(2),
        exit = result.exit, // don't care
        regCfg = Map.empty,
        trueCfg = Map(
          getBlockName(1) -> FINAL_TRUE, // not inverted
          getBlockName(2) -> FINAL_FALSE
        ),
        falseCfg = Map(
          getBlockName(1) -> FINAL_FALSE, // not inverted
          getBlockName(2) -> getBlockName(1)
        )
      )
    }
  }

  test("should work on OR: (x > 2 || y <= 3) fallThrough true") {
    val result = CfgVisitorUtil.shortCircuitOptimBranch(
      expression = IrBinOpExpr(
        op = LogicalBinOpType.OR,
        leftExpr = IrBinOpExpr(RelationalBinOpType.GT,
          IrVarReadExpr(IrLocation("x", None, null), null),
          IrIntLiteral(2, null),
          null),
        rightExpr = IrBinOpExpr(RelationalBinOpType.LTE,
          IrVarReadExpr(IrLocation("y", None, null), null),
          IrIntLiteral(3, null),
          null),
        null
      ),
      trueBranch = FINAL_TRUE,
      falseBranch = FINAL_FALSE,
      fallThroughBranch = true,
      scope = createScopeUtil()
    )
    assert {
      result === Scope[CfgBasicBlock](
        basicBlocks = Map(
          // switched number order because shortCircuit has to visit the last condition, first
          // hence the numbering
          getBlockName(2) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty, statements = List.empty,
            condition = CfgBinOpExpr(RelationalBinOpType.GT, CfgVariable("x", 0), CfgImmediate(2)) // not inverted
          ),
          getBlockName(1) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty, statements = List.empty,
            condition = CfgBinOpExpr(RelationalBinOpType.GT, CfgVariable("y", 0), CfgImmediate(3)) // inverted
          )
        ),
        entry = getBlockName(2),
        exit = result.exit, // don't care
        regCfg = Map.empty,
        trueCfg = Map(
          getBlockName(1) -> FINAL_FALSE, // inverted
          getBlockName(2) -> FINAL_TRUE
        ),
        falseCfg = Map(
          getBlockName(1) -> FINAL_TRUE, // inverted
          getBlockName(2) -> getBlockName(1)
        )
      )
    }
  }

  test("should work on OR: (x > 2 || y <= 3) fallThrough false") {
    val result = CfgVisitorUtil.shortCircuitOptimBranch(
      expression = IrBinOpExpr(
        op = LogicalBinOpType.OR,
        leftExpr = IrBinOpExpr(RelationalBinOpType.GT,
          IrVarReadExpr(IrLocation("x", None, null), null),
          IrIntLiteral(2, null),
          null),
        rightExpr = IrBinOpExpr(RelationalBinOpType.LTE,
          IrVarReadExpr(IrLocation("y", None, null), null),
          IrIntLiteral(3, null),
          null),
        null
      ),
      trueBranch = FINAL_TRUE,
      falseBranch = FINAL_FALSE,
      fallThroughBranch = false,
      scope = createScopeUtil()
    )
    assert {
      result === Scope[CfgBasicBlock](
        basicBlocks = Map(
          // switched number order because shortCircuit has to visit the last condition, first
          // hence the numbering
          getBlockName(2) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty, statements = List.empty,
            condition = CfgBinOpExpr(RelationalBinOpType.GT, CfgVariable("x", 0), CfgImmediate(2)) // not inverted
          ),
          getBlockName(1) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty, statements = List.empty,
            condition = CfgBinOpExpr(RelationalBinOpType.LTE, CfgVariable("y", 0), CfgImmediate(3)) // not inverted
          )
        ),
        entry = getBlockName(2),
        exit = result.exit, // don't care
        regCfg = Map.empty,
        trueCfg = Map(
          getBlockName(1) -> FINAL_TRUE, // not inverted
          getBlockName(2) -> FINAL_TRUE
        ),
        falseCfg = Map(
          getBlockName(1) -> FINAL_FALSE, // not inverted
          getBlockName(2) -> getBlockName(1)
        )
      )
    }
  }

  test("should work on NOT NOT: !!(x >= 2) fallThrough true") {
    val result = CfgVisitorUtil.shortCircuitOptimBranch(
      expression = IrUnaryOpExpr(
        op = UnaryOpType.NOT,
        expr = IrUnaryOpExpr(
          op = UnaryOpType.NOT,
          expr = IrBinOpExpr(RelationalBinOpType.GTE,
            IrVarReadExpr(IrLocation("x", None, null), null),
            IrIntLiteral(2, null),
            null),
          null
        ),
        null
      ),
      trueBranch = FINAL_TRUE,
      falseBranch = FINAL_FALSE,
      fallThroughBranch = true,
      scope = createScopeUtil()
    )
    assert {
      result === Scope[CfgBasicBlock](
        basicBlocks = Map(
          // switched number order because shortCircuit has to visit the last condition, first
          // hence the numbering
          getBlockName(1) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty, statements = List.empty,
            condition = CfgBinOpExpr(RelationalBinOpType.LT, CfgVariable("x", 0), CfgImmediate(2)) // inverted
          )
        ),
        entry = getBlockName(1),
        exit = result.exit, // don't care
        regCfg = Map.empty,
        trueCfg = Map(
          getBlockName(1) -> FINAL_FALSE
        ),
        falseCfg = Map(
          getBlockName(1) -> FINAL_TRUE
        )
      )
    }
  }

  test("should work on NOT NOT: !!(x >= 2) fallThrough false") {
    val result = CfgVisitorUtil.shortCircuitOptimBranch(
      expression = IrUnaryOpExpr(
        op = UnaryOpType.NOT,
        expr = IrUnaryOpExpr(
          op = UnaryOpType.NOT,
          expr = IrBinOpExpr(RelationalBinOpType.GTE,
            IrVarReadExpr(IrLocation("x", None, null), null),
            IrIntLiteral(2, null),
            null),
          null
        ),
        null
      ),
      trueBranch = FINAL_TRUE,
      falseBranch = FINAL_FALSE,
      fallThroughBranch = false,
      scope = createScopeUtil()
    )
    assert {
      result === Scope[CfgBasicBlock](
        basicBlocks = Map(
          // switched number order because shortCircuit has to visit the last condition, first
          // hence the numbering
          getBlockName(1) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty, statements = List.empty,
            condition = CfgBinOpExpr(RelationalBinOpType.GTE, CfgVariable("x", 0), CfgImmediate(2)) // not inverted
          )
        ),
        entry = getBlockName(1),
        exit = result.exit, // don't care
        regCfg = Map.empty,
        trueCfg = Map(
          getBlockName(1) -> FINAL_TRUE
        ),
        falseCfg = Map(
          getBlockName(1) -> FINAL_FALSE
        )
      )
    }
  }

  test("should work on NOT AND: !(x > 2 && y <= 3) fallThrough true") {
    val result = CfgVisitorUtil.shortCircuitOptimBranch(
      expression = IrUnaryOpExpr(
        op = UnaryOpType.NOT,
        expr = IrBinOpExpr(
          op = LogicalBinOpType.AND,
          leftExpr = IrBinOpExpr(RelationalBinOpType.GT,
            IrVarReadExpr(IrLocation("x", None, null), null),
            IrIntLiteral(2, null),
            null),
          rightExpr = IrBinOpExpr(RelationalBinOpType.LTE,
            IrVarReadExpr(IrLocation("y", None, null), null),
            IrIntLiteral(3, null),
            null),
          null
        ),
        null
      ),
      trueBranch = FINAL_TRUE,
      falseBranch = FINAL_FALSE,
      fallThroughBranch = true,
      scope = createScopeUtil()
    )
    assert {
      result === Scope[CfgBasicBlock](
        basicBlocks = Map(
          // switched number order because shortCircuit has to visit the last condition, first
          // hence the numbering
          getBlockName(2) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty, statements = List.empty,
            condition = CfgBinOpExpr(RelationalBinOpType.LTE, CfgVariable("x", 0), CfgImmediate(2)) // inverted
          ),
          getBlockName(1) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty, statements = List.empty,
            condition = CfgBinOpExpr(RelationalBinOpType.LTE, CfgVariable("y", 0), CfgImmediate(3)) // not inverted
          )
        ),
        entry = getBlockName(2),
        exit = result.exit, // don't care
        regCfg = Map.empty,
        trueCfg = Map(
          getBlockName(1) -> FINAL_FALSE, // inverted
          getBlockName(2) -> FINAL_TRUE
        ),
        falseCfg = Map(
          getBlockName(1) -> FINAL_TRUE, // inverted
          getBlockName(2) -> getBlockName(1)
        )
      )
    }
  }

  test("should work on NOT AND: !(x > 2 && y <= 3) fallThrough false") {
    val result = CfgVisitorUtil.shortCircuitOptimBranch(
      expression = IrUnaryOpExpr(
        op = UnaryOpType.NOT,
        expr = IrBinOpExpr(
          op = LogicalBinOpType.AND,
          leftExpr = IrBinOpExpr(RelationalBinOpType.GT,
            IrVarReadExpr(IrLocation("x", None, null), null),
            IrIntLiteral(2, null),
            null),
          rightExpr = IrBinOpExpr(RelationalBinOpType.LTE,
            IrVarReadExpr(IrLocation("y", None, null), null),
            IrIntLiteral(3, null),
            null),
          null
        ),
        null
      ),
      trueBranch = FINAL_TRUE,
      falseBranch = FINAL_FALSE,
      fallThroughBranch = false,
      scope = createScopeUtil()
    )
    assert {
      result === Scope[CfgBasicBlock](
        basicBlocks = Map(
          // switched number order because shortCircuit has to visit the last condition, first
          // hence the numbering
          getBlockName(2) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty, statements = List.empty,
            condition = CfgBinOpExpr(RelationalBinOpType.LTE, CfgVariable("x", 0), CfgImmediate(2)) // inverted
          ),
          getBlockName(1) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty, statements = List.empty,
            condition = CfgBinOpExpr(RelationalBinOpType.GT, CfgVariable("y", 0), CfgImmediate(3)) // not inverted
          )
        ),
        entry = getBlockName(2),
        exit = result.exit, // don't care
        regCfg = Map.empty,
        trueCfg = Map(
          getBlockName(1) -> FINAL_TRUE, // inverted
          getBlockName(2) -> FINAL_TRUE
        ),
        falseCfg = Map(
          getBlockName(1) -> FINAL_FALSE, // inverted
          getBlockName(2) -> getBlockName(1)
        )
      )
    }
  }

  test("should work on NOT OR: !(x > 2 || y <= 3) fallThrough true") {
    val result = CfgVisitorUtil.shortCircuitOptimBranch(
      expression = IrUnaryOpExpr(
        op = UnaryOpType.NOT,
        expr = IrBinOpExpr(
          op = LogicalBinOpType.OR,
          leftExpr = IrBinOpExpr(RelationalBinOpType.GT,
            IrVarReadExpr(IrLocation("x", None, null), null),
            IrIntLiteral(2, null),
            null),
          rightExpr = IrBinOpExpr(RelationalBinOpType.LTE,
            IrVarReadExpr(IrLocation("y", None, null), null),
            IrIntLiteral(3, null),
            null),
          null
        ),
        null
      ),
      trueBranch = FINAL_TRUE,
      falseBranch = FINAL_FALSE,
      fallThroughBranch = true,
      scope = createScopeUtil()
    )
    assert {
      result === Scope[CfgBasicBlock](
        basicBlocks = Map(
          // switched number order because shortCircuit has to visit the last condition, first
          // hence the numbering
          getBlockName(2) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty, statements = List.empty,
            condition = CfgBinOpExpr(RelationalBinOpType.GT, CfgVariable("x", 0), CfgImmediate(2)) // not inverted
          ),
          getBlockName(1) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty, statements = List.empty,
            condition = CfgBinOpExpr(RelationalBinOpType.LTE, CfgVariable("y", 0), CfgImmediate(3)) // not inverted
          )
        ),
        entry = getBlockName(2),
        exit = result.exit, // don't care
        regCfg = Map.empty,
        trueCfg = Map(
          getBlockName(1) -> FINAL_FALSE, // inverted
          getBlockName(2) -> FINAL_FALSE
        ),
        falseCfg = Map(
          getBlockName(1) -> FINAL_TRUE, // inverted
          getBlockName(2) -> getBlockName(1)
        )
      )
    }
  }

  test("should work on NOT OR: !(x > 2 || y <= 3) fallThrough false") {
    val result = CfgVisitorUtil.shortCircuitOptimBranch(
      expression = IrUnaryOpExpr(
        op = UnaryOpType.NOT,
        expr = IrBinOpExpr(
          op = LogicalBinOpType.OR,
          leftExpr = IrBinOpExpr(RelationalBinOpType.GT,
            IrVarReadExpr(IrLocation("x", None, null), null),
            IrIntLiteral(2, null),
            null),
          rightExpr = IrBinOpExpr(RelationalBinOpType.LTE,
            IrVarReadExpr(IrLocation("y", None, null), null),
            IrIntLiteral(3, null),
            null),
          null
        ),
        null
      ),
      trueBranch = FINAL_TRUE,
      falseBranch = FINAL_FALSE,
      fallThroughBranch = false,
      scope = createScopeUtil()
    )
    assert {
      result === Scope[CfgBasicBlock](
        basicBlocks = Map(
          // switched number order because shortCircuit has to visit the last condition, first
          // hence the numbering
          getBlockName(2) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty, statements = List.empty,
            condition = CfgBinOpExpr(RelationalBinOpType.GT, CfgVariable("x", 0), CfgImmediate(2)) // not inverted
          ),
          getBlockName(1) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty, statements = List.empty,
            condition = CfgBinOpExpr(RelationalBinOpType.GT, CfgVariable("y", 0), CfgImmediate(3)) // inverted
          )
        ),
        entry = getBlockName(2),
        exit = result.exit, // don't care
        regCfg = Map.empty,
        trueCfg = Map(
          getBlockName(1) -> FINAL_TRUE, // inverted
          getBlockName(2) -> FINAL_FALSE
        ),
        falseCfg = Map(
          getBlockName(1) -> FINAL_FALSE, // inverted
          getBlockName(2) -> getBlockName(1)
        )
      )
    }
  }

  test("should work on AND OR: ((x > 2 || y <= 3) && z) fallThrough true") {
    val result = CfgVisitorUtil.shortCircuitOptimBranch(
      expression = IrBinOpExpr(
        op = LogicalBinOpType.AND,
        leftExpr = IrBinOpExpr(
          op = LogicalBinOpType.OR,
          leftExpr = IrBinOpExpr(RelationalBinOpType.GT,
            IrVarReadExpr(IrLocation("x", None, null), null),
            IrIntLiteral(2, null),
            null),
          rightExpr = IrBinOpExpr(RelationalBinOpType.LTE,
            IrVarReadExpr(IrLocation("y", None, null), null),
            IrIntLiteral(3, null),
            null),
          null
        ),
        rightExpr = IrVarReadExpr(IrLocation("z", None, null), null),
        null
      ),
      trueBranch = FINAL_TRUE,
      falseBranch = FINAL_FALSE,
      fallThroughBranch = true,
      scope = createScopeUtil()
    )
    assert {
      result === Scope[CfgBasicBlock](
        basicBlocks = Map(
          // switched number order because shortCircuit has to visit the last condition, first
          // hence the numbering
          getBlockName(1) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty, statements = List.empty,
            condition = CfgUnaryOpExpr(UnaryOpType.NOT, CfgVariable("z", 0)) // inverted
          ),
          getBlockName(2) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty, statements = List.empty,
            condition = CfgBinOpExpr(RelationalBinOpType.GT, CfgVariable("y", 0), CfgImmediate(3)) // inverted
          ),
          getBlockName(3) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty, statements = List.empty,
            condition = CfgBinOpExpr(RelationalBinOpType.GT, CfgVariable("x", 0), CfgImmediate(2)) // not inverted
          )
        ),
        entry = getBlockName(3),
        exit = result.exit, // don't care
        regCfg = Map.empty,
        trueCfg = Map(
          getBlockName(1) -> FINAL_FALSE,
          getBlockName(2) -> FINAL_FALSE,
          getBlockName(3) -> getBlockName(1)
        ),
        falseCfg = Map(
          getBlockName(1) -> FINAL_TRUE,
          getBlockName(2) -> getBlockName(1),
          getBlockName(3) -> getBlockName(2)
        )
      )
    }
  }

  test("should work on OR AND: (z || (x > 2 && y <= 3)) fallThrough true") {
    val result = CfgVisitorUtil.shortCircuitOptimBranch(
      expression = IrBinOpExpr(
        op = LogicalBinOpType.OR,
        leftExpr = IrVarReadExpr(IrLocation("z", None, null), null),
        rightExpr = IrBinOpExpr(
          op = LogicalBinOpType.AND,
          leftExpr = IrBinOpExpr(RelationalBinOpType.GT,
            IrVarReadExpr(IrLocation("x", None, null), null),
            IrIntLiteral(2, null),
            null),
          rightExpr = IrBinOpExpr(RelationalBinOpType.LTE,
            IrVarReadExpr(IrLocation("y", None, null), null),
            IrIntLiteral(3, null),
            null),
          null
        ),
        null
      ),
      trueBranch = FINAL_TRUE,
      falseBranch = FINAL_FALSE,
      fallThroughBranch = true,
      scope = createScopeUtil()
    )
    assert {
      result === Scope[CfgBasicBlock](
        basicBlocks = Map(
          // switched number order because shortCircuit has to visit the last condition, first
          // hence the numbering
          getBlockName(3) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty, statements = List.empty,
            condition = CfgVariable("z", 0) // not inverted
          ),
          getBlockName(1) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty, statements = List.empty,
            condition = CfgBinOpExpr(RelationalBinOpType.GT, CfgVariable("y", 0), CfgImmediate(3)) // inverted
          ),
          getBlockName(2) -> ConditionalBlock[CfgStatement](
            fieldDecls = List.empty, statements = List.empty,
            condition = CfgBinOpExpr(RelationalBinOpType.LTE, CfgVariable("x", 0), CfgImmediate(2)) // inverted
          )
        ),
        entry = getBlockName(3),
        exit = result.exit, // don't care
        regCfg = Map.empty,
        trueCfg = Map(
          getBlockName(1) -> FINAL_FALSE,
          getBlockName(2) -> FINAL_FALSE,
          getBlockName(3) -> FINAL_TRUE
        ),
        falseCfg = Map(
          getBlockName(1) -> FINAL_TRUE,
          getBlockName(2) -> getBlockName(1),
          getBlockName(3) -> getBlockName(2)
        )
      )
    }
  }
}
