import decaf.compile.cfg._
import decaf.compile.ir.Type.{BOOL_ARR, INT}
import decaf.compile.ir._
import org.scalatest.funsuite.AnyFunSuite
import TestUtils._

class CfgVisitor_VisitFieldDeclTest extends AnyFunSuite {
 val scopeLevel = 0
 var blockCounter = 0

 def nextBlockName(): String = {
   blockCounter += 1
   s"block_$blockCounter"
 }

 var tempCounter = -1

 def getNewTemp(): CfgVariable = {
   tempCounter += 1
   CfgVariable(s"temp_$tempCounter", scopeLevel)
 }

 def resolveVariable(string: String): CfgVariable = {
   CfgVariable(string, scopeLevel)
 }


 val scope: ScopeUtil = ScopeUtil(resolveVariable, nextBlockName, getNewTemp)

 test("int a = 3;") {
   val variable = CfgVariable("a", 1)
   assert(
     CfgVisitor.visitFieldDecl(
       IrRegFieldDecl(
         declType = INT,
         identifier = "a",
         initializer = Some(IrInitializer(IrIntLiteral(3, null), null)),
         null
       )) === // this triple equals is the same as ==, but gives better error output for tests
       (
         CfgRegFieldDecl(variable),
         CfgRegAssignStatement(CfgLocation(variable, None), CfgImmediate(3L))
       )
   )
 }
 test("int a;") {
   val variable = CfgVariable("a", 1)
   assert(
     CfgVisitor.visitFieldDecl(
       IrRegFieldDecl(
         declType = INT,
         identifier = "a",
         initializer = None,
         null
       )) ===
       (
         CfgRegFieldDecl(variable),
         CfgRegAssignStatement(CfgLocation(variable, None), CfgImmediate(0L))
       )
   )
 }
 test("bool a[] = {true, true};") {
   assert(
     CfgVisitor.visitFieldDecl(
       IrArrayFieldDecl(
         declType = BOOL_ARR,
         identifier = "a",
         length = 2,
         initializer = Some(IrInitializer(
           IrArrayLiteral(List(IrBoolLiteral(value = true, null),IrBoolLiteral(value = true, null)), null),
           null)),
         location = null
       )) ===
       CfgArrayFieldDecl(
         variable = CfgVariable("a", 0),
         length = 2
       )
   )
 }
 test("bool a[2];") {
   assert(
     CfgVisitor.visitFieldDecl(
       IrArrayFieldDecl(
         declType = BOOL_ARR,
         identifier = "a",
         length = 2,
         initializer = None,
         location = null
       )) ===
       CfgArrayFieldDecl(
         variable = CfgVariable("a", 0),
         length = 2
       )
   )
 }
}
