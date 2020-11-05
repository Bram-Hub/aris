/*!
ASTs for logical expressions

# Usage

Parsing an expression that's statically known to be valid:

```
use aris::parser::parse_unwrap as p;

let expr1 = p("forall x, p(x) -> (Q & R)");
```

Parsing a potentially malformed expression (e.g. user input):
```
use aris::parser;

fn handle_user_input(input: &str) -> String {
    match parser::parse(input) {
        Some(expr) => format!("successful parse: {:?}", expr),
        None => format!("unsuccessful parse"),
    }
}
assert_eq!(&handle_user_input("good(predicate, expr)"), "successful parse: Apply { func: Var { name: \"good\" }, args: [Var { name: \"predicate\" }, Var { name: \"expr\" }] }");
assert_eq!(&handle_user_input("bad(missing, paren"), "unsuccessful parse");
```
*/

use std::collections::BTreeSet;
use std::collections::{HashMap, HashSet};
use std::fmt;
use std::mem;
use std::ops::Not;

use itertools::Itertools;
use maplit::hashset;
use serde::Deserialize;
use serde::Serialize;

/// Associative operators. All of these operations are associative.
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash, Ord, PartialOrd, Serialize, Deserialize)]
#[repr(C)]
pub enum Op {
    /// Logical and `∧`
    And,
    /// Logical or `∨`
    Or,
    /// Logical biconditional `↔`
    Bicon,
    /// Logical equivalence `≡`
    Equiv,
    /// Arithmetic addition `+`
    Add,
    /// Arithmetic multiplication `*`
    Mult,
}

/// Kinds of quantifiers
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash, Ord, PartialOrd, Serialize, Deserialize)]
#[repr(C)]
pub enum QuantKind {
    /// Universal quantifier `∀`
    Forall,
    /// Existential quantifier `∃`
    Exists,
}

/// A logical expression
#[derive(Clone, Debug, PartialEq, Eq, Hash, Ord, PartialOrd, Serialize, Deserialize)]
#[repr(C)]
pub enum Expr {
    /// Contradiction `⊥`
    Contra,

    /// Tautology `⊤`
    Taut,

    /// A symbolic logical variable `P`
    Var {
        /// Name of the variable
        name: String,
    },

    /// A function call `P(A, B, C)`
    Apply {
        /// The function `P` being called
        func: Box<Expr>,

        /// Arguments `A, B, C` passed to the function
        args: Vec<Expr>,
    },

    /// Logical negation `¬P`
    Not {
        /// The operand of the negation `P`
        operand: Box<Expr>
    },

    /// Logical implication `P → Q`
    Impl {
        /// The left expression `P`
        left: Box<Expr>,

        /// The right expression `Q`
        right: Box<Expr>,
    },

    /// An associative operation `P <OP> Q <OP> R`
    Assoc {
        /// The operator `<OP>`
        op: Op,

        /// The expressions `P, Q, R`
        exprs: Vec<Expr>,
    },

    /// A quantifier expression `<KIND> A, P`
    Quant {
        /// The kind of quantifier `<KIND>`
        kind: QuantKind,

        /// The quantified variable `A`
        name: String,

        /// The quantifier body `P`
        body: Box<Expr>,
    },
}

/// An expression in [negation normal form (NNF)][nnf]. This can be obtained
/// from an [`Expr`](Expr) with [`Expr::into_nnf()`](Expr::into_nnf) or methods
/// on `NnfExpr`.
///
/// [nnf]: https://en.wikipedia.org/wiki/Negation_normal_form
#[derive(Clone, Debug, PartialEq, Eq, Hash, Ord, PartialOrd)]
pub enum NnfExpr {
    /// A variable that may or may not be negated.
    Lit {
        /// Whether the variable is positive
        polarity: bool,
        /// Name of the variable
        name: String,
    },

    /// Sub-expressions OR'ed together
    Or {
        /// The subexpressions which are OR'ed
        exprs: Vec<NnfExpr>
    },

    /// Sub-expressions AND'ed together
    And {
        /// The subexpressions which are AND'ed
        exprs: Vec<NnfExpr>
    },
}

/// An expression in [conjunctive normal form (CNF)][cnf]. This can be obtained
/// from an [`Expr`](Expr) with [`Expr::into_cnf()`](Expr::into_cnf) or an
/// [`NnfExpr`](NnfExpr) with [`NnfExpr::into_cnf()`](NnfExpr::into_cnf).
/// Alternatively it can be built with methods on `CnfExpr`. Internally,
/// `CnfExpr` is represented as a `Vec<Vec<(bool, String)>>`. The inner vector
/// stores the list of literals OR'ed together, and the outer vector stores the
/// list of clauses AND'ed together. The `bool` and `String` describe the
/// polarity and name of the literal.
///
/// ```rust
/// use aris::expr::Expr;
/// # use aris::expr::CnfExpr;
/// assert_eq!(Expr::Taut.into_cnf(), Some(CnfExpr::taut()));
/// ```
///
/// [cnf]: https://en.wikipedia.org/wiki/Conjunctive_normal_form
#[derive(Clone, Debug, PartialEq, Eq, Hash, Ord, PartialOrd)]
pub struct CnfExpr(Vec<Vec<(bool, String)>>);

impl fmt::Display for Op {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match self {
            Op::And => write!(f, "∧"),
            Op::Or => write!(f, "∨"),
            Op::Bicon => write!(f, "↔"),
            Op::Equiv => write!(f, "≡"),
            Op::Add => write!(f, "+"),
            Op::Mult => write!(f, "*"),
        }
    }
}

impl fmt::Display for QuantKind {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match self {
            QuantKind::Forall => write!(f, "∀"),
            QuantKind::Exists => write!(f, "∃"),
        }
    }
}

/// Format associative operator expression
///
/// ## Parameters
///   * `op` - associative operator
///   * `exprs` - operands to operator
fn assoc_display_helper<O, E>(f: &mut fmt::Formatter, op: O, exprs: &[E]) -> fmt::Result
where
    O: fmt::Display,
    E: fmt::Display,
{
    let s = exprs
        .iter()
        .map(E::to_string)
        .collect::<Vec<String>>()
        .join(&format!(" {} ", op));
    write!(f, "({})", s)
}

impl fmt::Display for Expr {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match self {
            Expr::Contra => write!(f, "⊥"),
            Expr::Taut => write!(f, "⊤"),
            Expr::Var { name } => write!(f, "{}", name),
            Expr::Apply { func, args } => write!(
                f,
                "{}({})",
                func,
                args.iter()
                    .map(|x| x.to_string())
                    .collect::<Vec<String>>()
                    .join(", ")
            ),
            Expr::Not { operand } => write!(f, "¬{}", operand),
            Expr::Impl { left, right } => write!(f, "({} → {})", left, right),
            Expr::Assoc { op, exprs } => assoc_display_helper(f, op, exprs),
            Expr::Quant { kind, name, body } => write!(f, "({} {}, {})", kind, name, body),
        }
    }
}

impl fmt::Display for NnfExpr {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match self {
            NnfExpr::Lit { polarity, name } => {
                let neg = if *polarity { "" } else { "¬" };
                write!(f, "{}{}", neg, name)
            }
            NnfExpr::And { exprs } => assoc_display_helper(f, "∧", exprs),
            NnfExpr::Or { exprs } => assoc_display_helper(f, "∨", exprs),
        }
    }
}

/// Calculates the set of [free variables][1] in an expression
///
/// 1: https://en.wikipedia.org/wiki/Free_variables_and_bound_variables
pub fn free_vars(expr: &Expr) -> HashSet<String> {
    match expr {
        Expr::Contra => hashset![],
        Expr::Taut => hashset![],
        Expr::Var { name } => hashset![name.clone()],
        Expr::Apply { func, args } => {
            // Iterator over free vars in arguments
            let arg_free_vars = args.iter().flat_map(free_vars);

            free_vars(func).into_iter().chain(arg_free_vars).collect()
        }
        Expr::Not { operand } => free_vars(operand),
        Expr::Impl { left, right, .. } => &free_vars(left) | &free_vars(right),
        Expr::Assoc { exprs, .. } => exprs.iter().flat_map(free_vars).collect(),
        Expr::Quant { name, body, .. } => {
            let mut ret = free_vars(body);
            ret.remove(name);
            ret
        }
    }
}

/// Generate a variable name that doesn't exist in a set.
///
/// If `prefix` is not in `avoid`, `prefix` will be returned.
///
/// ## Parameters
///   * `prefix` - prefix of variable name to generate
///   * `avoid` - set of names to avoid returning
pub fn gen_var(prefix: &str, avoid: &HashSet<String>) -> String {
    if !avoid.contains(prefix) {
        return prefix.to_owned();
    }

    for i in 0u64.. {
        let ret = format!("{}{}", prefix, i);
        if !avoid.contains(&ret) {
            return ret;
        }
    }
    panic!("Somehow used more than 2^64 vars without finding anything?")
}

/// Replace a given free variable with an expression.
///
/// The replacement is capture-avoiding, meaning that the free variables in
/// `replacement` will remain free in the returned value. This works by renaming
/// the bound variables in quantifiers to prevent them from binding variables in
/// `replacement`.
///
/// ## Parameters
///   * `expr` - expression to check for matching free variables
///   * `var_to_replace` - variable name to be replaced
///   * `replacement` - expression that replaces the variable
pub fn subst(expr: Expr, var_to_replace: &str, replacement: Expr) -> Expr {
    match expr {
        Expr::Contra => Expr::Contra,
        Expr::Taut => Expr::Taut,
        Expr::Var { name } => {
            if name == var_to_replace {
                replacement
            } else {
                Expr::Var { name }
            }
        }
        Expr::Apply { func, args } => Expr::Apply {
            func: Box::new(subst(*func, var_to_replace, replacement.clone())),
            args: args
                .into_iter()
                .map(|expr| subst(expr, var_to_replace, replacement.clone()))
                .collect(),
        },
        Expr::Not { operand } => Expr::Not {
            operand: Box::new(subst(*operand, var_to_replace, replacement)),
        },
        Expr::Impl { left, right } => Expr::Impl {
            left: Box::new(subst(*left, var_to_replace, replacement.clone())),
            right: Box::new(subst(*right, var_to_replace, replacement)),
        },
        Expr::Assoc { op, exprs } => Expr::Assoc {
            op,
            exprs: exprs
                .into_iter()
                .map(|expr| subst(expr, var_to_replace, replacement.clone()))
                .collect(),
        },
        Expr::Quant { kind, name, body } => {
            if name == var_to_replace {
                // Variable is bound here, so can stop replacement
                Expr::Quant { kind, name, body }
            } else {
                // Capture-avoidance behavior, rename the quantified variable if
                // it collides with free variables in the replacement.

                let old_name = name;

                // New quantifier variable name
                let name = gen_var(&old_name, &free_vars(&replacement));

                // Change quantified variable
                let body = subst(*body, &old_name, Expr::var(&name));

                // Now it's safe to continue substitution
                let body = subst(body, var_to_replace, replacement);

                let body = Box::new(body);
                Expr::Quant { kind, name, body }
            }
        }
    }
}

/// Constraints that should hold for a substitution, maintained in a set during unification
#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub enum Constraint {
    /// Require that two subexpressions must be equal
    Equal(Expr, Expr),
}


/// A substitution of variable names to `Expr`s, meant to be passed to `subst`
#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub struct Substitution(pub Vec<(String, Expr)>);

impl Substitution {
    /// Apply all the pairs in a substitution to an expression
    pub fn apply(&self, expr: Expr) -> Expr {
        self.0.iter().fold(expr, |z, (x, y)| subst(z, x, y.clone())) 
    }
}

/// Unifies a set of equality constraints on expressions, giving a list of substitutions that make constrained expressions equal.
/// a == b -> unify(HashSet::from_iter(vec![Equal(a, b)])) == Some(vec![])
pub fn unify(mut c: HashSet<Constraint>) -> Option<Substitution> {
    // inspired by TAPL 22.4
    //println!("\t{:?}", c);
    let mut c_ = c.clone();
    let Constraint::Equal(left, right) = if let Some(x) = c_.drain().next() {
        c.remove(&x);
        x
    } else {
        return Some(Substitution(vec![]));
    };
    let subst_set = |x, e1: Expr, set: HashSet<_>| {
        set.into_iter()
            .map(
                |Constraint::Equal(e2, e3)| {
                    Constraint::Equal(subst(e2, x, e1.clone()), subst(e3, x, e1.clone()))
                },
            )
            .collect::<_>()
    };
    let (fvs, fvt) = (free_vars(&left), free_vars(&right));
    match (left, right) {
        (left, right) if left == right => unify(c),
        (Expr::Var { name: sname }, right) if !fvt.contains(&sname) => {
            unify(subst_set(&sname, right.clone(), c)).map(|mut x| {
                x.0.push((sname.clone(), right.clone()));
                x
            })
        }
        (left, Expr::Var { name: tname }) if !fvs.contains(&tname) => {
            unify(subst_set(&tname, left.clone(), c)).map(|mut x| {
                x.0.push((tname.clone(), left.clone()));
                x
            })
        }
        (Expr::Not { operand: s }, Expr::Not { operand: t }) => {
            c.insert(Constraint::Equal(*s, *t));
            unify(c)
        }
        (
            Expr::Impl {
                left: sl,
                right: sr,
            },
            Expr::Impl {
                left: tl,
                right: tr,
            },
        ) => {
            c.insert(Constraint::Equal(*sl, *tl));
            c.insert(Constraint::Equal(*sr, *tr));
            unify(c)
        }
        (Expr::Apply { func: sf, args: sa }, Expr::Apply { func: tf, args: ta })
            if sa.len() == ta.len() =>
        {
            c.insert(Constraint::Equal(*sf, *tf));
            c.extend(
                sa.into_iter()
                    .zip(ta.into_iter())
                    .map(|(x, y)| Constraint::Equal(x, y)),
            );
            unify(c)
        }
        (Expr::Assoc { op: so, exprs: se }, Expr::Assoc { op: to, exprs: te })
            if so == to && se.len() == te.len() =>
        {
            c.extend(se.iter().zip(te.iter()).map(|(x, y)| Constraint::Equal(x.clone(), y.clone())));
            unify(c)
        }
        (
            Expr::Quant {
                kind: sk,
                name: sn,
                body: sb,
            },
            Expr::Quant {
                kind: tk,
                name: tn,
                body: tb,
            },
        ) if sk == tk => {
            let uv = gen_var("__unification_var", &fvs.union(&fvt).cloned().collect());
            // require that the bodies of the quantifiers are alpha-equal by substituting a fresh constant
            c.insert(Constraint::Equal(subst(*sb, &sn, Expr::var(&uv)), subst(*tb, &tn, Expr::var(&uv))));
            // if the constant escapes, then a free variable in one formula unified with a captured variable in the other, so the values don't unify
            unify(c).and_then(|sub| {
                if sub
                    .0
                    .iter()
                    .any(|(x, y)| x == &uv || free_vars(y).contains(&uv))
                {
                    None
                } else {
                    Some(sub)
                }
            })
        }
        _ => None,
    }
}

/*
Note apply_non_literal

In order to make substitution not special case predicate/function application, Expr::Apply's func field is a general Box<Expr> instead of a String.
This also makes sense for eventually supporting lambda expressions using Expr::Quantifier nodes.
Currently, the parser will never produce Expr::Apply nodes that have a func that is not an Expr::Var, and some code depends on this to avoid handling a more difficult general case.
*/

impl Expr {
    /// Helper for constructing `Var` nodes
    pub fn var(name: &str) -> Expr {
        Expr::Var { name: name.into() }
    }
    /// Helper for constructing `Apply` nodes
    pub fn apply(func: Expr, args: &[Expr]) -> Expr {
        Expr::Apply {
            func: Box::new(func),
            args: args.to_vec(),
        }
    }
    /// Helper for constructing `Not` nodes
    pub fn not(expr: Expr) -> Expr {
        Expr::Not {
            operand: Box::new(expr),
        }
    }
    /// Construct an error message placeholder for an implication
    pub fn impl_place_holder() -> Expr {
        Expr::implies(Expr::var("_"), Expr::var("_"))
    }
    /// Helper for constructing `Impl` nodes
    pub fn implies(left: Expr, right: Expr) -> Expr {
        Expr::Impl {
            left: Box::new(left),
            right: Box::new(right),
        }
    }
    /// Helper for constructing `Or` nodes
    pub fn or(l: Expr, r: Expr) -> Expr {
        Expr::assoc(Op::Or, &[l, r])
    }
    /// Helper for constructing `Assoc` nodes
    pub fn assoc(op: Op, exprs: &[Expr]) -> Expr {
        Expr::Assoc {
            op,
            exprs: exprs.to_vec(),
        }
    }
    /// Construct an error message placeholder for an associative operator
    pub fn assocplaceholder(op: Op) -> Expr {
        Expr::assoc(op, &[Expr::var("_"), Expr::var("_"), Expr::var("...")])
    }
    /// Construct an error message placeholder for a quantifier
    pub fn quant_placeholder(kind: QuantKind) -> Expr {
        Expr::Quant {
            kind,
            name: "_".to_owned(),
            body: Box::new(Expr::var("_")),
        }
    }
    /// Helper for constructing `Forall` nodes
    pub fn forall(name: &str, body: Expr) -> Expr {
        Expr::Quant {
            kind: QuantKind::Forall,
            name: name.into(),
            body: Box::new(body),
        }
    }
    /// Helper for constructing `Exists` nodes
    pub fn exists(name: &str, body: Expr) -> Expr {
        Expr::Quant {
            kind: QuantKind::Exists,
            name: name.into(),
            body: Box::new(body),
        }
    }
    /// Infer arities (number of arguments) for each variable that occurs free in an expression
    pub fn infer_arities(&self, arities: &mut HashMap<String, usize>) {
        match self {
            Expr::Contra | Expr::Taut => {}
            Expr::Var { name } => {
                arities.entry(name.clone()).or_insert(0);
            }
            Expr::Apply { func, args } => match &**func {
                Expr::Var { name } => {
                    let arity = arities.entry(name.clone()).or_insert_with(|| args.len());
                    *arity = args.len().max(*arity);
                    for arg in args {
                        arg.infer_arities(arities);
                    }
                }
                _ => panic!("See note apply_non_literal"),
            },
            Expr::Not { operand } => {
                operand.infer_arities(arities);
            }
            Expr::Impl { left, right } => {
                left.infer_arities(arities);
                right.infer_arities(arities)
            }
            Expr::Assoc { op: _, exprs } => {
                for e in exprs {
                    e.infer_arities(arities);
                }
            }
            Expr::Quant {
                kind: _,
                name,
                body,
            } => {
                let mut body_arities = HashMap::new();
                body.infer_arities(&mut body_arities);
                for (k, v) in body_arities.into_iter() {
                    if &*k != name {
                        let arity = arities.entry(k).or_insert(v);
                        *arity = v.max(*arity);
                    }
                }
            }
        }
    }
    /// Evaluate a quantifier-free boolean expression, given values for all the free variables as truth tables of their arities
    /// panics on unbound variables or expressions with quantifiers or arithmetic
    pub fn eval(&self, env: &HashMap<String, Vec<bool>>) -> bool {
        match self {
            Expr::Contra => false,
            Expr::Taut => true,
            Expr::Var { name } => env[name][0], // variables are 0-arity functions
            Expr::Apply { func, args } => match &**func {
                Expr::Var { name } => {
                    let evaled_args: Vec<bool> = args.iter().map(|arg| arg.eval(env)).collect();
                    let mut index: usize = 0;
                    for (i, x) in evaled_args.into_iter().enumerate() {
                        index |= (x as usize) << i;
                    }
                    env[&*name][index]
                }
                _ => panic!("See note apply_non_literal"),
            },
            Expr::Not { operand } => !operand.eval(env),
            Expr::Impl { left, right } => {
                let (x, y) = (left.eval(env), right.eval(env));
                !x || y
            }
            Expr::Assoc { op, exprs } => {
                let (mut ret, f): (bool, &dyn Fn(bool, bool) -> bool) = match op {
                    Op::And => (true, &|x, y| x && y),
                    Op::Or => (false, &|x, y| x || y),
                    Op::Bicon => (true, &|x, y| x == y),
                    Op::Equiv | Op::Add | Op::Mult => unimplemented!(),
                };
                for b in exprs.iter().map(|e| e.eval(env)) {
                    ret = f(ret, b);
                }
                ret
            }
            Expr::Quant { .. } => panic!("Expr::eval does not support quantifiers"),
        }
    }
    /// Sort all commutative associative operators to normalize expressions in the case of arbitrary ordering
    /// Eg (B & A) ==> (A & B)
    pub fn sort_commutative_ops(self) -> Expr {
        self.transform(&|e| match e {
            Expr::Assoc { op, mut exprs } => {
                let is_sorted = exprs.windows(2).all(|xy| xy[0] <= xy[1]);
                if !is_sorted {
                    exprs.sort();
                    (Expr::Assoc { op, exprs }, true)
                } else {
                    (Expr::Assoc { op, exprs }, false)
                }
            }
            _ => (e, false),
        })
    }

    /// Combine associative operators such that nesting is flattened
    /// Eg (A & (B & C)) ==> (A & B & C)
    pub fn combine_associative_ops(self) -> Expr {
        self.transform(&|e| match e {
            Expr::Assoc {
                op: op_1,
                exprs: exprs_1,
            } => {
                let mut result = vec![];
                let mut combined = false;
                for expr in exprs_1 {
                    if let Expr::Assoc {
                        op: op_2,
                        exprs: exprs_2,
                    } = expr
                    {
                        if op_1 == op_2 {
                            result.extend(exprs_2);
                            combined = true;
                        } else {
                            result.push(Expr::Assoc {
                                op: op_2,
                                exprs: exprs_2,
                            });
                        }
                    } else {
                        result.push(expr);
                    }
                }
                (
                    Expr::Assoc {
                        op: op_1,
                        exprs: result,
                    },
                    combined,
                )
            }
            _ => (e, false),
        })
    }

    /// Helper function for `tranform()`; use the `trans` function to transform
    /// `expr`, yielding a tuple of the transformed expression and a `bool`
    /// indicating whether the expression can be transformed again.
    fn transform_expr_inner<Trans>(expr: Expr, trans: &Trans) -> (Expr, bool)
    where
        Trans: Fn(Expr) -> (Expr, bool),
    {
        let (result, status) = trans(expr);
        let (result, status2) = match result {
            // Base cases: these just got transformed above so no need to recurse them
            e @ Expr::Contra => (e, false),
            e @ Expr::Taut => (e, false),
            e @ Expr::Var { .. } => (e, false),

            // Recursive cases: transform each of the sub-expressions of the various compound expressions
            // and then construct a new instance of that compound expression with their transformed results.
            // If any transformation is successful, we return success
            Expr::Apply { func, args } => {
                let (func, fs) = Self::transform_expr_inner(*func, trans);
                let func = Box::new(func);
                // Fancy iterator hackery to transform each sub expr and then collect all their results
                let (args, stats): (Vec<_>, Vec<_>) = args
                    .into_iter()
                    .map(move |expr| Self::transform_expr_inner(expr, trans))
                    .unzip();
                let success = fs || stats.into_iter().any(|x| x);
                (Expr::Apply { func, args }, success)
            }
            Expr::Not { operand } => {
                let (operand, success) = Self::transform_expr_inner(*operand, trans);
                let operand = Box::new(operand);
                (Expr::Not { operand }, success)
            }
            Expr::Impl { left, right } => {
                let (left, ls) = Self::transform_expr_inner(*left, trans);
                let (right, rs) = Self::transform_expr_inner(*right, trans);
                let left = Box::new(left);
                let right = Box::new(right);
                let success = ls || rs;
                (Expr::Impl { left, right }, success)
            }
            Expr::Assoc { op, exprs } => {
                let (exprs, stats): (Vec<_>, Vec<_>) = exprs
                    .into_iter()
                    .map(move |expr| Self::transform_expr_inner(expr, trans))
                    .unzip();
                let success = stats.into_iter().any(|x| x);
                (Expr::Assoc { op, exprs }, success)
            }
            Expr::Quant { kind, name, body } => {
                let (body, success) = Self::transform_expr_inner(*body, trans);
                let body = Box::new(body);
                (Expr::Quant { kind, name, body }, success)
            }
        };
        // The key to this function is that it returns true if ANYTHING was transformed. That means
        // if either the whole expression or any of the inner expressions, we should re-run on everything.
        (result, status || status2)
    }

    /// Recursive transforming visitor over an expression
    /// trans_fn is a function that takes an Expr and returns one of two things:
    /// 1. If this expression is not transformable, (original expr, false)
    /// 2. If this expression is transformable, (transformed expr, true)
    ///
    /// This function basically does a recursive worklist over the expression hierarchy, trying to transform
    /// any expression and all the sub-expressions it contains. If your transformation function succeeds,
    /// it will also traverse your result. This will loop infinitely if your transformation creates patterns
    /// that it matches.
    pub fn transform<Trans>(self, trans_fn: &Trans) -> Expr
    where
        Trans: Fn(Expr) -> (Expr, bool),
    {
        // Worklist: Keep reducing and transforming as long as something changes. This will loop infinitely
        // if your transformation creates patterns that it matches.
        let (mut result, mut status) = Self::transform_expr_inner(self, trans_fn);
        while status {
            // Rust pls
            let (x, y) = Self::transform_expr_inner(result, trans_fn);
            result = x;
            status = y;
        }
        result
    }

    /// Like `transform_set()`, but the result is converted to a vector. This is
    /// used because `itertools::Itertools::cartesian_product` requires `Clone`,
    /// which `std::collections::hash_set::IntoIter` doesn't implement.
    fn transform_set_vec<Trans>(self, trans_fn: &Trans) -> Vec<Expr>
    where
        Trans: Fn(Expr) -> (Expr, bool),
    {
        self.transform_set(trans_fn).into_iter().collect()
    }

    /// Recursive transforming visitor over an expression, yielding a set of
    /// possible transformations. The parameter `trans_fn` takes an Expr and
    /// returns one of two things:
    ///
    ///   1. If this expression is not transformable, (original expr, false)
    ///   2. If this expression is transformable, (transformed expr, true)
    ///
    /// This should be used for non-confluent rewriting rules, and `transform()`
    /// should be used for confluent rewriting rules.
    pub fn transform_set<Trans>(self, trans_fn: &Trans) -> HashSet<Expr>
    where
        Trans: Fn(Expr) -> (Expr, bool),
    {
        let mut set = HashSet::new();

        // Add all incremental normalization levels to set. Keep transforming
        // the expression and adding the result to the set, until the expression
        // is not transformable.
        {
            let mut expr = self;
            let mut normable = true;
            set.insert(expr.clone());
            while normable {
                let result = trans_fn(expr.clone());
                expr = result.0;
                normable = result.1;
                set.insert(expr.clone());
            }
        }

        // Now that all incremental normalization levels are in the set,
        // recursively run this on all sub-nodes of the expression.
        for expr in set.clone() {
            match expr {
                // Base case: no sub-nodes
                Expr::Contra => {}
                Expr::Taut => {}
                Expr::Var { .. } => {}

                // Add the Cartesian product of the set of `func`
                // transformations and the sets of transformations of `args`
                Expr::Apply { func, args } => {
                    let func_set = func.transform_set(trans_fn).into_iter().map(Box::new);
                    let args_set = args
                        .into_iter()
                        .map(|arg| arg.transform_set_vec(trans_fn))
                        .multi_cartesian_product();
                    set.extend(
                        func_set
                            .cartesian_product(args_set)
                            .map(|(func, args)| Expr::Apply { func, args }),
                    );
                }

                // Add the set of transformations of `operand`
                Expr::Not { operand } => {
                    set.extend(
                        operand
                            .transform_set(trans_fn)
                            .into_iter()
                            .map(Box::new)
                            .map(move |operand| Expr::Not { operand }),
                    );
                }

                // Add the Cartesian product of the transformation sets of the
                // `left` and `right` sub-nodes
                Expr::Impl { left, right } => {
                    let left_set = left.transform_set(trans_fn).into_iter().map(Box::new);
                    let right_set = right.transform_set_vec(trans_fn).into_iter().map(Box::new);
                    let binop_set = left_set
                        .cartesian_product(right_set)
                        .map(|(left, right)| Expr::Impl { left, right });
                    set.extend(binop_set);
                }

                // Add the Cartesian product of the transformation sets of the
                // sub-nodes in `exprs`
                Expr::Assoc { op, exprs } => {
                    set.extend(
                        exprs
                            .into_iter()
                            .map(|expr| expr.transform_set_vec(trans_fn))
                            .multi_cartesian_product()
                            .map(|exprs| Expr::Assoc { op, exprs }),
                    );
                }

                // Add the set of transformations of `body`
                Expr::Quant { kind, name, body } => {
                    let body_set = body.transform_set(trans_fn).into_iter().map(Box::new);
                    let quant_set = body_set.map(|body| Expr::Quant {
                        kind,
                        name: name.clone(),
                        body,
                    });
                    set.extend(quant_set);
                }
            }
        }

        set
    }

    /// Simplify an expression with recursive DeMorgan's
    /// ~(A ^ B) <=> ~A v ~B  /  ~(A v B) <=> ~A ^ ~B
    /// Strategy: Expr::Apply this to all ~(A ^ B) constructions
    /// This should leave us with an expression in "DeMorgans'd normal form"
    /// With no ~(A ^ B) / ~(A v B) expressions
    pub fn normalize_demorgans(self) -> Expr {
        self.transform(&|expr| {
            let demorgans = |op, exprs: Vec<Expr>| Expr::Assoc {
                op,
                exprs: exprs
                    .into_iter()
                    .map(|expr| Expr::Not {
                        operand: Box::new(expr),
                    })
                    .collect(),
            };

            match expr {
                Expr::Not { operand } => match *operand {
                    Expr::Assoc { op: Op::And, exprs } => (demorgans(Op::Or, exprs), true),
                    Expr::Assoc { op: Op::Or, exprs } => (demorgans(Op::And, exprs), true),
                    _ => (Expr::not(*operand), false),
                },
                _ => (expr, false),
            }
        })
    }

    /// Reduce an expression over idempotence, that is:
    /// A & A -> A
    /// A | A -> A
    /// In a manner equivalent to normalize_demorgans
    pub fn normalize_idempotence(self) -> Expr {
        self.transform(&|expr| {
            match expr {
                Expr::Assoc {
                    op: op @ Op::And,
                    exprs,
                }
                | Expr::Assoc {
                    op: op @ Op::Or,
                    exprs,
                } => {
                    let mut unifies = true;
                    // (0, 1), (1, 2), ... (n - 2, n - 1)
                    for pair in exprs.windows(2) {
                        // Just doing a basic AST equality. Could replace this with unify if we want
                        // to be stronger
                        if pair[0] != pair[1] {
                            unifies = false;
                            break;
                        }
                    }

                    if unifies {
                        // Just use the first one
                        (exprs.into_iter().next().unwrap(), true)
                    } else {
                        (Expr::Assoc { op, exprs }, false)
                    }
                }
                _ => (expr, false),
            }
        })
    }

    /// View the top-level disjuncts of an Expr, counting contradiction as an empty disjunction. Useful for SAT interopration.
    pub fn disjuncts(&self) -> Vec<Expr> {
        match self {
            Expr::Contra => vec![],
            Expr::Assoc { op: Op::Or, exprs } => exprs.clone(),
            _ => vec![self.clone()],
        }
    }
    /// Turn a list of disjuncts into an Expr, handling the 0/1 cases in a way that maintains the invariant that Assoc's exprs.len() > 2.
    pub fn from_disjuncts(mut disjuncts: Vec<Expr>) -> Expr {
        match disjuncts.len() {
            0 => Expr::Contra,
            1 => disjuncts.pop().unwrap(),
            _ => Expr::Assoc {
                op: Op::Or,
                exprs: disjuncts,
            },
        }
    }
    /// View the top-level conjuncts of an Expr, counting tautology as an empty conjunction. Useful for SAT interopration.
    pub fn conjuncts(&self) -> Vec<Expr> {
        match self {
            Expr::Taut => vec![],
            Expr::Assoc { op: Op::And, exprs } => exprs.clone(),
            _ => vec![self.clone()],
        }
    }
    /// Turn a list of conjuncts into an Expr, handling the 0/1 cases in a way that maintains the invariant that Assoc's exprs.len() > 2.
    pub fn from_conjuncts(mut conjuncts: Vec<Expr>) -> Expr {
        match conjuncts.len() {
            0 => Expr::Taut,
            1 => conjuncts.pop().unwrap(),
            _ => Expr::Assoc {
                op: Op::And,
                exprs: conjuncts,
            },
        }
    }
    /// Push negation inwards through forall/exists, flipping the quantifier kind in the process
    pub fn negate_quantifiers(self) -> Expr {
        self.transform(&|expr| {
            let gen_opposite = |kind, name, body| Expr::Quant {
                kind,
                name,
                body: Box::new(Expr::not(body)),
            };

            match expr {
                Expr::Not { operand } => match *operand {
                    Expr::Quant { kind, name, body } => match kind {
                        QuantKind::Exists => (gen_opposite(QuantKind::Forall, name, *body), true),
                        QuantKind::Forall => (gen_opposite(QuantKind::Exists, name, *body), true),
                    },
                    _ => (Expr::not(*operand), false),
                },
                _ => (expr, false),
            }
        })
    }
    /// Remove any quantifiers whose names are unused in their bodies
    pub fn normalize_null_quantifiers(self) -> Expr {
        self.transform(&|expr| {
            match expr {
                Expr::Quant { kind, name, body } => {
                    if free_vars(&body).contains(&name) {
                        (Expr::Quant { kind, name, body }, false)
                    } else {
                        // if name is not free in body, then the quantifier isn't binding anything and can be removed
                        (*body, true)
                    }
                }
                _ => (expr, false),
            }
        })
    }
    /// Replace all bound variables with DeBruijn indices, for testing alpha-equivalence
    /// if `a.replacing_bound_vars() == b.replacing_bound_vars()`, then `a` is alpha-equivalent to `b`
    pub fn replacing_bound_vars(self) -> Expr {
        // replaces the letter names with numbers
        fn aux(expr: Expr, mut gamma: Vec<String>) -> Expr {
            match expr {
                Expr::Var { name } => {
                    // look up the name in gamma, get the index
                    let i = gamma
                        .into_iter()
                        .enumerate()
                        .find(|(_, n)| n == &name)
                        .unwrap()
                        .0;
                    Expr::Var {
                        name: format!("{}", i),
                    }
                }
                // push the name onto gamma from the actual quantifier,
                // Example: for forall x, P(x)
                // push x onto gamma
                // save the length of gamma before recursing, to use as the new name
                Expr::Quant { kind, name, body } => {
                    let current_level = format!("{}", gamma.len());
                    gamma.push(name);
                    let new_body = aux(*body, gamma);
                    Expr::Quant {
                        kind,
                        name: current_level,
                        body: Box::new(new_body),
                    }
                }
                // All the remainder cases
                Expr::Contra => Expr::Contra,
                Expr::Taut => Expr::Taut,
                Expr::Apply { func, args } => {
                    let func = aux(*func, gamma.clone());
                    let args = args.into_iter().map(|e| aux(e, gamma.clone())).collect();
                    Expr::Apply {
                        func: Box::new(func),
                        args,
                    }
                }
                Expr::Not { operand } => Expr::Not {
                    operand: Box::new(aux(*operand, gamma)),
                },
                Expr::Impl { left, right } => {
                    let left = Box::new(aux(*left, gamma.clone()));
                    let right = Box::new(aux(*right, gamma));
                    Expr::Impl { left, right }
                }
                Expr::Assoc { op, exprs } => {
                    let exprs = exprs.into_iter().map(|e| aux(e, gamma.clone())).collect();
                    Expr::Assoc { op, exprs }
                }
            }
        }

        let mut gamma = vec![];
        gamma.extend(free_vars(&self));

        let mut ret = aux(self, gamma.clone());
        // at this point, we've numbered all the vars, including the free ones
        // replace the free vars with their original names
        for (i, name) in gamma.into_iter().enumerate() {
            ret = subst(ret, &format!("{}", i), Expr::Var { name });
        }
        ret
    }
    /// Sort the names of quantified variables within runs of quantifiers of the same kind
    pub fn swap_quantifiers(self) -> Expr {
        // check for quantifier,
        // as long as the prefix is the same,
        // keep pushing variables into a set.
        // as soon as you reach a non-quantifier,
        // rewrap all the quantifiers in sorted order
        // if the sorted set is the same as the initial set, return false
        self.transform(&|expr| {
            let mut mod_expr = expr.clone();
            let mut stack = vec![];
            let mut last_quantifier = None;

            while let Expr::Quant { kind, name, body } = mod_expr {
                if last_quantifier.is_none() || last_quantifier == Some(kind) {
                    last_quantifier = Some(kind);
                    stack.push(name);
                    mod_expr = *body;
                } else {
                    mod_expr = Expr::Quant { kind, name, body };
                    break;
                }
            }

            stack.sort();

            if let Some(kind) = last_quantifier {
                for l_name in stack {
                    mod_expr = Expr::Quant {
                        kind,
                        name: l_name,
                        body: Box::new(mod_expr),
                    };
                }

                return (mod_expr.clone(), mod_expr != expr);
            }

            (expr, false)
        })
    }

    /// 7a1. forall x, (phi(x) ∧ psi) == (forall x, phi(x)) ∧ psi
    /// 7a2. exists x, (phi(x) ∧ psi) == (exists x, phi(x)) ∧ psi
    /// 7b1. forall x, (phi(x) ∨ psi) == (forall x, phi(x)) ∨ psi
    /// 7b2. exists x, (phi(x) ∨ psi) == (exists x, phi(x)) ∨ psi
    /// 7c1. forall x, (phi(x) → psi) == (exists x, phi(x)) → psi (! Expr::Quantifier changes!)
    /// 7c2. exists x, (phi(x) → psi) == (forall x, phi(x)) → psi (! Expr::Quantifier changes!)
    /// 7d1. forall x, (psi → phi(x)) == psi → (forall x, phi(x))
    /// 7d2. exists x, (psi → phi(x)) == psi → (exists x, phi(x))
    pub fn normalize_prenex_laws(self) -> Expr {
        let transform_7ab = |op: Op, exprs: Vec<Expr>| {
            // hoist a forall out of an and/or when the binder won't capture any of the other arms
            // if the binder doesn't occur in `all_free` (the union of all the arms freevars), it won't induce capturing
            let mut all_free = HashSet::new();
            for expr in &exprs {
                all_free.extend(free_vars(&expr));
            }
            let mut found = None;
            let mut others = vec![];
            for expr in exprs.into_iter() {
                match expr {
                    Expr::Quant { kind, name, body } => {
                        if found.is_none() && !all_free.contains(&name) {
                            found = Some((kind, name));
                            others.push(*body);
                        } else {
                            others.push(Expr::Quant { kind, name, body });
                        }
                    }
                    _ => others.push(expr),
                }
            }
            if let Some((kind, name)) = found {
                let body = Box::new(Expr::Assoc { op, exprs: others });
                (Expr::Quant { kind, name, body }, true)
            } else {
                // if none of the subexpressions were quantifiers whose binder was free, `others` should be in the same as `exprs`
                (Expr::Assoc { op, exprs: others }, false)
            }
        };
        let reconstruct_7cd = |kind: QuantKind, name: String, left, right| {
            let body = Box::new(Expr::Impl { left, right });
            (Expr::Quant { kind, name, body }, true)
        };
        self.transform(&|expr| {
            match expr {
                Expr::Assoc { op, exprs } => match op {
                    Op::And => transform_7ab(Op::And, exprs),
                    Op::Or => transform_7ab(Op::Or, exprs),
                    _ => (Expr::Assoc { op, exprs }, false),
                },
                Expr::Impl {
                    mut left,
                    mut right,
                } => {
                    let left_free = free_vars(&left);
                    let right_free = free_vars(&right);
                    left = match *left {
                        Expr::Quant { kind, name, body } if !right_free.contains(&name) => {
                            // 7c case, quantifier is flipped
                            match kind {
                                QuantKind::Forall => {
                                    return reconstruct_7cd(QuantKind::Exists, name, body, right);
                                }
                                QuantKind::Exists => {
                                    return reconstruct_7cd(QuantKind::Forall, name, body, right);
                                }
                            }
                        }
                        left => Box::new(left),
                    };
                    right = match *right {
                        Expr::Quant { kind, name, body } if !left_free.contains(&name) => {
                            // 7d case, quantifier is not flipped
                            // exhaustive match despite the bodies being the same: since if more quantifiers are added, should reconsider here instead of blindly hoisting the new quantifier
                            match kind {
                                QuantKind::Forall => {
                                    return reconstruct_7cd(QuantKind::Forall, name, left, body);
                                }
                                QuantKind::Exists => {
                                    return reconstruct_7cd(QuantKind::Exists, name, left, body);
                                }
                            }
                        }
                        right => Box::new(right),
                    };
                    (Expr::Impl { left, right }, false)
                }
                _ => (expr, false),
            }
        })
    }

    pub fn aristotelean_square(self) -> Expr {
        self.transform(&|expr| {
            let gen_opposite = |kind, name, body| match kind {
                QuantKind::Exists => (
                    Expr::Quant {
                        kind: QuantKind::Forall,
                        name,
                        body,
                    },
                    true,
                ),
                QuantKind::Forall => (
                    Expr::Quant {
                        kind: QuantKind::Exists,
                        name,
                        body,
                    },
                    true,
                ),
            };

            let orig_expr = expr.clone();
            match expr {
                // find unop quantifier on the left
                Expr::Not { operand } => {
                    match *operand {
                        Expr::Quant { kind, name, body } => {
                            match *body {
                                // find implies, turn into associative binop
                                Expr::Impl { left, right } => {
                                    let new_exprs = vec![*left, Expr::not(*right)];

                                    let new_body = Expr::Assoc {
                                        op: Op::And,
                                        exprs: new_exprs,
                                    };
                                    gen_opposite(kind, name, Box::new(new_body))
                                }

                                // find and with exactly 2 exprs
                                Expr::Assoc { op: Op::And, exprs } => {
                                    if exprs.len() != 2 {
                                        (orig_expr, false)
                                    } else {
                                        let new_body = Expr::Impl {
                                            left: Box::new(exprs[0].clone()),
                                            right: Box::new(Expr::not(exprs[1].clone())),
                                        };

                                        gen_opposite(kind, name, Box::new(new_body))
                                    }
                                }

                                _ => (orig_expr, false),
                            }
                        }
                        _ => (Expr::not(*operand), false),
                    }
                }

                _ => (expr, false),
            }
        })
    }

    /// Distribute forall/exists into and/or
    pub fn quantifier_distribution(self) -> Expr {
        let push_quantifier_inside = |kind: QuantKind, qname: String, exprs: &mut Vec<Expr>| {
            for iter in exprs.iter_mut() {
                match kind {
                    QuantKind::Exists => {
                        let tmp = mem::replace(iter, Expr::Contra);
                        *iter = Expr::exists(qname.as_str(), tmp);
                    }

                    QuantKind::Forall => {
                        let tmp = mem::replace(iter, Expr::Contra);
                        *iter = Expr::forall(qname.as_str(), tmp);
                    }
                }
            }
        };

        self.transform(&|expr| {
            let orig_expr = expr.clone();

            match expr {
                Expr::Quant { kind, name, body } => {
                    match *body {
                        Expr::Assoc { op, mut exprs } => {
                            // continue only if op is And or Or
                            match op {
                                Op::And | Op::Or => {}
                                _ => return (orig_expr, false),
                            };

                            // inline push_quantifier_inside here
                            push_quantifier_inside(kind, name, &mut exprs);
                            (Expr::assoc(op, &exprs), true)
                        }
                        _ => (orig_expr, false),
                    }
                }
                _ => (expr, false),
            }
        })
    }

    /// Convert an [`Expr`](Expr) into a [`CnfExpr`](CnfExpr), or return
    /// [`None`](None) if there are any quantifiers, applications, or
    /// arithmetic.
    ///
    /// ```rust
    /// use aris::parser::parse_unwrap as p;
    /// # use aris::expr::Expr;
    /// use aris::expr::CnfExpr;
    ///
    /// let a = CnfExpr::var("A");
    /// let b = CnfExpr::var("B");
    /// let exprs = vec![a, b];
    ///
    /// assert_eq!(p("A | B").into_cnf().unwrap(), CnfExpr::or(exprs.clone()));
    /// assert_eq!(p("A & B").into_cnf().unwrap(), CnfExpr::and(exprs));
    /// assert_eq!(p("~A").into_cnf().unwrap(), CnfExpr::literal(false, "A"));
    /// ```
    pub fn into_cnf(self) -> Option<CnfExpr> {
        self.into_nnf().map(NnfExpr::into_cnf)
    }

    /// Convert an [`Expr`](Expr) into an [`NnfExpr`](NnfExpr), or return
    /// [`None`](None) if there are any quantifiers, applications, or
    /// arithmetic.
    ///
    /// ```rust
    /// use aris::parser::parse_unwrap as p;
    /// # use aris::expr::Expr;
    /// use aris::expr::NnfExpr;
    ///
    /// let a = NnfExpr::var("A");
    /// let b = NnfExpr::var("B");
    /// let exprs = vec![a, b];
    ///
    /// assert_eq!(p("A | B").into_nnf().unwrap(), NnfExpr::Or { exprs: exprs.clone() });
    /// assert_eq!(p("A & B").into_nnf().unwrap(), NnfExpr::And { exprs });
    /// assert_eq!(p("~A").into_nnf().unwrap(), !NnfExpr::var("A"));
    /// ```
    pub fn into_nnf(self) -> Option<NnfExpr> {
        // Helper function for converting a vector of expressions to NNF
        fn map_nnf(exprs: Vec<Expr>) -> Option<Vec<NnfExpr>> {
            exprs.into_iter().map(Expr::into_nnf).collect()
        }

        // Recursively convert to NNF
        match self {
            // Base cases
            Expr::Contra => Some(NnfExpr::contra()),
            Expr::Taut => Some(NnfExpr::taut()),
            Expr::Var { name } => Some(NnfExpr::var(name)),
            Expr::Apply { .. } | Expr::Quant { .. } => None,

            // Recursive cases
            Expr::Not { operand } => operand.into_nnf().map(NnfExpr::not),
            Expr::Impl { left, right } => {
                let left = left.into_nnf()?;
                let right = right.into_nnf()?;
                Some(left.implies(right))
            }
            Expr::Assoc { op, exprs } => match op {
                Op::And => map_nnf(exprs).map(|exprs| NnfExpr::And { exprs }),
                Op::Or => map_nnf(exprs).map(|exprs| NnfExpr::Or { exprs }),
                Op::Bicon => exprs
                    .into_iter()
                    .map(Self::into_nnf)
                    .collect::<Option<Vec<NnfExpr>>>()?
                    .into_iter()
                    .fold1(NnfExpr::bicon),
                Op::Equiv | Op::Add | Op::Mult => None,
            },
        }
    }
}

impl NnfExpr {
    /// Create a true (tautology) NNF expression.
    ///
    /// ```rust
    /// use aris::parser::parse_unwrap as p;
    /// # use aris::expr::NnfExpr;
    ///
    /// assert_eq!(p("⊤").into_nnf(), Some(NnfExpr::taut()));
    /// ```
    pub fn taut() -> Self {
        // An empty AND
        // AND() ≡ ⊤
        Self::And { exprs: vec![] }
    }

    /// Create a false (contradiction) NNF expression.
    ///
    /// ```rust
    /// use aris::parser::parse_unwrap as p;
    /// # use aris::expr::NnfExpr;
    ///
    /// assert_eq!(p("⊥").into_nnf(), Some(NnfExpr::contra()));
    /// ```
    pub fn contra() -> Self {
        // An empty OR
        // OR() ≡ ⊥
        Self::Or { exprs: vec![] }
    }

    /// Create an NNF expression by applying logical implication to two NNF
    /// expressions.
    ///
    /// ```rust
    /// use aris::parser::parse_unwrap as p;
    /// # use aris::expr::NnfExpr;
    ///
    /// let a = NnfExpr::var("A");
    /// let b = NnfExpr::var("B");
    ///
    /// assert_eq!(p("A -> B").into_nnf(), Some(a.implies(b)));
    /// ```
    pub fn implies(self, other: Self) -> Self {
        // A → B ≡ ¬A ∨ B
        Self::Or {
            exprs: vec![self.not(), other],
        }
    }

    /// Create an NNF expression from a variable name
    ///
    /// ```rust
    /// use aris::parser::parse_unwrap as p;
    /// # use aris::expr::NnfExpr;
    ///
    /// assert_eq!(p("A").into_nnf(), Some(NnfExpr::var("A")));
    /// ```
    pub fn var<S: ToString>(name: S) -> Self {
        let name = name.to_string();
        Self::Lit {
            polarity: true,
            name,
        }
    }

    /// Create an NNF expression by applying the logical biconditional to two NNF
    /// expressions.
    ///
    /// ```rust
    /// use aris::parser::parse_unwrap as p;
    /// # use aris::expr::NnfExpr;
    ///
    /// let a = NnfExpr::var("A");
    /// let b = NnfExpr::var("B");
    ///
    /// assert_eq!(p("A <-> B").into_nnf(), Some(a.bicon(b)));
    /// ```
    pub fn bicon(self, other: Self) -> Self {
        // A ↔ B ≡ (A → B) ∧ (A → B)
        let a = self.clone().implies(other.clone());
        let b = other.implies(self);
        Self::And { exprs: vec![a, b] }
    }

    /// Convert from [`NnfExpr`](NnfExpr) into [`CnfExpr`](CnfExpr) by distributing ORs.
    ///
    /// ```rust
    /// # use aris::expr::NnfExpr;
    /// # use aris::expr::CnfExpr;
    /// assert_eq!(NnfExpr::var("A").into_cnf(), CnfExpr::var("A"));
    /// ```
    pub fn into_cnf(self) -> CnfExpr {
        // Make an iterator over the CNF conversions of NNF expressions
        fn map_cnf(exprs: Vec<NnfExpr>) -> impl Iterator<Item = CnfExpr> {
            exprs.into_iter().map(NnfExpr::into_cnf)
        }

        match self {
            NnfExpr::Lit { polarity, name } => CnfExpr::literal(polarity, name),
            NnfExpr::And { exprs } => CnfExpr::and(map_cnf(exprs)),
            NnfExpr::Or { exprs } => CnfExpr::or(map_cnf(exprs)),
        }
    }
}

impl Not for NnfExpr {
    type Output = Self;

    /// Create an NNF expression by applying logical NOT to an NNF expression.
    ///
    /// ```rust
    /// use aris::parser::parse_unwrap as p;
    /// # use aris::expr::NnfExpr;
    ///
    /// // Using `!` operator
    /// let a = NnfExpr::var("A");
    /// assert_eq!(p("~A").into_nnf(), Some(!a));
    ///
    /// // Using explicit `Not` trait method call
    /// let b = NnfExpr::var("B");
    /// use std::ops::Not;
    /// assert_eq!(p("~B").into_nnf(), Some(b.not()));
    /// ```
    fn not(self) -> Self {
        let map_not = |exprs: Vec<Self>| exprs.into_iter().map(Self::not).collect();
        match self {
            NnfExpr::Lit { polarity, name } => NnfExpr::Lit {
                polarity: !polarity,
                name,
            },
            NnfExpr::And { exprs } => NnfExpr::Or {
                exprs: map_not(exprs),
            },
            NnfExpr::Or { exprs } => NnfExpr::And {
                exprs: map_not(exprs),
            },
        }
    }
}

impl CnfExpr {
    /// Create a true (tautology) CNF expression.
    ///
    /// ```rust
    /// use aris::parser::parse_unwrap as p;
    /// # use aris::expr::CnfExpr;
    ///
    /// assert_eq!(p("⊤").into_cnf(), Some(CnfExpr::taut()));
    /// ```
    pub fn taut() -> Self {
        // An empty AND
        // AND() ≡ ⊤
        CnfExpr(vec![])
    }

    /// Create a false (contradiction) CNF expression.
    ///
    /// ```rust
    /// use aris::parser::parse_unwrap as p;
    /// # use aris::expr::CnfExpr;
    ///
    /// assert_eq!(p("⊥").into_cnf(), Some(CnfExpr::contra()));
    /// ```
    pub fn contra() -> Self {
        // An AND with an empty OR inside
        // AND(OR()) ≡ OR() ≡ ⊥
        CnfExpr(vec![vec![]])
    }

    /// Create a CNF expression from a literal (a variable name and its
    /// polarity).
    ///
    /// ```rust
    /// use aris::parser::parse_unwrap as p;
    /// # use aris::expr::CnfExpr;
    ///
    /// assert_eq!(p("A").into_cnf().unwrap(), CnfExpr::literal(true, "A"));
    /// assert_eq!(p("~A").into_cnf().unwrap(), CnfExpr::literal(false, "A"));
    /// ```
    pub fn literal<S: ToString>(polarity: bool, name: S) -> Self {
        CnfExpr(vec![vec![(polarity, name.to_string())]])
    }

    /// Create a CNF expression from a variable.
    ///
    /// ```rust
    /// use aris::parser::parse_unwrap as p;
    /// # use aris::expr::CnfExpr;
    ///
    /// assert_eq!(p("A").into_cnf().unwrap(), CnfExpr::var("A"));
    /// ```
    pub fn var<S: ToString>(name: S) -> Self {
        Self::literal(true, name)
    }

    /// Create a CNF expression by applying logical AND to many CNF expressions.
    ///
    /// ```rust
    /// use aris::parser::parse_unwrap as p;
    /// # use aris::expr::CnfExpr;
    ///
    /// let a = CnfExpr::var("A");
    /// let b = CnfExpr::var("B");
    ///
    /// assert_eq!(p("A & B").into_cnf(), Some(CnfExpr::and(vec![a, b])));
    /// ```
    pub fn and<I>(exprs: I) -> Self
    where
        I: IntoIterator<Item = CnfExpr>,
    {
        CnfExpr(exprs.into_iter().flat_map(|expr| expr.0).collect())
    }

    /// Create a CNF expression by applying logical OR to many CNF expressions.
    ///
    /// ```rust
    /// use aris::parser::parse_unwrap as p;
    /// # use aris::expr::CnfExpr;
    ///
    /// let a = CnfExpr::var("A");
    /// let b = CnfExpr::var("B");
    ///
    /// assert_eq!(p("A | B").into_cnf(), Some(CnfExpr::or(vec![a, b])));
    /// ```
    pub fn or<I>(exprs: I) -> Self
    where
        I: IntoIterator<Item = CnfExpr>,
    {
        let clauses = exprs
            .into_iter()
            .map(|expr| expr.0)
            .multi_cartesian_product()
            .map(|clauses| clauses.concat())
            .collect::<Vec<Vec<(bool, String)>>>();
        if clauses.is_empty() {
            CnfExpr::contra()
        } else {
            CnfExpr(clauses)
        }
    }

    /// Use CNF expression to create a [`varisat::CnfFormula`][cnfformula]
    /// usable by [`varisat`][varisat]. Additionally, it returns the mapping
    /// between variable names and [`varisat::Var`](varisat::Var) so that the
    /// model returned by `varisat` can be pretty printed.
    ///
    /// ```rust
    /// # use aris::expr::CnfExpr;
    /// let (sat, vars) = CnfExpr::var("A").to_varisat();
    /// ```
    ///
    /// [cnfformula]: varisat::CnfFormula
    /// [varisat]: varisat
    pub fn to_varisat(&self) -> (varisat::CnfFormula, HashMap<varisat::Var, String>) {
        // Get the variables in the expression and make a hash table from the
        // variable name to the corresponding `varisat::Var`.
        let vars = self
            .0
            .iter()
            .flatten()
            .map(|(_, name)| name.to_string())
            .collect::<BTreeSet<String>>() // Sort and remove duplicates
            .into_iter()
            .enumerate()
            .map(|(index, name)| (name, varisat::Var::from_index(index)))
            .collect::<HashMap<String, varisat::Var>>();

        // Use the `vars` hash table above to convert the expression into an
        // iterator over clauses, where each clause is a `Vec<varisat::Lit>`.
        // Basically, this converts each `(bool, String)` in the `CnfExpr` to
        // `varisat::Lit`.
        let clauses = self.0.iter().map(|clause| {
            clause
                .iter()
                .map(|(is_pos, name)| varisat::Lit::from_var(vars[name], *is_pos))
                .collect::<Vec<varisat::Lit>>()
        });

        let sat = varisat::CnfFormula::from(clauses);

        // Reverse order of `HashMap`. Convert `HashMap<String, varisat::Var>`
        // to `HashMap<varisat::Var, String>`.
        let vars = vars
            .into_iter()
            .map(|(name, var)| (var, name))
            .collect::<HashMap<varisat::Var, String>>();

        (sat, vars)
    }
}

pub fn expressions_for_depth(
    depth: usize,
    max_assoc: usize,
    mut vars: BTreeSet<String>,
) -> BTreeSet<Expr> {
    let mut ret = BTreeSet::new();
    if depth == 0 {
        ret.insert(Expr::Contra);
        ret.insert(Expr::Taut);
        ret.extend(vars.iter().cloned().map(|name| Expr::Var { name }));
    } else {
        let smaller: Vec<_> = expressions_for_depth(depth - 1, max_assoc, vars.clone())
            .into_iter()
            .collect();
        let mut products = vec![];
        for i in 2..=max_assoc {
            products.extend((0..i).map(|_| smaller.clone()).multi_cartesian_product());
        }
        for v in vars.iter() {
            for arglist in products.iter() {
                ret.insert(Expr::apply(Expr::var(v), arglist));
            }
        }
        for e in smaller.iter() {
            ret.insert(Expr::not(e.clone()));
        }
        for lhs in smaller.iter() {
            for rhs in smaller.iter() {
                ret.insert(Expr::implies(lhs.clone(), rhs.clone()));
            }
        }
        for op in &[Op::And, Op::Or, Op::Bicon, Op::Equiv, Op::Add, Op::Mult] {
            for arglist in products.iter() {
                ret.insert(Expr::assoc(*op, &arglist));
            }
        }
        let x = format!("x{}", depth);
        vars.insert(x.clone());
        for body in expressions_for_depth(depth - 1, max_assoc, vars).into_iter() {
            ret.insert(Expr::forall(&x, body.clone()));
            ret.insert(Expr::exists(&x, body.clone()));
        }
    }
    ret
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_gen_var() {
        assert_eq!(gen_var("A", &hashset![]), "A");
        assert_eq!(
            gen_var(
                "A",
                &hashset!["B".to_owned(), "C".to_owned(), "D".to_owned()]
            ),
            "A"
        );
        assert_eq!(
            gen_var(
                "A",
                &hashset![
                    "A".to_owned(),
                    "B".to_owned(),
                    "C".to_owned(),
                    "D".to_owned()
                ]
            ),
            "A0"
        );
        assert_eq!(
            gen_var(
                "A",
                &hashset![
                    "A".to_owned(),
                    "A0".to_owned(),
                    "A1".to_owned(),
                    "A2".to_owned(),
                    "A3".to_owned()
                ]
            ),
            "A4"
        );
    }

    #[test]
    fn test_subst() {
        use crate::parser::parse_unwrap as p;
        assert_eq!(
            subst(p("x & forall x, x"), "x", p("y")),
            p("y & forall x, x")
        ); // hit (true, _) case in Expr::Quantifier
        assert_eq!(
            subst(p("forall x, x & y"), "y", p("x")),
            p("forall x0, x0 & x")
        ); // hit (false, true) case in Expr::Quantifier
        assert_eq!(
            subst(p("forall x, x & y"), "y", p("z")),
            p("forall x, x & z")
        ); // hit (false, false) case in Expr::Quantifier
        assert_eq!(
            subst(p("forall f, f(x) & g(y, z)"), "g", p("h")),
            p("forall f, f(x) & h(y, z)")
        );
        assert_eq!(
            subst(p("forall f, f(x) & g(y, z)"), "g", p("f")),
            p("forall f0, f0(x) & f(y, z)")
        );
    }

    #[test]
    fn test_unify() {
        use crate::parser::parse_unwrap as p;
        let u = |s, t| {
            let left = p(s);
            let right = p(t);
            let ret = unify(
                vec![Constraint::Equal(left.clone(), right.clone())]
                .into_iter()
                .collect(),
            );
            if let Some(ref ret) = ret {
                let subst_l = ret.apply(left.clone());
                let subst_r = ret.apply(right.clone());
                // TODO: assert alpha_equal(subst_l, subst_r);
                println!("{} {} {:?} {} {}", left, right, ret, subst_l, subst_r);
            }
            ret
        };
        println!("{:?}", u("x", "forall y, y"));
        println!("{:?}", u("forall y, y", "y"));
        println!("{:?}", u("x", "x"));
        assert_eq!(u("forall x, x", "forall y, y"), Some(Substitution(vec![]))); // should be equal with no substitution since unification is modulo alpha equivalence
        println!("{:?}", u("f(x,y,z)", "g(x,y,y)"));
        println!("{:?}", u("g(x,y,y)", "f(x,y,z)"));
        println!(
            "{:?}",
            u(
                "forall foo, foo(x,y,z) & bar",
                "forall bar, bar(x,y,z) & baz"
            )
        );

        assert_eq!(u("forall x, z", "forall y, y"), None);
        assert_eq!(u("x & y", "x | y"), None);
    }

    #[test]
    pub fn test_combine_associative_ops() {
        use crate::parser::parse_unwrap as p;
        let f = |s: &str| {
            let e = p(s);
            println!(
                "association of {} is {}",
                e,
                e.clone().combine_associative_ops()
            );
        };
        f("a & (b & (c | (p -> (q <-> (r <-> s)))) & ((t === u) === (v === ((w | x) | y))))");
        f("a & ((b & c) | (q | r))");
        f("(a & (b & c)) | (q | r)");
    }

    #[test]
    fn test_expressions_for_depth() {
        use std::iter::FromIterator;

        let vars = BTreeSet::from_iter(vec!["a".into()]);
        for depth in 0..3 {
            let set = expressions_for_depth(depth, 2, vars.clone());
            println!("Depth: {}", depth);
            for expr in set {
                println!("\t{}", expr);
            }
        }
    }
}
