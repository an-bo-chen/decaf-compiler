import TestUtils._
import decaf.compile.CfgBasicBlock
import decaf.compile.cfg._
import decaf.compile.ir._
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.immutable.TreeMap

class CfgVisitor_simplifyScopeTest extends AnyFunSuite {

 test("should not change one regular block") {
   val result = CfgVisitorUtil.simplifyScope(Scope[CfgBasicBlock](
     basicBlocks = TreeMap(getBlockName(1) -> getTestRegularBlock(1)),
     entry = getBlockName(1),
     exit = getBlockName(1),
     regCfg = TreeMap.empty,
     trueCfg = TreeMap.empty,
     falseCfg = TreeMap.empty
   ))
   assert {
     result === Scope[CfgBasicBlock](
       basicBlocks = TreeMap(getBlockName(1) -> getTestRegularBlock(1)),
       entry = getBlockName(1),
       exit = getBlockName(1),
       regCfg = TreeMap.empty,
       trueCfg = TreeMap.empty,
       falseCfg = TreeMap.empty
     )
   }
 }

 test("should not change one conditional block") {
   val result = CfgVisitorUtil.simplifyScope(Scope[CfgBasicBlock](
     basicBlocks = TreeMap(getBlockName(1) -> getTestConditionalBlock(1)),
     entry = getBlockName(1),
     exit = getBlockName(1),
     regCfg = TreeMap.empty,
     trueCfg = TreeMap.empty,
     falseCfg = TreeMap.empty
   ))
   assert {
     result === Scope[CfgBasicBlock](
       basicBlocks = TreeMap(getBlockName(1) -> getTestConditionalBlock(1)),
       entry = getBlockName(1),
       exit = getBlockName(1),
       regCfg = TreeMap.empty,
       trueCfg = TreeMap.empty,
       falseCfg = TreeMap.empty
     )
   }
 }

 test("should combine blocks in a chain") {
   val result = CfgVisitorUtil.simplifyScope(Scope[CfgBasicBlock](
     basicBlocks = TreeMap(
       getBlockName(1) -> getTestRegularBlock(1),
       getBlockName(2) -> getTestRegularBlock(2),
       getBlockName(3) -> getTestRegularBlock(3),
       getBlockName(4) -> getTestRegularBlock(4)
     ),
     entry = getBlockName(1),
     exit = getBlockName(3),
     regCfg = TreeMap(
       getBlockName(1) -> getBlockName(2),
       getBlockName(2) -> getBlockName(3),
       getBlockName(3) -> getBlockName(4)
     ),
     trueCfg = TreeMap.empty,
     falseCfg = TreeMap.empty
   ))
   assert {
     result === Scope[CfgBasicBlock](
       basicBlocks = TreeMap(getBlockName(1) -> RegularBlock[CfgStatement](
         fieldDecls = List.empty,
         statements = List(
           getTestStatement(1),
           getTestStatement(2),
           getTestStatement(3),
           getTestStatement(4)
         )
       )),
       entry = getBlockName(1),
       exit = getBlockName(1),
       regCfg = TreeMap.empty,
       trueCfg = TreeMap.empty,
       falseCfg = TreeMap.empty
     )
   }
 }

 test("should combine blocks preceding conditional") {
   val result = CfgVisitorUtil.simplifyScope(Scope[CfgBasicBlock](
     basicBlocks = TreeMap(
       getBlockName(1) -> getTestRegularBlock(1),
       getBlockName(2) -> getTestRegularBlock(2),
       getBlockName(3) -> getTestConditionalBlock(3),
       getBlockName(4) -> getTestRegularBlock(4),
       getBlockName(5) -> getTestRegularBlock(5)
     ),
     entry = getBlockName(1),
     exit = getBlockName(5),
     regCfg = TreeMap(
       getBlockName(1) -> getBlockName(2),
       getBlockName(2) -> getBlockName(3)
     ),
     trueCfg = TreeMap(
       getBlockName(3) -> getBlockName(4)
     ),
     falseCfg = TreeMap(
       getBlockName(3) -> getBlockName(5)
     )
   ))
   assert {
     result === Scope[CfgBasicBlock](
       basicBlocks = TreeMap(
         getBlockName(1) -> ConditionalBlock[CfgStatement](
           fieldDecls = List.empty,
           statements = List(
             getTestStatement(1),
             getTestStatement(2),
             getTestStatement(3)
           ),
           condition = CfgImmediate(0)
         ),
         getBlockName(4) -> getTestRegularBlock(4),
         getBlockName(5) -> getTestRegularBlock(5)
       ),
       entry = getBlockName(1),
       exit = getBlockName(5),
       regCfg = TreeMap.empty,
       trueCfg = TreeMap(
         getBlockName(1) -> getBlockName(4)
       ),
       falseCfg = TreeMap(
         getBlockName(1) -> getBlockName(5)
       )
     )
   }
 }

 test("should combine blocks succeeding conditional") {
   val result = CfgVisitorUtil.simplifyScope(Scope[CfgBasicBlock](
     basicBlocks = TreeMap(
       getBlockName(1) -> getTestConditionalBlock(1),
       getBlockName(2) -> getTestRegularBlock(2),
       getBlockName(3) -> getTestRegularBlock(3),
       getBlockName(4) -> getTestRegularBlock(4),
       getBlockName(5) -> getTestRegularBlock(5),
       getBlockName(6) -> getTestRegularBlock(6)
     ),
     entry = getBlockName(1),
     exit = getBlockName(6),
     regCfg = TreeMap(
       getBlockName(2) -> getBlockName(4),
       getBlockName(3) -> getBlockName(4),
       getBlockName(4) -> getBlockName(5),
       getBlockName(5) -> getBlockName(6)
     ),
     trueCfg = TreeMap(
       getBlockName(1) -> getBlockName(2)
     ),
     falseCfg = TreeMap(
       getBlockName(1) -> getBlockName(3)
     )
   ))
   assert {
     result === Scope[CfgBasicBlock](
       basicBlocks = TreeMap(
         getBlockName(1) -> getTestConditionalBlock(1),
         getBlockName(2) -> getTestRegularBlock(2),
         getBlockName(3) -> getTestRegularBlock(3),
         getBlockName(4) -> RegularBlock[CfgStatement](
           fieldDecls = List.empty,
           statements = List(
             getTestStatement(4),
             getTestStatement(5),
             getTestStatement(6)
           )
         )
       ),
       entry = getBlockName(1),
       exit = getBlockName(4),
       regCfg = TreeMap(
         getBlockName(2) -> getBlockName(4),
         getBlockName(3) -> getBlockName(4)
       ),
       trueCfg = TreeMap(
         getBlockName(1) -> getBlockName(2)
       ),
       falseCfg = TreeMap(
         getBlockName(1) -> getBlockName(3)
       )
     )
   }
 }

 test("should combine blocks within true and false branches") {

   val result = CfgVisitorUtil.simplifyScope(Scope[CfgBasicBlock](
     basicBlocks = TreeMap(
       getBlockName(1) -> getTestConditionalBlock(1),
       getBlockName(2) -> getTestRegularBlock(2), // true
       getBlockName(3) -> getTestRegularBlock(3), // true
       getBlockName(4) -> getTestRegularBlock(4), // false
       getBlockName(5) -> getTestRegularBlock(5)  // false
     ),
     entry = getBlockName(1),
     exit = getBlockName(5),
     regCfg = TreeMap(
       getBlockName(2) -> getBlockName(3),
       getBlockName(4) -> getBlockName(5)
     ),
     trueCfg = TreeMap(
       getBlockName(1) -> getBlockName(2)
     ),
     falseCfg = TreeMap(
       getBlockName(1) -> getBlockName(4)
     )
   ))
   assert {
     result === Scope[CfgBasicBlock](
       basicBlocks = TreeMap(
         getBlockName(1) -> getTestConditionalBlock(1),
         getBlockName(2) -> RegularBlock[CfgStatement](
           fieldDecls = List.empty,
           statements = List(
             getTestStatement(2),
             getTestStatement(3)
           )
         ),
         getBlockName(4) -> RegularBlock[CfgStatement](
           fieldDecls = List.empty,
           statements = List(
             getTestStatement(4),
             getTestStatement(5)
           )
         )
       ),
       entry = getBlockName(1),
       exit = getBlockName(4),
       regCfg = TreeMap.empty,
       trueCfg = TreeMap(
         getBlockName(1) -> getBlockName(2)
       ),
       falseCfg = TreeMap(
         getBlockName(1) -> getBlockName(4)
       )
     )
   }
 }

 test("should not change for conditional with another nested conditional in true branch") {

   val result = CfgVisitorUtil.simplifyScope(Scope[CfgBasicBlock](
     basicBlocks = TreeMap(
       getBlockName(1) -> getTestConditionalBlock(1), // condition 1
       getBlockName(2) -> getTestConditionalBlock(2), // conditional 2 in true branch
       getBlockName(3) -> getTestRegularBlock(3),  // true branch for conditional 2
       getBlockName(4) -> getTestRegularBlock(4), // false branch for conditional 2
       getBlockName(5) -> getTestRegularBlock(5) // false branch for conditional 1
     ),
     entry = getBlockName(1),
     exit = getBlockName(3),
     regCfg = TreeMap.empty,
     trueCfg = TreeMap(
       getBlockName(1) -> getBlockName(2),
       getBlockName(2) -> getBlockName(3)
     ),
     falseCfg = TreeMap(
       getBlockName(1) -> getBlockName(5),
       getBlockName(2) -> getBlockName(4)
     )
   ))
   assert {
     result === Scope[CfgBasicBlock](
       basicBlocks = TreeMap(
         getBlockName(1) -> getTestConditionalBlock(1), // condition 1
         getBlockName(2) -> getTestConditionalBlock(2), // conditional 2 in true branch
         getBlockName(3) -> getTestRegularBlock(3), // true branch for conditional 2
         getBlockName(4) -> getTestRegularBlock(4), // false branch for conditional 2
         getBlockName(5) -> getTestRegularBlock(5) // false branch for conditional 1
       ),
       entry = getBlockName(1),
       exit = getBlockName(3),
       regCfg = TreeMap.empty,
       trueCfg = TreeMap(
         getBlockName(1) -> getBlockName(2),
         getBlockName(2) -> getBlockName(3)
       ),
       falseCfg = TreeMap(
         getBlockName(1) -> getBlockName(5),
         getBlockName(2) -> getBlockName(4)
       )
     )
   }
 }

 test("should not change for conditional with another nested conditional in false branch") {

   val result = CfgVisitorUtil.simplifyScope(Scope[CfgBasicBlock](
     basicBlocks = TreeMap(
       getBlockName(1) -> getTestConditionalBlock(1), // condition 1
       getBlockName(2) -> getTestRegularBlock(2), // true branch for conditional 1
       getBlockName(3) -> getTestConditionalBlock(3), // conditional 2 in false branch
       getBlockName(4) -> getTestRegularBlock(4), // true branch for conditional 2
       getBlockName(5) -> getTestRegularBlock(5) // false branch for conditional 2
     ),
     entry = getBlockName(1),
     exit = getBlockName(2),
     regCfg = TreeMap.empty,
     trueCfg = TreeMap(
       getBlockName(1) -> getBlockName(2),
       getBlockName(2) -> getBlockName(4)
     ),
     falseCfg = TreeMap(
       getBlockName(1) -> getBlockName(3),
       getBlockName(2) -> getBlockName(5)
     )
   ))
   assert {
     result === Scope[CfgBasicBlock](
       basicBlocks = TreeMap(
         getBlockName(1) -> getTestConditionalBlock(1), // condition 1
         getBlockName(2) -> getTestRegularBlock(2), // true branch for conditional 1
         getBlockName(3) -> getTestConditionalBlock(3), // conditional 2 in false branch
         getBlockName(4) -> getTestRegularBlock(4), // true branch for conditional 2
         getBlockName(5) -> getTestRegularBlock(5) // false branch for conditional 2
       ),
       entry = getBlockName(1),
       exit = getBlockName(2),
       regCfg = TreeMap.empty,
       trueCfg = TreeMap(
         getBlockName(1) -> getBlockName(2),
         getBlockName(2) -> getBlockName(4)
       ),
       falseCfg = TreeMap(
         getBlockName(1) -> getBlockName(3),
         getBlockName(2) -> getBlockName(5)
       )
     )
   }
 }

 test("should not change for conditional with a nested conditional in true/false branch") {

   val result = CfgVisitorUtil.simplifyScope(Scope[CfgBasicBlock](
     basicBlocks = TreeMap(
       getBlockName(1) -> getTestConditionalBlock(1), // condition 1
       getBlockName(2) -> getTestConditionalBlock(2), // conditional 2 in true branch
       getBlockName(3) -> getTestRegularBlock(3), // true branch for conditional 2
       getBlockName(4) -> getTestRegularBlock(4), // false branch for conditional 2
       getBlockName(5) -> getTestConditionalBlock(5), // conditional 3 in false branch
       getBlockName(6) -> getTestRegularBlock(6), // true branch for conditional 3
       getBlockName(7) -> getTestRegularBlock(7) // false branch for conditional 3
     ),
     entry = getBlockName(1),
     exit = getBlockName(6),
     regCfg = TreeMap.empty,
     trueCfg = TreeMap(
       getBlockName(1) -> getBlockName(2),
       getBlockName(2) -> getBlockName(3),
       getBlockName(5) -> getBlockName(6)
     ),
     falseCfg = TreeMap(
       getBlockName(1) -> getBlockName(5),
       getBlockName(2) -> getBlockName(4),
       getBlockName(5) -> getBlockName(7)
     )
   ))
   assert {
     result === Scope[CfgBasicBlock](
       basicBlocks = TreeMap(
         getBlockName(1) -> getTestConditionalBlock(1), // condition 1
         getBlockName(2) -> getTestConditionalBlock(2), // conditional 2 in true branch
         getBlockName(3) -> getTestRegularBlock(3), // true branch for conditional 2
         getBlockName(4) -> getTestRegularBlock(4), // false branch for conditional 2
         getBlockName(5) -> getTestConditionalBlock(5), // conditional 3 in false branch
         getBlockName(6) -> getTestRegularBlock(6), // true branch for conditional 3
         getBlockName(7) -> getTestRegularBlock(7) // false branch for conditional 3
       ),
       entry = getBlockName(1),
       exit = getBlockName(6),
       regCfg = TreeMap.empty,
       trueCfg = TreeMap(
         getBlockName(1) -> getBlockName(2),
         getBlockName(2) -> getBlockName(3),
         getBlockName(5) -> getBlockName(6)
       ),
       falseCfg = TreeMap(
         getBlockName(1) -> getBlockName(5),
         getBlockName(2) -> getBlockName(4),
         getBlockName(5) -> getBlockName(7)
       )
     )
   }
 }

 test("should not change for simple loop") {

   val result = CfgVisitorUtil.simplifyScope(Scope[CfgBasicBlock](
     basicBlocks = TreeMap(
       getBlockName(1) -> getTestConditionalBlock(1), // loop conditional
       getBlockName(2) -> getTestRegularBlock(2), // loop body
       getBlockName(3) -> getTestRegularBlock(3) // exit
     ),
     entry = getBlockName(1),
     exit = getBlockName(3),
     regCfg = TreeMap(
       getBlockName(2) -> getBlockName(1)
     ),
     trueCfg = TreeMap(
       getBlockName(1) -> getBlockName(2)
     ),
     falseCfg = TreeMap(
       getBlockName(1) -> getBlockName(3)
     )
   ))
   assert {
     result === Scope[CfgBasicBlock](
       basicBlocks = TreeMap(
         getBlockName(1) -> getTestConditionalBlock(1), // loop conditional
         getBlockName(2) -> getTestRegularBlock(2), // loop body
         getBlockName(3) -> getTestRegularBlock(3) // exit
       ),
       entry = getBlockName(1),
       exit = getBlockName(3),
       regCfg = TreeMap(
         getBlockName(2) -> getBlockName(1)
       ),
       trueCfg = TreeMap(
         getBlockName(1) -> getBlockName(2)
       ),
       falseCfg = TreeMap(
         getBlockName(1) -> getBlockName(3)
       )
     )
   }
 }

 test("should combines blocks in loop body") {

   val result = CfgVisitorUtil.simplifyScope(Scope[CfgBasicBlock](
     basicBlocks = TreeMap(
       getBlockName(1) -> getTestConditionalBlock(1), // loop conditional
       getBlockName(2) -> getTestRegularBlock(2), // loop body
       getBlockName(3) -> getTestRegularBlock(3), // loop body
       getBlockName(4) -> getTestRegularBlock(4), // loop body
       getBlockName(5) -> getTestRegularBlock(5), // loop body
       getBlockName(6) -> getTestRegularBlock(6) // exit
     ),
     entry = getBlockName(1),
     exit = getBlockName(6),
     regCfg = TreeMap(
       getBlockName(2) -> getBlockName(3),
       getBlockName(3) -> getBlockName(4),
       getBlockName(4) -> getBlockName(5),
       getBlockName(5) -> getBlockName(1)
     ),
     trueCfg = TreeMap(
       getBlockName(1) -> getBlockName(2)
     ),
     falseCfg = TreeMap(
       getBlockName(1) -> getBlockName(6)
     )
   ))
   assert {
     result === Scope[CfgBasicBlock](
       basicBlocks = TreeMap(
         getBlockName(1) -> getTestConditionalBlock(1), // loop conditional
         getBlockName(2) -> RegularBlock[CfgStatement](
           fieldDecls = List.empty,
           statements = List(
             getTestStatement(2),
             getTestStatement(3),
             getTestStatement(4),
             getTestStatement(5)
           )
         ), // loop body
         getBlockName(6) -> getTestRegularBlock(6) // exit
       ),
       entry = getBlockName(1),
       exit = getBlockName(6),
       regCfg = TreeMap(
         getBlockName(2) -> getBlockName(1)
       ),
       trueCfg = TreeMap(
         getBlockName(1) -> getBlockName(2)
       ),
       falseCfg = TreeMap(
         getBlockName(1) -> getBlockName(6)
       )
     )
   }
 }

 test("should not change for nested loop") {

   val result = CfgVisitorUtil.simplifyScope(Scope[CfgBasicBlock](
     basicBlocks = TreeMap(
       getBlockName(1) -> getTestConditionalBlock(1), // conditional of loop 1
       getBlockName(2) -> getTestConditionalBlock(2), // conditional of loop 2 in body of loop 1
       getBlockName(3) -> getTestRegularBlock(3), // body of loop 2
       getBlockName(4) -> getTestRegularBlock(4), // exit of loop 2
         getBlockName(5) -> getTestRegularBlock(5) // exit of loop 1
     ),
     entry = getBlockName(1),
     exit = getBlockName(5),
     regCfg = TreeMap(
       getBlockName(3) -> getBlockName(2), // body of loop 2 back to conditional of loop 2
       getBlockName(4) -> getBlockName(1) // exit of loop back to conditional of loop 1
     ),
     trueCfg = TreeMap(
       getBlockName(1) -> getBlockName(2),
       getBlockName(2) -> getBlockName(3)
     ),
     falseCfg = TreeMap(
       getBlockName(1) -> getBlockName(5),
       getBlockName(2) -> getBlockName(4)
     )
   ))
   assert {
     result === Scope[CfgBasicBlock](
       basicBlocks = TreeMap(
         getBlockName(1) -> getTestConditionalBlock(1), // conditional of loop 1
         getBlockName(2) -> getTestConditionalBlock(2), // conditional of loop 2 in body of loop 1
         getBlockName(3) -> getTestRegularBlock(3), // body of loop 2
         getBlockName(4) -> getTestRegularBlock(4), // exit of loop 2
         getBlockName(5) -> getTestRegularBlock(5) // exit of loop 1
       ),
       entry = getBlockName(1),
       exit = getBlockName(5),
       regCfg = TreeMap(
         getBlockName(3) -> getBlockName(2), // body of loop 2 back to conditional of loop 2
         getBlockName(4) -> getBlockName(1) // exit of loop back to conditional of loop 1
       ),
       trueCfg = TreeMap(
         getBlockName(1) -> getBlockName(2),
         getBlockName(2) -> getBlockName(3)
       ),
       falseCfg = TreeMap(
         getBlockName(1) -> getBlockName(5),
         getBlockName(2) -> getBlockName(4)
       )
     )
   }
 }

 test("should remove unreachable for a break in loop body") {

   val result = CfgVisitorUtil.simplifyScope(Scope[CfgBasicBlock](
     basicBlocks = TreeMap(
       getBlockName(1) -> getTestConditionalBlock(1), // loop conditional
       getBlockName(2) -> getTestRegularBlock(2), // loop body
       getBlockName(3) -> getTestRegularBlock(3), // break
       getBlockName(4) -> getTestRegularBlock(4) // exit
     ),
     entry = getBlockName(1),
     exit = getBlockName(4),
     regCfg = TreeMap(
       getBlockName(2) -> getBlockName(1), // body back to conditional
       getBlockName(3) -> getBlockName(4) // break to exit
     ),
     trueCfg = TreeMap(
       getBlockName(1) -> getBlockName(2)
     ),
     falseCfg = TreeMap(
       getBlockName(1) -> getBlockName(4)
     )
   ))
   assert {
     result === Scope[CfgBasicBlock](
       basicBlocks = TreeMap(
         getBlockName(1) -> getTestConditionalBlock(1), // loop conditional
         getBlockName(2) -> getTestRegularBlock(2), // loop body
         // getBlockName(3) -> getTestRegularBlock(3), // break
         getBlockName(4) -> getTestRegularBlock(4) // exit
       ),
       entry = getBlockName(1),
       exit = getBlockName(4),
       regCfg = TreeMap(
         getBlockName(2) -> getBlockName(1) // body back to conditional
         // getBlockName(3) -> getBlockName(4) // break to exit
       ),
       trueCfg = TreeMap(
         getBlockName(1) -> getBlockName(2)
       ),
       falseCfg = TreeMap(
         getBlockName(1) -> getBlockName(4)
       )
     )
   }
 }

 test("should not change for a break within a conditional in loop body") {

   val result = CfgVisitorUtil.simplifyScope(Scope[CfgBasicBlock](
     basicBlocks = TreeMap(
       getBlockName(1) -> getTestConditionalBlock(1), // loop conditional
       getBlockName(2) -> getTestConditionalBlock(2), // conditional in loop body
       getBlockName(3) -> getTestRegularBlock(3), // break
       getBlockName(4) -> getTestRegularBlock(4) // exit
     ),
     entry = getBlockName(1),
     exit = getBlockName(4),
     regCfg = TreeMap(
       getBlockName(3) -> getBlockName(4) // break to exit
     ),
     trueCfg = TreeMap(
       getBlockName(1) -> getBlockName(2),
       getBlockName(2) -> getBlockName(3) // if true, then break
     ),
     falseCfg = TreeMap(
       getBlockName(1) -> getBlockName(4),
       getBlockName(2) -> getBlockName(1) // if false, back to loop conditional
     )
   ))
   assert {
     result === Scope[CfgBasicBlock](
       basicBlocks = TreeMap(
         getBlockName(1) -> getTestConditionalBlock(1), // loop conditional
         getBlockName(2) -> getTestConditionalBlock(2), // conditional in loop body
         getBlockName(3) -> getTestRegularBlock(3), // break
         getBlockName(4) -> getTestRegularBlock(4) // exit
       ),
       entry = getBlockName(1),
       exit = getBlockName(4),
       regCfg = TreeMap(
         getBlockName(3) -> getBlockName(4) // break to exit
       ),
       trueCfg = TreeMap(
         getBlockName(1) -> getBlockName(2),
         getBlockName(2) -> getBlockName(3) // if true, then break
       ),
       falseCfg = TreeMap(
         getBlockName(1) -> getBlockName(4),
         getBlockName(2) -> getBlockName(1) // if false, back to loop conditional
       )
     )
   }
 }

 test("should not change for a continue in loop body") {

   val result = CfgVisitorUtil.simplifyScope(Scope[CfgBasicBlock](
     basicBlocks = TreeMap(
       getBlockName(1) -> getTestConditionalBlock(1), // loop conditional
       getBlockName(2) -> getTestRegularBlock(2), // loop body
       getBlockName(3) -> getTestRegularBlock(3), // continue
       getBlockName(4) -> getTestRegularBlock(4) // exit
     ),
     entry = getBlockName(1),
     exit = getBlockName(4),
     regCfg = TreeMap(
       getBlockName(2) -> getBlockName(1), // body back to conditional
       getBlockName(3) -> getBlockName(1) // continue back to conditional
     ),
     trueCfg = TreeMap(
       getBlockName(1) -> getBlockName(2)
     ),
     falseCfg = TreeMap(
       getBlockName(1) -> getBlockName(4)
     )
   ))
   assert {
     result === Scope[CfgBasicBlock](
       basicBlocks = TreeMap(
         getBlockName(1) -> getTestConditionalBlock(1), // loop conditional
         getBlockName(2) -> getTestRegularBlock(2), // loop body
         // getBlockName(3) -> getTestRegularBlock(3), // continue
         getBlockName(4) -> getTestRegularBlock(4) // exit
       ),
       entry = getBlockName(1),
       exit = getBlockName(4),
       regCfg = TreeMap(
         getBlockName(2) -> getBlockName(1) // body back to conditional
         // getBlockName(3) -> getBlockName(1) // continue back to conditional
       ),
       trueCfg = TreeMap(
         getBlockName(1) -> getBlockName(2)
       ),
       falseCfg = TreeMap(
         getBlockName(1) -> getBlockName(4)
       )
     )
   }
 }

 test("should not change for a continue within a conditional in loop body") {

   val result = CfgVisitorUtil.simplifyScope(Scope[CfgBasicBlock](
     basicBlocks = TreeMap(
       getBlockName(1) -> getTestConditionalBlock(1), // loop conditional
       getBlockName(2) -> getTestConditionalBlock(2), // conditional in loop body
       getBlockName(3) -> getTestRegularBlock(3), // continue
       getBlockName(4) -> getTestRegularBlock(4) // exit
     ),
     entry = getBlockName(1),
     exit = getBlockName(4),
     regCfg = TreeMap(
       getBlockName(3) -> getBlockName(4) // continue back to conditional
     ),
     trueCfg = TreeMap(
       getBlockName(1) -> getBlockName(2),
       getBlockName(2) -> getBlockName(3) // if true, then continue
     ),
     falseCfg = TreeMap(
       getBlockName(1) -> getBlockName(4),
       getBlockName(2) -> getBlockName(1) // if false, back to loop conditional
     )
   ))
   assert {
     result === Scope[CfgBasicBlock](
       basicBlocks = TreeMap(
         getBlockName(1) -> getTestConditionalBlock(1), // loop conditional
         getBlockName(2) -> getTestConditionalBlock(2), // conditional in loop body
         getBlockName(3) -> getTestRegularBlock(3), // continue
         getBlockName(4) -> getTestRegularBlock(4) // exit
       ),
       entry = getBlockName(1),
       exit = getBlockName(4),
       regCfg = TreeMap(
         getBlockName(3) -> getBlockName(4) // continue back to conditional
       ),
       trueCfg = TreeMap(
         getBlockName(1) -> getBlockName(2),
         getBlockName(2) -> getBlockName(3) // if true, then continue
       ),
       falseCfg = TreeMap(
         getBlockName(1) -> getBlockName(4),
         getBlockName(2) -> getBlockName(1) // if false, back to loop conditional
       )
     )
   }
 }
}
