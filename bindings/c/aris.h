#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>

/**
 * Associative operators. All of these operations are associative.
 */
typedef enum {
  /**
   * Logical and `∧`
   */
  And,
  /**
   * Logical or `∨`
   */
  Or,
  /**
   * Logical biconditional `↔`
   */
  Bicon,
  /**
   * Logical equivalence `≡`
   */
  Equiv,
  /**
   * Arithmetic addition `+`
   */
  Add,
  /**
   * Arithmetic multiplication `*`
   */
  Mult,
} Op;

/**
 * Kinds of quantifiers
 */
typedef enum {
  /**
   * Universal quantifier `∀`
   */
  Forall,
  /**
   * Existential quantifier `∃`
   */
  Exists,
} QuantKind;

typedef struct Box_Expr Box_Expr;

typedef struct String String;

typedef struct Vec_Expr Vec_Expr;

/**
 * A logical expression
 */
typedef enum {
  /**
   * Contradiction `⊥`
   */
  Contra,
  /**
   * Tautology `⊤`
   */
  Taut,
  /**
   * A symbolic logical variable `P`
   */
  Var,
  /**
   * A function call `P(A, B, C)`
   */
  Apply,
  /**
   * Logical negation `¬P`
   */
  Not,
  /**
   * Logical implication `P → Q`
   */
  Impl,
  /**
   * An associative operation `P <OP> Q <OP> R`
   */
  Assoc,
  /**
   * A quantifier expression `<KIND> A, P`
   */
  Quant,
} Expr_Tag;

typedef struct {
  /**
   * Name of the variable
   */
  String name;
} Var_Body;

typedef struct {
  /**
   * The function `P` being called
   */
  Box_Expr func;
  /**
   * Arguments `A, B, C` passed to the function
   */
  Vec_Expr args;
} Apply_Body;

typedef struct {
  /**
   * The operand of the negation `P`
   */
  Box_Expr operand;
} Not_Body;

typedef struct {
  /**
   * The left expression `P`
   */
  Box_Expr left;
  /**
   * The right expression `Q`
   */
  Box_Expr right;
} Impl_Body;

typedef struct {
  /**
   * The operator `<OP>`
   */
  Op op;
  /**
   * The expressions `P, Q, R`
   */
  Vec_Expr exprs;
} Assoc_Body;

typedef struct {
  /**
   * The kind of quantifier `<KIND>`
   */
  QuantKind kind;
  /**
   * The quantified variable `A`
   */
  String name;
  /**
   * The quantifier body `P`
   */
  Box_Expr body;
} Quant_Body;

typedef struct {
  Expr_Tag tag;
  union {
    Var_Body var;
    Apply_Body apply;
    Not_Body not;
    Impl_Body impl;
    Assoc_Body assoc;
    Quant_Body quant;
  };
} Expr;

Expr *aris_expr_parse(const int8_t *e);
