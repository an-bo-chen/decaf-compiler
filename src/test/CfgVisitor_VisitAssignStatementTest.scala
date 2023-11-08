import decaf.compile.cfg._
import decaf.compile.ir._
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.immutable.TreeMap
class CfgVisitor_VisitAssignStatementTest extends AnyFunSuite {
 val scopeLevel = 2
 var blockCounter = 0

 def nextBlockName(): String = {
   blockCounter += 1
   s"block_$blockCounter"
 }

 def getNewTemp(): CfgVariable = {
   CfgVariable(s"_0", scopeLevel)
 }

 def resolveVariable(string: String): CfgVariable = {
   CfgVariable(string, scopeLevel)
 }

 val scope: ScopeUtil = ScopeUtil((x) => resolveVariable(x), () => nextBlockName(), () => getNewTemp())

 test("int a; a = 1;") {
   val assignBlock = CfgVisitor.visitAssignStatement(
       IrAssignStatement(
         assignLocation = IrLocation("a", None, null),
         assignOp = AssignOpType.EQUAL,
         assignExpr = IrIntLiteral(1L, null),
         null
       ),
       scope.copy()
     )

     val statements = assignBlock.statements
     val to = CfgLocation(CfgVariable("a", 2), None)
     assert(
       statements === List(
         CfgRegAssignStatement(to, CfgImmediate(1L))
       )
     )
 }
}
