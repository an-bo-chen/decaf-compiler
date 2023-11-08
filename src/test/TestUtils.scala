import decaf.compile.cfg._
import decaf.compile._
import decaf.compile.ir.{ArithBinOpType, RelationalBinOpType, UnaryOpType}

object TestUtils {
  def getVariable(number: Int): CfgVariable = CfgVariable(
    identifier = s"$number",
    number = 0
  )

  // to test that simplifyScope is actually combining the statements, we only need
  // one test statement in each block passed in
  def getTestStatement(number: Int): CfgReturnStatement =
    CfgReturnStatement(Some(getVariable(number)))

  def getTestRegularBlock(number: Int): CfgRegularBlock = RegularBlock[CfgStatement](
    fieldDecls = List.empty,
    statements = List(getTestStatement(number))
  )

  def getTestConditionalBlock(number: Int): CfgConditionalBlock = ConditionalBlock[CfgStatement](
    fieldDecls = List.empty,
    statements = List(getTestStatement(number)),
    condition = CfgImmediate(0)
  )

  def getBlockName(number: Int): String = {
    s"block_$number"
  }

  def getUsesStatement(uses: Iterable[Int]): CfgStatement = {
    CfgFunctionCallStatement(
      "",
      uses.map(num => CfgFunctionCallValueParam(getVariable(num))).toList
    )
  }

  def getAssignUsesStatement(assignTo: Int, uses: Iterable[Int]): CfgRegAssignStatement = {
    CfgRegAssignStatement(
      to = CfgLocation(getVariable(assignTo), None),
      value = CfgFunctionCallExpr(
        "func",
        uses.map(num => CfgFunctionCallValueParam(getVariable(num))).toList
      )
    )
  }

  def getExpressionUses(uses: Iterable[Int]): CfgCondExpression = {
    val usesVar = uses.map(getVariable).toList
    uses.size match {
      case 0 =>
        CfgImmediate(0L)
      case 1 =>
        CfgUnaryOpExpr(UnaryOpType.NOT, usesVar.head)
      case 2 =>
        CfgBinOpExpr(RelationalBinOpType.GT, usesVar.head, usesVar.last)
      case _ =>
        throw new IllegalArgumentException("uses must have size at most 2")
    }
  }

  /**
   * uses must have at most 2 values
   * */
  def getAssignUsesStatement_NoFunction(assignTo: Int, uses: Iterable[Int]): CfgRegAssignStatement = {
    val assignValue: CfgExpression = getExpressionUses(uses)
    CfgRegAssignStatement(CfgLocation(getVariable(assignTo), None), assignValue)
  }

  def getAssignUsesIndex_NoFunction(assignTo: Int, index: CfgValue, uses: Iterable[Int]): CfgRegAssignStatement = {
    val assignValue: CfgExpression = getExpressionUses(uses)
    CfgRegAssignStatement(CfgLocation(getVariable(assignTo), Some(index)), assignValue)
  }
}
