#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>

typedef enum {
  And,
  Or,
  Bicon,
} ASymbol;

typedef enum {
  Implies,
  Plus,
  Mult,
} BSymbol;

typedef enum {
  Forall,
  Exists,
} QSymbol;

typedef enum {
  Not,
} USymbol;

typedef struct Box_Expr Box_Expr;

typedef struct String String;

typedef struct Vec_Expr Vec_Expr;

typedef struct Vec_String Vec_String;

typedef enum {
  Bottom,
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
