#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>

/**
 * Symbol for associative binary operations
 */
typedef enum {
  And,
  Or,
  Bicon,
  Equiv,
} ASymbol;

/**
 * Symbol for binary operations
 */
typedef enum {
  Implies,
  Plus,
  Mult,
} BSymbol;

/**
 * Symbol for quantifiers
 */
typedef enum {
  Forall,
  Exists,
} QSymbol;

/**
 * Symbol for unary operations
 */
typedef enum {
  Not,
} USymbol;

typedef struct Box_Expr Box_Expr;

typedef struct String String;

typedef struct Vec_Expr Vec_Expr;

typedef struct Vec_String Vec_String;

/**
 * aris::expr::Expr is the core AST (Abstract Syntax Tree) type for representing logical expressions.
 * For most of the recursive cases, it uses symbols so that code can work on the shape of e.g. a binary operation without worrying about which binary operation it is.
 */
typedef enum {
  Contradiction,
  Tautology,
  Var,
  Apply,
  Unop,
  Binop,
  AssocBinop,
  Quantifier,
} Expr_Tag;

typedef struct {
  String name;
} Var_Body;

typedef struct {
  Box_Expr func;
  Vec_Expr args;
} Apply_Body;

typedef struct {
  USymbol symbol;
  Box_Expr operand;
} Unop_Body;

typedef struct {
  BSymbol symbol;
  Box_Expr left;
  Box_Expr right;
} Binop_Body;

typedef struct {
  ASymbol symbol;
  Vec_Expr exprs;
} AssocBinop_Body;

typedef struct {
  QSymbol symbol;
  String name;
  Box_Expr body;
} Quantifier_Body;

typedef struct {
  Expr_Tag tag;
  union {
    Var_Body var;
    Apply_Body apply;
    Unop_Body unop;
    Binop_Body binop;
    AssocBinop_Body assoc_binop;
    Quantifier_Body quantifier;
  };
} Expr;

Expr aris_box_expr_deref(const Box_Expr *x);

Expr *aris_expr_parse(const int8_t *e);

Expr aris_vec_expr_index(const Vec_Expr *x, uintptr_t i);

String aris_vec_string_index(const Vec_String *x, uintptr_t i);
