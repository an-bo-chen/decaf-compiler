lexer grammar DecafLexer;

// Decaf keywords

BOOL: 'bool';
BREAK: 'break';
IMPORT: 'import';
CONTINUE: 'continue';
ELSE: 'else';
FOR: 'for';
WHILE: 'while';
IF: 'if';
INT: 'int';
RETURN: 'return';
LEN: 'len';
VOID: 'void';

// Parse for the symbols
LCURLY: '{';
RCURLY: '}';
LSQUARE: '[';
RSQUARE: ']';
LPAREN: '(';
RPAREN: ')';

COMMA: ',';
SEMI: ';';

INC: '++';
DEC: '--';

ADD_EQUAL: '+=';
SUB_EQUAL: '-=';

ADD: '+';
SUB: '-';
MULT: '*';
DIV: '/';
MOD: '%';
EQUAL: '=';
NOT: '!';

LT: '<';
LTE: '<=';
GT: '>';
GTE: '>=';
NEQ: '!=';
EQ: '==';

AND: '&&';
OR: '||';

QUES: '?';
COLON: ':';

// Handle comments
/*
White space is defined as one or more spaces, tabs, line-break characters (carriage return, line feed, form feed),
and comments.
*/
WHITE_SPACE: [ \t\r\n\f]+ -> skip;
LINE_COMMENT: '//' ~[\r\n]* -> skip;
BLOCK_COMMENT: '/*' .*? '*/' -> skip;

// Literals and identifiers
INT_LITERAL: ('0' | [0-9] (([0-9] ([0-9_]* [0-9])?)?));
HEX_LITERAL: '0' 'x' [0-9a-fA-F] ([0-9a-fA-F_]* [0-9a-fA-F])?;
BOOL_LITERAL: 'true' | 'false';

fragment ValidAlphaNumeric: ValidAlpha | [0-9];
fragment ValidAlpha: [a-zA-Z_];

// We don't like \, ", ', new lines so we'll exclude these, but we allow escaped \t, \n, \", \', \\
STRING_LITERAL: '"' (~['"\\\r\n\t\f] | '\\' [tnrf"'\\])* '"';
CHAR_LITERAL: '\'' (~['"\\\r\n\t\f] | '\\' [tnrf"'\\]) '\'';
IDENTIFIER: ValidAlpha (ValidAlphaNumeric*)?;
