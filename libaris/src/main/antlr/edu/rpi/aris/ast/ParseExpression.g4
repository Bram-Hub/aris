grammar ParseExpression;

@header {
package edu.rpi.aris.ast;
}

main: expr EOF;

SPACE: [ \n\r\t]+ ;
NUMBER: [0-9]+ ;
VARIABLE: [a-zA-Z][a-zA-Z0-9]* ;

predicate:
    SPACE? VARIABLE SPACE?
    | SPACE? VARIABLE SPACE? '(' SPACE? arg_list SPACE? ')' SPACE?
    ;

arg_list: SPACE? VARIABLE SPACE? | SPACE? VARIABLE SPACE? ',' arg_list ;

forallQuantifier: 'forall ' | '∀';
existsQuantifier: 'exists ' | '∃';
quantifier: forallQuantifier | existsQuantifier  ;
binder: SPACE? quantifier SPACE? VARIABLE SPACE? ',' SPACE? paren_expr ;

andrepr: '&' | '∧' | '/\\' ;
andterm: SPACE? paren_expr SPACE? andrepr SPACE? andterm SPACE?
    | paren_expr ;

orrepr: '|' | '∨' | '\\/' ;
orterm: SPACE? paren_expr SPACE? orrepr SPACE? orterm SPACE?
    | paren_expr ;

biconrepr: '<->' | '↔' ;
biconterm: SPACE? paren_expr SPACE? biconrepr SPACE? biconterm SPACE?
    | paren_expr ;

assocterm: andterm | orterm | biconterm ;

BINOP: '->' | '+' | '*' ;
binopterm: paren_expr SPACE? BINOP SPACE? paren_expr ;

notterm: '~' paren_expr;

bottom: '_|_' ;

paren_expr: bottom | predicate | notterm | binder | SPACE? '(' SPACE? expr SPACE? ')' SPACE? ;

expr: assocterm | binopterm | paren_expr ;
