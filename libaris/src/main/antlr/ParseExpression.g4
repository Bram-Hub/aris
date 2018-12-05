grammar ParseExpression;

main: expr EOF;

SPACE: [ \n\r\t]+ ;
NUMBER: [0-9]+ ;
VARIABLE: [a-zA-Z][a-zA-Z0-9]* ;

predicate:
    | SPACE? VARIABLE SPACE?
    | SPACE? VARIABLE SPACE? '(' SPACE? arg_list SPACE? ')' SPACE?
    ;

arg_list: SPACE? VARIABLE SPACE? | SPACE? VARIABLE SPACE? ',' arg_list ;

quantifier: 'forall ' | '∀' | 'exists ' | '∃' ;
binder: quantifier SPACE? VARIABLE SPACE? ',' SPACE? expr ;

andrepr: '&' | '∧' | '/\\' ;
andterm: SPACE? paren_expr SPACE? andrepr SPACE? andterm SPACE?
    | paren_expr ;

orrepr: '|' | '∨' | '\\/' ;
orterm: SPACE? paren_expr SPACE? '|' SPACE? orterm SPACE?
    | paren_expr ;

biconrepr: '<->' | '↔' ;
biconterm: SPACE? paren_expr SPACE? biconrepr SPACE? biconterm SPACE?
    | paren_expr ;

assocterm: andterm | orterm | biconterm ;

binop: '->' | '+' | '*' ;
binopterm: paren_expr SPACE? binop SPACE? paren_expr ;

notterm: '~' paren_expr;

bottom: '_|_' ;

paren_expr: bottom | predicate | notterm | SPACE? '(' SPACE? expr SPACE? ')' SPACE? ;

expr:
    | SPACE? binder
    | assocterm
    | binopterm
    | paren_expr
    ;
