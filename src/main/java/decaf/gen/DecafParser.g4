parser grammar DecafParser;

options { tokenVocab=DecafLexer; }

program: import_decl* field_decl* method_decl* EOF;

import_decl: IMPORT IDENTIFIER SEMI;

field_decl: type (
    field_decl_sub
    (COMMA field_decl_sub)*
    SEMI
);

field_decl_sub: (IDENTIFIER field_decl_arr? (EQUAL initializer)?);

field_decl_arr: LSQUARE int_literal? RSQUARE;

type: INT | BOOL;

int_literal: INT_LITERAL | HEX_LITERAL;

initializer: literal | array_literal;

literal: ((SUB)? int_literal) | (CHAR_LITERAL) | (BOOL_LITERAL);
array_literal: LCURLY (literal) (COMMA literal)* RCURLY;

method_decl: (type | VOID) IDENTIFIER LPAREN (decl_parameter (COMMA decl_parameter)*)? RPAREN block;

decl_parameter: (type IDENTIFIER);

block: LCURLY field_decl* statement* RCURLY;

statement:
    assign_statement |
    method_call_statement |
    if_statement |
    for_statement |
    while_statement |
    return_statement |
    break_statement |
    continue_statement
    ;

assign_statement: location assign_expr SEMI;
method_call_statement: method_call SEMI;
if_statement: IF LPAREN expr RPAREN block (ELSE block)?;
for_statement: FOR LPAREN IDENTIFIER EQUAL expr SEMI expr SEMI for_update RPAREN block;
while_statement: WHILE LPAREN expr RPAREN block;
return_statement: RETURN expr? SEMI;
break_statement: BREAK SEMI;
continue_statement: CONTINUE SEMI;

for_update: location (compound_assign_op expr | increment);

location: IDENTIFIER | (IDENTIFIER LSQUARE expr RSQUARE);
assign_expr: (assign_op expr) | increment;

assign_op: EQUAL | compound_assign_op;
compound_assign_op: ADD_EQUAL | SUB_EQUAL;
increment: INC | DEC;

expr:
    location |
    method_call |
    literal |
    LEN LPAREN IDENTIFIER RPAREN |
    LPAREN expr RPAREN | // Paren
    SUB expr | // Unary minus
    NOT expr | // Not
    expr mul_op expr | // Bin_op in order
    expr add_op expr |
    expr rel_op expr |
    expr eq_op expr  |
    expr AND expr |
    expr OR expr |
    expr QUES (expr COLON expr); // Ternary

method_call:
    IDENTIFIER (LPAREN (import_arg (COMMA import_arg)*)? RPAREN);

import_arg: expr | STRING_LITERAL;

mul_op: MULT | DIV | MOD;
add_op: ADD | SUB;
rel_op: LT | LTE | GT | GTE;
eq_op: EQ | NEQ;