import decaf.compile.CfgBasicBlock
import decaf.compile.cfg.ScopeUtil
import decaf.compile.cfg._
import decaf.compile.ir._
import org.scalatest.funsuite.AnyFunSuite

class CfgVisitor_shortCircuitTest extends AnyFunSuite{
 val FINAL_TRUE = "FINAL_TRUE"
 val FINAL_FALSE = "FINAL_FALSE"
 val DEFAULT_SCOPE_LEVEL = 1
 val DUMMY_GLOBALS = Map("a" -> CfgVariable("a", 0))

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

 test("should work on literal: (true)") {
   val result = CfgVisitorUtil.shortCircuit(
     expression = IrBoolLiteral(value = true, null),
     trueBranch = FINAL_TRUE,
     falseBranch = FINAL_FALSE,
     scope = createScopeUtil()
   )
   assert {
     result === Scope[CfgBasicBlock](
       basicBlocks = Map(getBlockName(1) -> ConditionalBlock[CfgStatement](
         fieldDecls = List.empty, statements = List.empty, condition = CfgImmediate(1)
       )),
       entry = getBlockName(1),
       exit = result.exit, // don't care
       regCfg = Map.empty,
       trueCfg = Map(getBlockName(1) -> FINAL_TRUE),
       falseCfg = Map(getBlockName(1) -> FINAL_FALSE)
     )
   }
 }

 test("should work on AND: (false and true)") {
   val result = CfgVisitorUtil.shortCircuit(
     expression = IrBinOpExpr(
       op = LogicalBinOpType.AND,
       leftExpr = IrBoolLiteral(value = false, null),
       rightExpr = IrBoolLiteral(value = true, null),
       null
     ),
     trueBranch = FINAL_TRUE,
     falseBranch = FINAL_FALSE,
     scope = createScopeUtil()
   )
   assert {
     result === Scope[CfgBasicBlock](
       basicBlocks = Map(
         getBlockName(1) -> ConditionalBlock[CfgStatement](
           fieldDecls = List.empty, statements = List.empty, condition = CfgImmediate(1)
         ),
         getBlockName(2) -> ConditionalBlock[CfgStatement](
           fieldDecls = List.empty, statements = List.empty, condition = CfgImmediate(0)
         )
       ),
       entry = getBlockName(2),
       exit = result.exit, // don't care
       regCfg = Map.empty,
       trueCfg = Map(
         getBlockName(1) -> FINAL_TRUE,
         getBlockName(2) -> getBlockName(1)
       ),
       falseCfg = Map(
         getBlockName(1) -> FINAL_FALSE,
         getBlockName(2) -> FINAL_FALSE
       )
     )
   }
 }

 test("should work on OR, nested expressions: ((false or true) and false)") {
   val result = CfgVisitorUtil.shortCircuit(
     expression = IrBinOpExpr(
       op = LogicalBinOpType.AND,
       leftExpr = IrBinOpExpr(
         op = LogicalBinOpType.OR,
         leftExpr = IrBoolLiteral(value = false, null),
         rightExpr = IrBoolLiteral(value = true, null),
         null
       ),
       rightExpr = IrBoolLiteral(value = false, null),
       null
     ),
     trueBranch = FINAL_TRUE,
     falseBranch = FINAL_FALSE,
     scope = createScopeUtil()
   )
   assert {
     result === Scope[CfgBasicBlock](
       basicBlocks = Map(
         getBlockName(1) -> ConditionalBlock[CfgStatement](
           fieldDecls = List.empty, statements = List.empty, condition = CfgImmediate(0)
         ),
         getBlockName(2) -> ConditionalBlock[CfgStatement](
           fieldDecls = List.empty, statements = List.empty, condition = CfgImmediate(1)
         ),
         getBlockName(3) -> ConditionalBlock[CfgStatement](
           fieldDecls = List.empty, statements = List.empty, condition = CfgImmediate(0)
         )
       ),
       entry = getBlockName(3),
       exit = result.exit, // don't care
       regCfg = Map.empty,
       trueCfg = Map(
         getBlockName(3) -> getBlockName(1),
         getBlockName(2) -> getBlockName(1),
         getBlockName(1) -> FINAL_TRUE
       ),
       falseCfg = Map(
         getBlockName(3) -> getBlockName(2),
         getBlockName(2) -> FINAL_FALSE,
         getBlockName(1) -> FINAL_FALSE
       )
     )
   }
 }
}
