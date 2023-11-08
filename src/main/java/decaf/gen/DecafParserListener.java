// Generated from java-escape by ANTLR 4.11.1
package decaf.gen;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link DecafParser}.
 */
public interface DecafParserListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link DecafParser#program}.
	 * @param ctx the parse tree
	 */
	void enterProgram(DecafParser.ProgramContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#program}.
	 * @param ctx the parse tree
	 */
	void exitProgram(DecafParser.ProgramContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#import_decl}.
	 * @param ctx the parse tree
	 */
	void enterImport_decl(DecafParser.Import_declContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#import_decl}.
	 * @param ctx the parse tree
	 */
	void exitImport_decl(DecafParser.Import_declContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#field_decl}.
	 * @param ctx the parse tree
	 */
	void enterField_decl(DecafParser.Field_declContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#field_decl}.
	 * @param ctx the parse tree
	 */
	void exitField_decl(DecafParser.Field_declContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#field_decl_sub}.
	 * @param ctx the parse tree
	 */
	void enterField_decl_sub(DecafParser.Field_decl_subContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#field_decl_sub}.
	 * @param ctx the parse tree
	 */
	void exitField_decl_sub(DecafParser.Field_decl_subContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#field_decl_arr}.
	 * @param ctx the parse tree
	 */
	void enterField_decl_arr(DecafParser.Field_decl_arrContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#field_decl_arr}.
	 * @param ctx the parse tree
	 */
	void exitField_decl_arr(DecafParser.Field_decl_arrContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#type}.
	 * @param ctx the parse tree
	 */
	void enterType(DecafParser.TypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#type}.
	 * @param ctx the parse tree
	 */
	void exitType(DecafParser.TypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#int_literal}.
	 * @param ctx the parse tree
	 */
	void enterInt_literal(DecafParser.Int_literalContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#int_literal}.
	 * @param ctx the parse tree
	 */
	void exitInt_literal(DecafParser.Int_literalContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#initializer}.
	 * @param ctx the parse tree
	 */
	void enterInitializer(DecafParser.InitializerContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#initializer}.
	 * @param ctx the parse tree
	 */
	void exitInitializer(DecafParser.InitializerContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#literal}.
	 * @param ctx the parse tree
	 */
	void enterLiteral(DecafParser.LiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#literal}.
	 * @param ctx the parse tree
	 */
	void exitLiteral(DecafParser.LiteralContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#array_literal}.
	 * @param ctx the parse tree
	 */
	void enterArray_literal(DecafParser.Array_literalContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#array_literal}.
	 * @param ctx the parse tree
	 */
	void exitArray_literal(DecafParser.Array_literalContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#method_decl}.
	 * @param ctx the parse tree
	 */
	void enterMethod_decl(DecafParser.Method_declContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#method_decl}.
	 * @param ctx the parse tree
	 */
	void exitMethod_decl(DecafParser.Method_declContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#decl_parameter}.
	 * @param ctx the parse tree
	 */
	void enterDecl_parameter(DecafParser.Decl_parameterContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#decl_parameter}.
	 * @param ctx the parse tree
	 */
	void exitDecl_parameter(DecafParser.Decl_parameterContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#block}.
	 * @param ctx the parse tree
	 */
	void enterBlock(DecafParser.BlockContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#block}.
	 * @param ctx the parse tree
	 */
	void exitBlock(DecafParser.BlockContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#statement}.
	 * @param ctx the parse tree
	 */
	void enterStatement(DecafParser.StatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#statement}.
	 * @param ctx the parse tree
	 */
	void exitStatement(DecafParser.StatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#assign_statement}.
	 * @param ctx the parse tree
	 */
	void enterAssign_statement(DecafParser.Assign_statementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#assign_statement}.
	 * @param ctx the parse tree
	 */
	void exitAssign_statement(DecafParser.Assign_statementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#method_call_statement}.
	 * @param ctx the parse tree
	 */
	void enterMethod_call_statement(DecafParser.Method_call_statementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#method_call_statement}.
	 * @param ctx the parse tree
	 */
	void exitMethod_call_statement(DecafParser.Method_call_statementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#if_statement}.
	 * @param ctx the parse tree
	 */
	void enterIf_statement(DecafParser.If_statementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#if_statement}.
	 * @param ctx the parse tree
	 */
	void exitIf_statement(DecafParser.If_statementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#for_statement}.
	 * @param ctx the parse tree
	 */
	void enterFor_statement(DecafParser.For_statementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#for_statement}.
	 * @param ctx the parse tree
	 */
	void exitFor_statement(DecafParser.For_statementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#while_statement}.
	 * @param ctx the parse tree
	 */
	void enterWhile_statement(DecafParser.While_statementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#while_statement}.
	 * @param ctx the parse tree
	 */
	void exitWhile_statement(DecafParser.While_statementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#return_statement}.
	 * @param ctx the parse tree
	 */
	void enterReturn_statement(DecafParser.Return_statementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#return_statement}.
	 * @param ctx the parse tree
	 */
	void exitReturn_statement(DecafParser.Return_statementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#break_statement}.
	 * @param ctx the parse tree
	 */
	void enterBreak_statement(DecafParser.Break_statementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#break_statement}.
	 * @param ctx the parse tree
	 */
	void exitBreak_statement(DecafParser.Break_statementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#continue_statement}.
	 * @param ctx the parse tree
	 */
	void enterContinue_statement(DecafParser.Continue_statementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#continue_statement}.
	 * @param ctx the parse tree
	 */
	void exitContinue_statement(DecafParser.Continue_statementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#for_update}.
	 * @param ctx the parse tree
	 */
	void enterFor_update(DecafParser.For_updateContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#for_update}.
	 * @param ctx the parse tree
	 */
	void exitFor_update(DecafParser.For_updateContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#location}.
	 * @param ctx the parse tree
	 */
	void enterLocation(DecafParser.LocationContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#location}.
	 * @param ctx the parse tree
	 */
	void exitLocation(DecafParser.LocationContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#assign_expr}.
	 * @param ctx the parse tree
	 */
	void enterAssign_expr(DecafParser.Assign_exprContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#assign_expr}.
	 * @param ctx the parse tree
	 */
	void exitAssign_expr(DecafParser.Assign_exprContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#assign_op}.
	 * @param ctx the parse tree
	 */
	void enterAssign_op(DecafParser.Assign_opContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#assign_op}.
	 * @param ctx the parse tree
	 */
	void exitAssign_op(DecafParser.Assign_opContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#compound_assign_op}.
	 * @param ctx the parse tree
	 */
	void enterCompound_assign_op(DecafParser.Compound_assign_opContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#compound_assign_op}.
	 * @param ctx the parse tree
	 */
	void exitCompound_assign_op(DecafParser.Compound_assign_opContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#increment}.
	 * @param ctx the parse tree
	 */
	void enterIncrement(DecafParser.IncrementContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#increment}.
	 * @param ctx the parse tree
	 */
	void exitIncrement(DecafParser.IncrementContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterExpr(DecafParser.ExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitExpr(DecafParser.ExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#method_call}.
	 * @param ctx the parse tree
	 */
	void enterMethod_call(DecafParser.Method_callContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#method_call}.
	 * @param ctx the parse tree
	 */
	void exitMethod_call(DecafParser.Method_callContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#import_arg}.
	 * @param ctx the parse tree
	 */
	void enterImport_arg(DecafParser.Import_argContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#import_arg}.
	 * @param ctx the parse tree
	 */
	void exitImport_arg(DecafParser.Import_argContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#mul_op}.
	 * @param ctx the parse tree
	 */
	void enterMul_op(DecafParser.Mul_opContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#mul_op}.
	 * @param ctx the parse tree
	 */
	void exitMul_op(DecafParser.Mul_opContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#add_op}.
	 * @param ctx the parse tree
	 */
	void enterAdd_op(DecafParser.Add_opContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#add_op}.
	 * @param ctx the parse tree
	 */
	void exitAdd_op(DecafParser.Add_opContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#rel_op}.
	 * @param ctx the parse tree
	 */
	void enterRel_op(DecafParser.Rel_opContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#rel_op}.
	 * @param ctx the parse tree
	 */
	void exitRel_op(DecafParser.Rel_opContext ctx);
	/**
	 * Enter a parse tree produced by {@link DecafParser#eq_op}.
	 * @param ctx the parse tree
	 */
	void enterEq_op(DecafParser.Eq_opContext ctx);
	/**
	 * Exit a parse tree produced by {@link DecafParser#eq_op}.
	 * @param ctx the parse tree
	 */
	void exitEq_op(DecafParser.Eq_opContext ctx);
}