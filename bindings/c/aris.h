#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>

/**
 * Associative operators. All of these operations are associative.
 */
typedef enum Op {
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
typedef enum QuantKind {
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
typedef enum Expr_Tag {
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

typedef struct Var_Body {
  /**
   * Name of the variable
   */
  struct String name;
} Var_Body;

typedef struct Apply_Body {
  /**
   * The function `P` being called
   */
  struct Box_Expr func;
  /**
   * Arguments `A, B, C` passed to the function
   */
  struct Vec_Expr args;
} Apply_Body;

typedef struct Not_Body {
  /**
   * The operand of the negation `P`
   */
  struct Box_Expr operand;
} Not_Body;

typedef struct Impl_Body {
  /**
   * The left expression `P`
   */
  struct Box_Expr left;
  /**
   * The right expression `Q`
   */
  struct Box_Expr right;
} Impl_Body;

typedef struct Assoc_Body {
  /**
   * The operator `<OP>`
   */
  enum Op op;
  /**
   * The expressions `P, Q, R`
   */
  struct Vec_Expr exprs;
} Assoc_Body;

typedef struct Quant_Body {
  /**
   * The kind of quantifier `<KIND>`
   */
  enum QuantKind kind;
  /**
   * The quantified variable `A`
   */
  struct String name;
  /**
   * The quantifier body `P`
   */
  struct Box_Expr body;
} Quant_Body;

typedef struct Expr {
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

struct Expr *aris_expr_parse(const int8_t *e);
