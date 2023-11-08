// Generated from java-escape by ANTLR 4.11.1
package decaf.gen;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link DecafParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface DecafParserVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link DecafParser#program}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitProgram(DecafParser.ProgramContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#import_decl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitImport_decl(DecafParser.Import_declContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#field_decl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitField_decl(DecafParser.Field_declContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#field_decl_sub}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitField_decl_sub(DecafParser.Field_decl_subContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#field_decl_arr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitField_decl_arr(DecafParser.Field_decl_arrContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#type}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitType(DecafParser.TypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#int_literal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInt_literal(DecafParser.Int_literalContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#initializer}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInitializer(DecafParser.InitializerContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#literal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLiteral(DecafParser.LiteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#array_literal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArray_literal(DecafParser.Array_literalContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#method_decl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMethod_decl(DecafParser.Method_declContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#decl_parameter}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDecl_parameter(DecafParser.Decl_parameterContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#block}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBlock(DecafParser.BlockContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatement(DecafParser.StatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#assign_statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssign_statement(DecafParser.Assign_statementContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#method_call_statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMethod_call_statement(DecafParser.Method_call_statementContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#if_statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIf_statement(DecafParser.If_statementContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#for_statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFor_statement(DecafParser.For_statementContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#while_statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitWhile_statement(DecafParser.While_statementContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#return_statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitReturn_statement(DecafParser.Return_statementContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#break_statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBreak_statement(DecafParser.Break_statementContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#continue_statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitContinue_statement(DecafParser.Continue_statementContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#for_update}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFor_update(DecafParser.For_updateContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#location}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLocation(DecafParser.LocationContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#assign_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssign_expr(DecafParser.Assign_exprContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#assign_op}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssign_op(DecafParser.Assign_opContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#compound_assign_op}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCompound_assign_op(DecafParser.Compound_assign_opContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#increment}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIncrement(DecafParser.IncrementContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpr(DecafParser.ExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#method_call}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMethod_call(DecafParser.Method_callContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#import_arg}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitImport_arg(DecafParser.Import_argContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#mul_op}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMul_op(DecafParser.Mul_opContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#add_op}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAdd_op(DecafParser.Add_opContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#rel_op}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRel_op(DecafParser.Rel_opContext ctx);
	/**
	 * Visit a parse tree produced by {@link DecafParser#eq_op}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEq_op(DecafParser.Eq_opContext ctx);
}