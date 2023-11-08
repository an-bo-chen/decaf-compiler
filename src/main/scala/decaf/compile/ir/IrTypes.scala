package decaf.compile.ir

import scala.collection.mutable

/**
 * A location in the code that was inputted to the scanner. 
 *
 * @param lineNumber vertical location in the code by line number.
 * @param colNumber horizontal location in the code by column number.
 */
case class CodeLocation(lineNumber: Int, colNumber: Int)

/**
 * A base class for our internal representation.
 * All internal representation classes inherit form this class.
 *
 * @param location code location (see `CodeLocation` docs).
 */
sealed class Ir(val location: CodeLocation)

/**
 * Represents an entire Decaf Program.
 * 
 * @param imports the list of imports.
 * @param fields the list of global field declarations.
 * @param functions the list of declared methods.
 * @param scope the global scope.
 * @param location code location (see `CodeLocation` docs).
 */
case class IrProgram(
                      imports: List[IrImportDecl],
                      fields: List[IrFieldDecl],
                      functions: List[IrFunctionDecl],
                      scope: IrScope,
                      override val location: CodeLocation
                    ) extends Ir(location)

/**
 * A map of declared variables to declarations within some scope.
 * Everything with a scope (global, methods, and blocks) contain an `IrScope` field.
 *
 * @param symbols the aforementioned map, mapping string identifiers to their declarations.
 */
case class IrScope(symbols: mutable.HashMap[String, IrMemberDecl], order: mutable.HashMap[String, Int], scopeType: ScopeType, curMethodIdentifier: Option[String])

sealed abstract class ScopeType(val typeStr: String) {
  override def toString: String = typeStr
}
object ScopeType {
  final case object PROGRAM extends ScopeType(typeStr = "PROGRAM")

  final case object METHOD_PARAMETERS extends ScopeType(typeStr = "METHOD_PARAMETERS")

  final case object METHOD_BODY extends ScopeType(typeStr = "METHOD_BODY")

  final case object IF_ELSE extends ScopeType(typeStr = "IF_ELSE")

  final case object FOR extends ScopeType(typeStr = "FOR")

  final case object WHILE extends ScopeType(typeStr = "WHILE")
}

/**
 * Represents a block of code, such as method bodies, `if`/`else` blocks, etc.
 *
 * @param fields the declared fields within this block.
 * @param statements the statements within this block.
 * @param scope the scope of this block.
 * @param location code location (see `CodeLocation` docs).
 */
case class IrBlock(
                    fields: List[IrFieldDecl],
                    statements: List[IrStatement],
                    scope: IrScope,
                    override val location: CodeLocation
                  ) extends Ir(location)

/**
 * Represents a location in memory being accessed via
 * a read (see `IrVarReadExpr`) or
 * a write (see `IrAssignStatement`).
 *
 * @param identifier the name of the variable being accessed.
 * @param index the optional index for if the variable is an array. Not semantically checked if the variable actually is an array!
 * @param location code location (see `CodeLocation` docs).
 */
case class IrLocation(
                       identifier: String,
                       index: Option[IrExpression],
                       override val location: CodeLocation
                     ) extends Ir(location)

// Initializers

/**
 * Represents an initializer for a variable declaration.
 *
 * @param literal the initializer literal (see `IrLiteral` docs).
 * @param location code location (see `CodeLocation` docs).
 */
case class IrInitializer(
                                     literal: IrLiteral,
                                     override val location: CodeLocation
                                   ) extends Ir(location)

// Literals

/**
 * Represents a literal. All instances are one of:
 * `IrIntLiteral` representing a int value
 * `IrBoolLiteral` representing a boolean value
 * `IrArrayLiteral` representing an array value
 *
 * @param value: the literal value
 * @param location code location (see `CodeLocation` docs).
 */
sealed abstract class IrLiteral(
                                 val value: Any,
                                 override val location: CodeLocation
                               ) extends IrExpression(location)
case class IrIntLiteral(
                         override val value: Long,
                         override val location: CodeLocation
                       ) extends IrLiteral(value, location)
case class IrBoolLiteral(
                          override val value: Boolean,
                          override val location: CodeLocation
                        ) extends IrLiteral(value, location)
case class IrArrayLiteral(
                           override val value: List[IrLiteral],
                           override val location: CodeLocation
                         ) extends IrLiteral(value, location)

// Statements

/**
 * Represents a code statement
 *
 * @param location code location (see `CodeLocation` docs).
 */
sealed abstract class IrStatement(
                                   override val location: CodeLocation
                                 ) extends Ir(location)

/**
 * Represents an assignment statement.
 *
 * @param assignLocation the location being assigned to, could be either a variable or array index (see docs for `IrLocation`).
 * @param assignOp the assignment operation.
 * @param assignExpr the expression, the value of which is being assigned to the location.
 * @param location code location (see `CodeLocation` docs).
 */
case class IrAssignStatement(
                              assignLocation: IrLocation,
                              assignOp: AssignOpType,
                              assignExpr: IrExpression,
                              override val location: CodeLocation
                            ) extends IrStatement(location)

/**
 * Represents a method call statement, where no result is required. Ex. `printf("hello world")`
 * Distinguish this from method call expressions.
 *
 * @param identifier the name of the method being called.
 * @param params the params passed to the method, which may be string literals or expressions
 *               (see docs for MethodCallParam)
 * @param location code location (see `CodeLocation` docs).
 */
case class IrFunctionCallStatement(
                             identifier: String,
                             params: List[MethodCallParam],
                             override val location: CodeLocation
                           ) extends IrStatement(location)

/**
 * Represents an if statement.
 *
 * @param conditional the condition being checked in the if statement.
 * @param thenBlock the block to be run if conditional evaluates true.
 * @param elseBlock optionally, the block to be run if conditional evaluates false.
 * @param location code location (see `CodeLocation` docs).
 */
case class IrIfStatement(
                        conditional: IrExpression,
                        thenBlock: IrBlock,
                        elseBlock: Option[IrBlock],
                        override val location: CodeLocation
                        ) extends IrStatement(location)

/**
 * Represents a for loop statement. Ex:
 * `for (i = 0; i < 4; i += 1) { bar(); }`
 *
 * @param identifier the name of the iterator variable of the for loop (`i`).
 * @param declExpr the expression the iterator variable is initialized to (`0`).
 * @param conditional the condition being checked before each iteration of the for loop (`i < 4`).
 * @param forUpdate the update statement to be run after each iteration of the for loop (`i += 1`).
 * @param forBlock the block of code to be run each iteration of the for loop (`{ bar(); }`).
 * @param location code location (see `CodeLocation` docs).
 */
case class IrForStatement(
                         identifier: String,
                         declExpr: IrExpression,
                         conditional: IrExpression,
                         forUpdate: IrForUpdate,
                         forBlock: IrBlock,
                         override val location: CodeLocation
                         ) extends IrStatement(location)

/**
 * Represents the for update code.
 *
 * @param identifier the name of the variable to be updated when running this code.
 * @param compoundAssignOp the compound assignment operation.
 * @param expr the expression, the value of which is being assigned to the variable with the identifier.
 * @param location code location (see `CodeLocation` docs).
 */
case class IrForUpdate(
                        identifier: IrLocation,
                        compoundAssignOp: AssignOpType,
                        expr: IrExpression,
                        override val location: CodeLocation
                      ) extends Ir(location: CodeLocation)

/**
 * Represents a while loop statement. Ex:
 * `while(k < bar()) { baz(); }`
 *
 * @param conditional the condition to be checked each iteration of the while loop (`k < bar()`).
 * @param whileBlock the block of code (`{ baz(); }`) to be run each iteration of the while loop.
 * @param location code location (see `CodeLocation` docs).
 */
case class IrWhileStatement(
                           conditional: IrExpression,
                           whileBlock: IrBlock,
                           override val location: CodeLocation
                           ) extends IrStatement(location)

/**
 * Represents a return statement.
 *
 * @param expr the expression being returned.
 * @param location code location (see `CodeLocation` docs).
 */
case class IrReturnStatement(
                            expr: Option[IrExpression],
                            override val location: CodeLocation
                            ) extends IrStatement(location)

/**
 * Represents a continue statement.
 *
 * @param location code location (see `CodeLocation` docs).
 */
case class IrContinueStatement(override val location: CodeLocation) extends IrStatement(location)

/**
 * Represents a break statement.
 *
 * @param location code location (see `CodeLocation` docs).
 */
case class IrBreakStatement(override val location: CodeLocation) extends IrStatement(location)

// Declarations

/**
 * Includes all types. All instances are one of:
 * `INT` representing ints
 * `BOOL` representing booleans
 * `INT_ARR` representing int arrays
 * `BOOL_ARR` representing boolean arrays
 * `VOID` representing void return type.
 *
 * @param typeStr string representation of the type.
 */
sealed abstract class Type(val typeStr: String) {
  override def toString: String = typeStr
}
object Type {
  final case object INT extends Type(typeStr = "INT")

  final case object BOOL extends Type(typeStr = "BOOL")

  final case object INT_ARR extends Type(typeStr = "INT_ARR")

  final case object BOOL_ARR extends Type(typeStr = "BOOL_ARR")

  final case object VOID extends Type(typeStr = "VOID")
}

/**
 * Represents a declaration of a variable with a type.
 *
 * @param declType the type of the declaration.
 * @param identifier the name of the variable being declared.
 * @param location code location (see `CodeLocation` docs).
 */
sealed abstract class IrMemberDecl(
                                    val declType: Type,
                                    val identifier: String,
                                    override val location: CodeLocation
                                  ) extends Ir(location)

/**
 * Represents an import declaration.
 *
 * @param identifier the name of the thing being imported.
 * @param location code location (see `CodeLocation` docs).
 */
case class IrImportDecl(
                         override val identifier: String,
                         override val location: CodeLocation
                       ) extends IrMemberDecl(Type.INT, identifier, location)

/**
 * Represents a list of declarations that were declared using shorthand on one line. Ex:
 * `int a = 3, b, c[] = {3,4};`
 *
 * @param fields the fields being declared (these objects contain the type information).
 * @param location code location (see `CodeLocation` docs).
 */
class IrFieldDeclContainer(val fields: List[IrFieldDecl], override val location: CodeLocation) extends Ir(location)

/**
 * Represents a field declaration. Ex:
 * `bool b = false;`
 * All instances are either
 * `IrRegFieldDecl` representing a regular, non-array field declaration, or
 * `IrArrayFieldDecl` representing an array field declaration, which also includes the length of the declared array.
 *
 * @param declType the type of the declaration.
 * @param identifier the name of the variable being declared.
 * @param initializer the initial value, which is optional.
 * @param location code location (see `CodeLocation` docs).
 */
sealed abstract class IrFieldDecl(
                                   override val declType: Type,
                                   override val identifier: String,
                                   val initializer: Option[IrInitializer],
                                   override val location: CodeLocation
                      ) extends IrMemberDecl(declType, identifier, location)
case class IrRegFieldDecl(
                           override val declType: Type,
                           override val identifier: String,
                           override val initializer: Option[IrInitializer],
                           override val location: CodeLocation
                           ) extends IrFieldDecl(declType, identifier, initializer, location)
case class IrArrayFieldDecl(
                             override val declType: Type,
                             override val identifier: String,
                             override val initializer: Option[IrInitializer],
                             length: Long,
                             override val location: CodeLocation
                           ) extends IrFieldDecl(declType, identifier, initializer, location)

/**
 * Represents the declaration of a method.
 *
 * @param declType the return type of the method.
 * @param identifier the name of the method being declared.
 * @param parameters the declared parameters of the method (see `IrParameterDecl`).
 * @param block the method body block.
 * @param scope the scope of the method.
 * @param location code location (see `CodeLocation` docs).
 */
case class IrFunctionDecl(
                         override val declType: Type,
                         override val identifier: String,
                         parameters: List[IrParameterDecl],
                         block: IrBlock,
                         scope: IrScope,
                         override val location: CodeLocation
                       ) extends IrMemberDecl(declType, identifier, location)

/**
 * Represents the declaration of a method parameter in a method declaration.
 *
 * @param declType the type of the declaration.
 * @param identifier the name of the parameter variable being declared.
 * @param location code location (see `CodeLocation` docs).
 */
case class IrParameterDecl(
                            override val declType: Type,
                            override val identifier: String,
                            override val location: CodeLocation
                          ) extends IrFieldDecl(declType, identifier, None, location)

// Expressions

/**
 * Represents an expression.
 *
 * @param location code location (see `CodeLocation` docs).
 */
sealed abstract class IrExpression(
                                    override val location: CodeLocation
                                  ) extends Ir(location)

/**
 * Represents a read of a location which results in some value, ex.
 * '`b`' in `int a = b;` or
 * '`b[2]`' in `int a = b[2];`
 *
 * @param readLocation the location being read from, which contains the identifier (variable name)
 *                     and a possible index if the read is an array index operation (see docs for `IrLocation`)
 * @param location code location (see `CodeLocation` docs).
 */
case class IrVarReadExpr(
                      readLocation: IrLocation,
                      override val location: CodeLocation
                    ) extends IrExpression(location)

/**
 * Represents a method call within an expression which must result in some value, ex.
 * `sum(a, 3+4, false)` in `a = sum(a, 3+4, false);`.
 * Distinguish this from method call statements.
 *
 * @param identifier the name of the method being called.
 * @param params the params passed to the method, which may be a string literals or expressions
 *               (see docs for MethodCallParam)
 * @param location code location (see `CodeLocation` docs).
 */
case class IrFunctionCallExpr(
                             identifier: String,
                             params: List[MethodCallParam],
                             override val location: CodeLocation
                           ) extends IrExpression(location)

/**
 * Represents a parameter passed to a method call.
 * All instances are either a 
 * `MethodCallExprParam` representing an expression param
 * `MethodCallStringParam` representing a string literal param.
 *
 * @param param either a expression or a string literal depending on type (see above).
 */
sealed abstract class MethodCallParam(val param: Any,
                             override val location: CodeLocation) extends Ir(location)
case class FunctionCallExprParam(
                                override val param: IrExpression,
                                override val location: CodeLocation
                              ) extends MethodCallParam(param, location)
case class FunctionCallStringParam(
                                  override val param: String,
                                  override val location: CodeLocation
                                ) extends MethodCallParam(param, location)

/**
 * Represents an expression that is `len()` of some variable.
 *
 * @param identifier the name of the variable `len()` is being called on
 * @param location code location (see `CodeLocation` docs).
 */
case class IrLenExpr(
                      identifier: String,
                      override val location: CodeLocation
                    ) extends IrExpression(location)

/**
 * Represents a unary operation being applied to some expression, resulting in a value ex.
 * `!false` where the unary operation is `!`, and the expression is `false`
 *
 * @param op the unary operation, represented by enum IrUnaryOpType
 * @param expr the expression the operation is being applied to
 * @param location code location (see `CodeLocation` docs).
 */
case class IrUnaryOpExpr(
                          op: UnaryOpType,
                          expr: IrExpression,
                          override val location: CodeLocation
                        ) extends IrExpression(location)

/**
 * Represents a binary operation being applied to some expression, resulting in a value ex.
 * `2 <= (1+3)` where the unary operation is `<=`, and the expressions are `2` and `(1+3)`
 *
 * @param op the unary operation, represented by enum IrUnaryOpType
 * @param leftExpr the expression on the left the operation is being applied to
 * @param rightExpr the expression on the right the operation is being applied to
 * @param location code location (see `CodeLocation` docs).
 */
case class IrBinOpExpr(
                        op: BinOpType, // rather than IrBinOp, we can always just grab the opType and simplify
                        leftExpr: IrExpression,
                        rightExpr: IrExpression,
                        override val location: CodeLocation
                      ) extends IrExpression(location)

case class IrBinOp(
                    op: BinOpType,
                    override val location: CodeLocation
                  ) extends Ir(location)

/**
 * Represents a ternary operation being applied to a condition, and two expressions ex.
 * `a >= b ? foo(3) : arr[8]` where the condition is `a >= b`, the the expressions are `foo(3)` and `arr[8]`.
 * @param condition the condition for the ternary operation
 * @param trueExpr the expression to evaluate if the condition is true
 * @param falseExpr the expression to evaluate if the condition is false
 * @param location code location (see `CodeLocation` docs).
 */
case class IrTernaryExpr(
                          condition: IrExpression,
                          trueExpr: IrExpression,
                          falseExpr: IrExpression,
                          override val location: CodeLocation
                        ) extends IrExpression(location)

// Operations

/**
 * Represents the possible unary operations.
 *
 * @param opStr the operator representing the operation in Decaf.
 * @param opName name of the operation.
 */
sealed abstract class UnaryOpType(val opStr: String, val opName: String) {
  override def toString: String = opStr
}
object UnaryOpType {
  final case object UMINUS extends UnaryOpType(opStr = "-", opName = "UMIN")
  final case object NOT extends  UnaryOpType(opStr = "!", opName = "NOT")
}

/**
 * Represents the possible binary operations,
 * including arithmetic, comparison, relational, and logical operations.
 *
 * @param opStr the operator representing the operation in Decaf.
 * @param opName name of the operation.
 */
sealed abstract class BinOpType(val opStr: String, val opName: String) {
  override def toString: String = opStr
}

/**
 * Represents the possible arithmetic binary operations (int, int) -> int.
 *
 * @param opStr the operator representing the operation in Decaf.
 * @param opName name of the operation.
 */
sealed abstract class ArithBinOpType(
                                      override val opStr: String,
                                      override val opName: String
                                    ) extends BinOpType(opStr, opName)
object ArithBinOpType {
  final case object MUL extends ArithBinOpType(opStr = "*", opName = "MUL")
  final case object DIV extends ArithBinOpType(opStr = "/", opName = "DIV")
  final case object MOD extends ArithBinOpType(opStr = "%", opName = "MOD")
  final case object ADD extends ArithBinOpType(opStr = "+", opName = "ADD")
  final case object SUB extends ArithBinOpType(opStr = "-", opName = "SUB")
}

/**
 * Represents the possible relational binary operations (int, int) -> bool.
 *
 * @param opStr the operator representing the operation in Decaf.
 * @param opName name of the operation.
 */
sealed abstract class RelationalBinOpType(
                                           override val opStr: String,
                                           override val opName: String
                                         ) extends BinOpType(opStr, opName)
object RelationalBinOpType {
  final case object LT extends RelationalBinOpType(opStr = "<", opName = "LT")
  final case object LTE extends RelationalBinOpType(opStr = "<=", opName = "LTE")
  final case object GT extends RelationalBinOpType(opStr = ">", opName = "GT")
  final case object GTE extends RelationalBinOpType(opStr = ">=", opName = "GTE")
}

/**
 * Represents the possible equality binary operations (int, int) | (bool, bool) -> bool.
 *
 * @param opStr the operator representing the operation in Decaf.
 * @param opName name of the operation.
 */
sealed abstract class EqualityBinOpType(
                                        override val opStr: String,
                                        override val opName: String
                                      ) extends BinOpType(opStr, opName)
object EqualityBinOpType {
  final case object EQ extends EqualityBinOpType(opStr = "==", opName = "EQ")
  final case object NEQ extends EqualityBinOpType(opStr = "!=", opName = "NEQ")
}

/**
 * Represents the possible logical binary operations (bool, bool) -> bool.
 *
 * @param opStr the operator representing the operation in Decaf.
 * @param opName name of the operation.
 */
sealed abstract class LogicalBinOpType(
                                        override val opStr: String,
                                        override val opName: String
                                      ) extends BinOpType(opStr, opName)
object LogicalBinOpType {
  final case object AND extends LogicalBinOpType(opStr = "&&", opName = "AND")
  final case object OR extends LogicalBinOpType(opStr = "||", opName = "OR")
}



/**
 * Represents the possible assign operations.
 *
 * @param opStr the operator representing the operation in Decaf.
 * @param opName name of the operation.
 */
sealed abstract class AssignOpType(val opStr: String, val opName: String) {
  override def toString: String = opStr
}
object AssignOpType {
  final case object EQUAL extends AssignOpType(opStr = "=", opName = "EQUAL")
  final case object ADD_EQUAL extends AssignOpType(opStr = "+=", opName = "ADD_EQUAL")
  final case object SUB_EQUAL extends AssignOpType(opStr = "-=", opName = "SUB_EQUAL")
}
