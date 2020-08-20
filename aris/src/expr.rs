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

`Expr` is an enum, and can be inspected with rust's `match` construct:

```
use aris::parser::parse_unwrap as p;
use aris::expr::*;

fn is_it_an_and(e: &Expr) -> bool {
    match e {
        Expr::AssocBinop { symbol: ASymbol::And, .. } => true,
        _ => false,
    }
}

let expr1 = p("a & b");
let expr2 = p("a | (b & c)");
let expr3 = p("forall a, exists b, forall c, exists d, a -> (b | c | ~d)");

assert_eq!(is_it_an_and(&expr1), true);
assert_eq!(is_it_an_and(&expr2), false);
assert_eq!(is_it_an_and(&expr3), false);

fn does_it_have_any_ands(e: &Expr) -> bool {
    use aris::expr::Expr::*;
    match e {
        Contradiction | Tautology | Var { .. } => false,
        Apply { func, args } => does_it_have_any_ands(&func) || args.iter().any(|arg| does_it_have_any_ands(arg)),
        Unop { symbol: _, operand } => does_it_have_any_ands(&operand),
        Binop { symbol: _, left, right } => does_it_have_any_ands(&left) || does_it_have_any_ands(&right),
        AssocBinop { symbol: ASymbol::And, .. } => true,
        AssocBinop { symbol: _, exprs } => exprs.iter().any(|expr| does_it_have_any_ands(expr)),
        Quantifier { symbol: _, name: _, body } => does_it_have_any_ands(&body),
    }
}

assert_eq!(does_it_have_any_ands(&expr1), true);
assert_eq!(does_it_have_any_ands(&expr2), true);
assert_eq!(does_it_have_any_ands(&expr3), false);
```
*/

use std::collections::BTreeSet;
use std::collections::{HashMap, HashSet};
use std::mem;
use std::ops::Not;

use itertools::Itertools;
use serde::Deserialize;
use serde::Serialize;

/// Symbol for unary operations
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash, Ord, PartialOrd, Serialize, Deserialize)]
#[repr(C)]
pub enum USymbol {
    Not,
}
/// Symbol for binary operations
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash, Ord, PartialOrd, Serialize, Deserialize)]
#[repr(C)]
pub enum BSymbol {
    Implies,
    Plus,
    Mult,
}
/// Symbol for associative binary operations
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash, Ord, PartialOrd, Serialize, Deserialize)]
#[repr(C)]
pub enum ASymbol {
    And,
    Or,
    Bicon,
    Equiv,
}
/// Symbol for quantifiers
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash, Ord, PartialOrd, Serialize, Deserialize)]
#[repr(C)]
pub enum QSymbol {
    Forall,
    Exists,
}

/// aris::expr::Expr is the core AST (Abstract Syntax Tree) type for representing logical expressions.
/// For most of the recursive cases, it uses symbols so that code can work on the shape of e.g. a binary operation without worrying about which binary operation it is.
#[derive(Clone, Debug, PartialEq, Eq, Hash, Ord, PartialOrd, Serialize, Deserialize)]
#[repr(C)]
pub enum Expr {
    Contradiction,
    Tautology,
    Var {
        name: String,
    },
    Apply {
        func: Box<Expr>,
        args: Vec<Expr>,
    },
    Unop {
        symbol: USymbol,
        operand: Box<Expr>,
    },
    Binop {
        symbol: BSymbol,
        left: Box<Expr>,
        right: Box<Expr>,
    },
    AssocBinop {
        symbol: ASymbol,
        exprs: Vec<Expr>,
    },
    Quantifier {
        symbol: QSymbol,
        name: String,
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
    Or { exprs: Vec<NnfExpr> },
    /// Sub-expressions AND'ed together
    And { exprs: Vec<NnfExpr> },
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
/// assert_eq!(Expr::Tautology.into_cnf(), Some(CnfExpr::tautology()));
/// ```
///
/// [cnf]: https://en.wikipedia.org/wiki/Conjunctive_normal_form
#[derive(Clone, Debug, PartialEq, Eq, Hash, Ord, PartialOrd)]
pub struct CnfExpr(Vec<Vec<(bool, String)>>);

impl std::fmt::Display for USymbol {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        match self {
            USymbol::Not => write!(f, "¬"),
        }
    }
}

impl std::fmt::Display for BSymbol {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        match self {
            BSymbol::Implies => write!(f, "→"),
            BSymbol::Plus => write!(f, "+"),
            BSymbol::Mult => write!(f, "*"),
        }
    }
}

impl std::fmt::Display for ASymbol {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        match self {
            ASymbol::And => write!(f, "∧"),
            ASymbol::Or => write!(f, "∨"),
            ASymbol::Bicon => write!(f, "↔"),
            ASymbol::Equiv => write!(f, "≡"),
        }
    }
}

impl std::fmt::Display for QSymbol {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        match self {
            QSymbol::Forall => write!(f, "∀"),
            QSymbol::Exists => write!(f, "∃"),
        }
    }
}

fn assoc_display_helper<S, E>(
    f: &mut std::fmt::Formatter,
    symbol: S,
    exprs: &[E],
) -> std::fmt::Result
where
    S: std::fmt::Display,
    E: std::fmt::Display,
{
    let s = exprs
        .iter()
        .map(E::to_string)
        .collect::<Vec<_>>()
        .join(&format!(" {} ", symbol));
    write!(f, "({})", s)
}

impl std::fmt::Display for Expr {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        use Expr::*;
        match self {
            Contradiction => write!(f, "⊥"),
            Tautology => write!(f, "⊤"),
            Var { name } => write!(f, "{}", name),
            Apply { func, args } => {
                write!(f, "{}", func)?;
                if !args.is_empty() {
                    write!(
                        f,
                        "({})",
                        args.iter()
                            .map(|x| format!("{}", x))
                            .collect::<Vec<_>>()
                            .join(", ")
                    )?
                };
                Ok(())
            }
            Unop { symbol, operand } => write!(f, "{}{}", symbol, operand),
            Binop {
                symbol,
                left,
                right,
            } => write!(f, "({} {} {})", left, symbol, right),
            AssocBinop { symbol, exprs } => assoc_display_helper(f, symbol, exprs),
            Quantifier { symbol, name, body } => write!(f, "({} {}, {})", symbol, name, body),
        }
    }
}

impl std::fmt::Display for NnfExpr {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
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

pub trait PossiblyCommutative {
    fn is_commutative(&self) -> bool;
}

impl PossiblyCommutative for BSymbol {
    fn is_commutative(&self) -> bool {
        use BSymbol::*;
        match self {
            Implies => false,
            Plus | Mult => true,
        }
    }
}

impl PossiblyCommutative for ASymbol {
    fn is_commutative(&self) -> bool {
        use ASymbol::*;
        match self {
            // currently, all the implemented associative connectives are also commutative, but that's not true in general, so this is future-proofing
            And | Or | Bicon | Equiv => true,
        }
    }
}

/// Calculates the set of variables that occur free in some logical expression
pub fn freevars(e: &Expr) -> HashSet<String> {
    let mut r = HashSet::new();
    match e {
        Expr::Contradiction => (),
        Expr::Tautology => (),
        Expr::Var { name } => {
            r.insert(name.clone());
        }
        Expr::Apply { func, args } => {
            r.extend(freevars(func));
            for s in args.iter().map(|x| freevars(x)) {
                r.extend(s);
            }
        }
        Expr::Unop { operand, .. } => {
            r.extend(freevars(operand));
        }
        Expr::Binop { left, right, .. } => {
            r.extend(freevars(left));
            r.extend(freevars(right));
        }
        Expr::AssocBinop { exprs, .. } => {
            for expr in exprs.iter() {
                r.extend(freevars(expr));
            }
        }
        Expr::Quantifier { name, body, .. } => {
            r.extend(freevars(body));
            r.remove(name);
        }
    }
    r
}

/// Generate a fresh symbol/variable name, using "orig" as a prefix, and avoiding collisions with the specified set
pub fn gensym(orig: &str, avoid: &HashSet<String>) -> String {
    for i in 0u64.. {
        let ret = format!("{}{}", orig, i);
        if !avoid.contains(&ret[..]) {
            return ret;
        }
    }
    panic!("Somehow gensym used more than 2^64 ids without finding anything?")
}

/// `subst(e, to_replace, with)` performs capture-avoiding substitution of free variables named `to_replace` with `with` in `e`
pub fn subst(e: &Expr, to_replace: &str, with: Expr) -> Expr {
    match e {
        Expr::Contradiction => Expr::Contradiction,
        Expr::Tautology => Expr::Tautology,
        Expr::Var { ref name } => {
            if name == to_replace {
                with
            } else {
                Expr::Var { name: name.clone() }
            }
        }
        Expr::Apply { ref func, ref args } => Expr::Apply {
            func: Box::new(subst(func, to_replace, with.clone())),
            args: args
                .iter()
                .map(|e2| subst(e2, to_replace, with.clone()))
                .collect(),
        },
        Expr::Unop { symbol, operand } => Expr::Unop {
            symbol: *symbol,
            operand: Box::new(subst(operand, to_replace, with)),
        },
        Expr::Binop {
            symbol,
            left,
            right,
        } => Expr::Binop {
            symbol: *symbol,
            left: Box::new(subst(left, to_replace, with.clone())),
            right: Box::new(subst(right, to_replace, with)),
        },
        Expr::AssocBinop { symbol, exprs } => Expr::AssocBinop {
            symbol: *symbol,
            exprs: exprs
                .iter()
                .map(|e2| subst(e2, to_replace, with.clone()))
                .collect(),
        },
        Expr::Quantifier { symbol, name, body } => {
            let fv_with = freevars(&with);
            let (newname, newbody) = match (name == to_replace, fv_with.contains(name)) {
                (true, _) => (name.clone(), *body.clone()),
                (false, true) => {
                    let newname = gensym(name, &fv_with);
                    let body0 = subst(body, name, Expr::var(&newname[..]));
                    let body1 = subst(&body0, to_replace, with);
                    //println!("{:?}\n{:?}\n{:?}", body, body0, body1);
                    (newname, body1)
                }
                (false, false) => (name.clone(), subst(body, to_replace, with)),
            };
            Expr::Quantifier {
                symbol: *symbol,
                name: newname,
                body: Box::new(newbody),
            }
        }
    }
}

#[test]
fn test_subst() {
    use crate::parser::parse_unwrap as p;
    assert_eq!(
        subst(&p("x & forall x, x"), "x", p("y")),
        p("y & forall x, x")
    ); // hit (true, _) case in Quantifier
    assert_eq!(
        subst(&p("forall x, x & y"), "y", p("x")),
        p("forall x0, x0 & x")
    ); // hit (false, true) case in Quantifier
    assert_eq!(
        subst(&p("forall x, x & y"), "y", p("z")),
        p("forall x, x & z")
    ); // hit (false, false) case in Quantifier
    assert_eq!(
        subst(&p("forall f, f(x) & g(y, z)"), "g", p("h")),
        p("forall f, f(x) & h(y, z)")
    );
    assert_eq!(
        subst(&p("forall f, f(x) & g(y, z)"), "g", p("f")),
        p("forall f0, f0(x) & f(y, z)")
    );
}

#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub struct Equal {
    pub left: Expr,
    pub right: Expr,
}

#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub struct Substitution<A, B>(pub Vec<(A, B)>);
/// Unifies a set of equality constraints on expressions, giving a list of substitutions that make constrained expressions equal.
/// a == b -> unify(HashSet::from_iter(vec![Equal(a, b)])) == Some(vec![])
pub fn unify(mut c: HashSet<Equal>) -> Option<Substitution<String, Expr>> {
    // inspired by TAPL 22.4
    //println!("\t{:?}", c);
    let mut c_ = c.clone();
    let Equal { left, right } = if let Some(x) = c_.drain().next() {
        c.remove(&x);
        x
    } else {
        return Some(Substitution(vec![]));
    };
    use Expr::*;
    let subst_set = |x, e1: Expr, set: HashSet<_>| {
        set.into_iter()
            .map(
                |Equal {
                     left: e2,
                     right: e3,
                 }| Equal {
                    left: subst(&e2, x, e1.clone()),
                    right: subst(&e3, x, e1.clone()),
                },
            )
            .collect::<_>()
    };
    let (fvs, fvt) = (freevars(&left), freevars(&right));
    match (&left, &right) {
        (_, _) if left == right => unify(c),
        (Var { name: ref sname }, _) if !fvt.contains(sname) => {
            unify(subst_set(&sname, right.clone(), c)).map(|mut x| {
                x.0.push((sname.clone(), right.clone()));
                x
            })
        }
        (_, Var { name: ref tname }) if !fvs.contains(tname) => {
            unify(subst_set(&tname, left.clone(), c)).map(|mut x| {
                x.0.push((tname.clone(), left.clone()));
                x
            })
        }
        (
            Unop {
                symbol: ss,
                operand: so,
            },
            Unop {
                symbol: ts,
                operand: to,
            },
        ) if ss == ts => {
            c.insert(Equal {
                left: *so.clone(),
                right: *to.clone(),
            });
            unify(c)
        }
        (
            Binop {
                symbol: ss,
                left: sl,
                right: sr,
            },
            Binop {
                symbol: ts,
                left: tl,
                right: tr,
            },
        ) if ss == ts => {
            c.insert(Equal {
                left: *sl.clone(),
                right: *tl.clone(),
            });
            c.insert(Equal {
                left: *sr.clone(),
                right: *tr.clone(),
            });
            unify(c)
        }
        (Apply { func: sf, args: sa }, Apply { func: tf, args: ta }) if sa.len() == ta.len() => {
            c.insert(Equal {
                left: *sf.clone(),
                right: *tf.clone(),
            });
            c.extend(sa.iter().zip(ta.iter()).map(|(x, y)| Equal {
                left: x.clone(),
                right: y.clone(),
            }));
            unify(c)
        }
        (
            AssocBinop {
                symbol: ss,
                exprs: se,
            },
            AssocBinop {
                symbol: ts,
                exprs: te,
            },
        ) if ss == ts && se.len() == te.len() => {
            c.extend(se.iter().zip(te.iter()).map(|(x, y)| Equal {
                left: x.clone(),
                right: y.clone(),
            }));
            unify(c)
        }
        (
            Quantifier {
                symbol: ss,
                name: sn,
                body: sb,
            },
            Quantifier {
                symbol: ts,
                name: tn,
                body: tb,
            },
        ) if ss == ts => {
            let uv = gensym("__unification_var", &fvs.union(&fvt).cloned().collect());
            // require that the bodies of the quantifiers are alpha-equal by substituting a fresh constant
            c.insert(Equal {
                left: subst(sb, sn, Expr::var(&uv)),
                right: subst(tb, tn, Expr::var(&uv)),
            });
            // if the constant escapes, then a free variable in one formula unified with a captured variable in the other, so the values don't unify
            unify(c).and_then(|sub| {
                if sub
                    .0
                    .iter()
                    .any(|(x, y)| x == &uv || freevars(y).contains(&uv))
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

#[test]
fn test_unify() {
    use crate::parser::parse_unwrap as p;
    let u = |s, t| {
        let left = p(s);
        let right = p(t);
        let ret = unify(
            vec![Equal {
                left: left.clone(),
                right: right.clone(),
            }]
            .into_iter()
            .collect(),
        );
        if let Some(ref ret) = ret {
            let subst_l = ret
                .0
                .iter()
                .fold(left.clone(), |z, (x, y)| subst(&z, x, y.clone()));
            let subst_r = ret
                .0
                .iter()
                .fold(right.clone(), |z, (x, y)| subst(&z, x, y.clone()));
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

/*
Note apply_non_literal

In order to make substitution not special case predicate/function application, Expr::Apply's func field is a general Box<Expr> instead of a String.
This also makes sense for eventually supporting lambda expressions using Quantifier nodes.
Currently, the parser will never produce Expr::Apply nodes that have a func that is not an Expr::Var, and some code depends on this to avoid handling a more difficult general case.
*/

impl Expr {
    pub fn var(name: &str) -> Expr {
        Expr::Var { name: name.into() }
    }
    pub fn apply(func: Expr, args: &[Expr]) -> Expr {
        Expr::Apply {
            func: Box::new(func),
            args: args.to_vec(),
        }
    }
    pub fn not(expr: Expr) -> Expr {
        Expr::Unop {
            symbol: USymbol::Not,
            operand: Box::new(expr),
        }
    }
    pub fn binop(symbol: BSymbol, l: Expr, r: Expr) -> Expr {
        Expr::Binop {
            symbol,
            left: Box::new(l),
            right: Box::new(r),
        }
    }
    pub fn binopplaceholder(symbol: BSymbol) -> Expr {
        Expr::binop(symbol, Expr::var("_"), Expr::var("_"))
    }
    pub fn implies(l: Expr, r: Expr) -> Expr {
        Expr::binop(BSymbol::Implies, l, r)
    }
    pub fn or(l: Expr, r: Expr) -> Expr {
        Expr::assocbinop(ASymbol::Or, &[l, r])
    }
    pub fn assocbinop(symbol: ASymbol, exprs: &[Expr]) -> Expr {
        Expr::AssocBinop {
            symbol,
            exprs: exprs.to_vec(),
        }
    }
    pub fn assocplaceholder(symbol: ASymbol) -> Expr {
        Expr::assocbinop(symbol, &[Expr::var("_"), Expr::var("_"), Expr::var("...")])
    }
    pub fn quantifierplaceholder(symbol: QSymbol) -> Expr {
        Expr::Quantifier {
            symbol,
            name: "_".into(),
            body: Box::new(Expr::var("_")),
        }
    }
    pub fn forall(name: &str, body: Expr) -> Expr {
        Expr::Quantifier {
            symbol: QSymbol::Forall,
            name: name.into(),
            body: Box::new(body),
        }
    }
    pub fn exists(name: &str, body: Expr) -> Expr {
        Expr::Quantifier {
            symbol: QSymbol::Exists,
            name: name.into(),
            body: Box::new(body),
        }
    }
    pub fn infer_arities(&self, arities: &mut HashMap<String, usize>) {
        use Expr::*;
        match self {
            Contradiction | Tautology => {}
            Var { name } => {
                arities.entry(name.clone()).or_insert(0);
            }
            Apply { func, args } => match &**func {
                Var { name } => {
                    let arity = arities.entry(name.clone()).or_insert_with(|| args.len());
                    *arity = args.len().max(*arity);
                    for arg in args {
                        arg.infer_arities(arities);
                    }
                }
                _ => panic!("See note apply_non_literal"),
            },
            Unop { symbol: _, operand } => {
                operand.infer_arities(arities);
            }
            Binop {
                symbol: _,
                left,
                right,
            } => {
                left.infer_arities(arities);
                right.infer_arities(arities)
            }
            AssocBinop { symbol: _, exprs } => {
                for e in exprs {
                    e.infer_arities(arities);
                }
            }
            Quantifier {
                symbol: _,
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
        use Expr::*;
        match self {
            Contradiction => false,
            Tautology => true,
            Var { name } => env[name][0], // variables are 0-arity functions
            Apply { func, args } => match &**func {
                Var { name } => {
                    let evaled_args: Vec<bool> = args.iter().map(|arg| arg.eval(env)).collect();
                    let mut index: usize = 0;
                    for (i, x) in evaled_args.into_iter().enumerate() {
                        index |= (x as usize) << i;
                    }
                    env[&*name][index]
                }
                _ => panic!("See note apply_non_literal"),
            },
            Unop { symbol, operand } => {
                use USymbol::*;
                match symbol {
                    Not => !operand.eval(env),
                }
            }
            Binop {
                symbol,
                left,
                right,
            } => {
                use BSymbol::*;
                let (x, y) = (left.eval(env), right.eval(env));
                match symbol {
                    Implies => !x || y,
                    Plus | Mult => panic!("Expr::eval does not support arithmetic"),
                }
            }
            AssocBinop { symbol, exprs } => {
                use ASymbol::*;
                let (mut ret, f): (bool, &dyn Fn(bool, bool) -> bool) = match symbol {
                    And => (true, &|x, y| x && y),
                    Or => (false, &|x, y| x || y),
                    Bicon => (true, &|x, y| x == y),
                    Equiv => unimplemented!(),
                };
                for b in exprs.iter().map(|e| e.eval(env)) {
                    ret = f(ret, b);
                }
                ret
            }
            Quantifier { .. } => panic!("Expr::eval does not support quantifiers"),
        }
    }
    /// Sort all commutative associative operators to normalize expressions in the case of arbitrary ordering
    /// Eg (B & A) ==> (A & B)
    pub fn sort_commutative_ops(self) -> Expr {
        use Expr::*;

        self.transform(&|e| match e {
            Binop {
                symbol,
                left,
                right,
            } => {
                if symbol.is_commutative() {
                    let (left, right) = if left <= right {
                        (left, right)
                    } else {
                        (right, left)
                    };
                    (
                        Binop {
                            symbol,
                            left,
                            right,
                        },
                        true,
                    )
                } else {
                    (
                        Binop {
                            symbol,
                            left,
                            right,
                        },
                        false,
                    )
                }
            }
            AssocBinop { symbol, mut exprs } => {
                let is_sorted = exprs.windows(2).all(|xy| xy[0] <= xy[1]);
                if symbol.is_commutative() && !is_sorted {
                    exprs.sort();
                    (AssocBinop { symbol, exprs }, true)
                } else {
                    (AssocBinop { symbol, exprs }, false)
                }
            }
            _ => (e, false),
        })
    }

    /// Combine associative operators such that nesting is flattened
    /// Eg (A & (B & C)) ==> (A & B & C)
    pub fn combine_associative_ops(self) -> Expr {
        use Expr::*;

        self.transform(&|e| match e {
            AssocBinop {
                symbol: symbol1,
                exprs: exprs1,
            } => {
                let mut result = vec![];
                let mut combined = false;
                for expr in exprs1 {
                    if let AssocBinop {
                        symbol: symbol2,
                        exprs: exprs2,
                    } = expr
                    {
                        if symbol1 == symbol2 {
                            result.extend(exprs2);
                            combined = true;
                        } else {
                            result.push(AssocBinop {
                                symbol: symbol2,
                                exprs: exprs2,
                            });
                        }
                    } else {
                        result.push(expr);
                    }
                }
                (
                    AssocBinop {
                        symbol: symbol1,
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
        use Expr::*;

        let (result, status) = trans(expr);
        let (result, status2) = match result {
            // Base cases: these just got transformed above so no need to recurse them
            e @ Contradiction => (e, false),
            e @ Tautology => (e, false),
            e @ Var { .. } => (e, false),

            // Recursive cases: transform each of the sub-expressions of the various compound expressions
            // and then construct a new instance of that compound expression with their transformed results.
            // If any transformation is successful, we return success
            Apply { func, args } => {
                let (func, fs) = Self::transform_expr_inner(*func, trans);
                let func = Box::new(func);
                // Fancy iterator hackery to transform each sub expr and then collect all their results
                let (args, stats): (Vec<_>, Vec<_>) = args
                    .into_iter()
                    .map(move |expr| Self::transform_expr_inner(expr, trans))
                    .unzip();
                let success = fs || stats.into_iter().any(|x| x);
                (Apply { func, args }, success)
            }
            Unop { symbol, operand } => {
                let (operand, success) = Self::transform_expr_inner(*operand, trans);
                let operand = Box::new(operand);
                (Unop { symbol, operand }, success)
            }
            Binop {
                symbol,
                left,
                right,
            } => {
                let (left, ls) = Self::transform_expr_inner(*left, trans);
                let (right, rs) = Self::transform_expr_inner(*right, trans);
                let left = Box::new(left);
                let right = Box::new(right);
                let success = ls || rs;
                (
                    Binop {
                        symbol,
                        left,
                        right,
                    },
                    success,
                )
            }
            AssocBinop { symbol, exprs } => {
                let (exprs, stats): (Vec<_>, Vec<_>) = exprs
                    .into_iter()
                    .map(move |expr| Self::transform_expr_inner(expr, trans))
                    .unzip();
                let success = stats.into_iter().any(|x| x);
                (AssocBinop { symbol, exprs }, success)
            }
            Quantifier { symbol, name, body } => {
                let (body, success) = Self::transform_expr_inner(*body, trans);
                let body = Box::new(body);
                (Quantifier { symbol, name, body }, success)
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
        use Expr::*;

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
                Contradiction => {}
                Tautology => {}
                Var { .. } => {}

                // Add the Cartesian product of the set of `func`
                // transformations and the sets of transformations of `args`
                Apply { func, args } => {
                    let func_set = func.transform_set(trans_fn).into_iter().map(Box::new);
                    let args_set = args
                        .into_iter()
                        .map(|arg| arg.transform_set_vec(trans_fn))
                        .multi_cartesian_product();
                    set.extend(
                        func_set
                            .cartesian_product(args_set)
                            .map(|(func, args)| Apply { func, args }),
                    );
                }

                // Add the set of transformations of `operand`
                Unop { symbol, operand } => {
                    set.extend(
                        operand
                            .transform_set(trans_fn)
                            .into_iter()
                            .map(Box::new)
                            .map(move |operand| Unop { symbol, operand }),
                    );
                }

                // Add the Cartesian product of the transformation sets of the
                // `left` and `right` sub-nodes
                Binop {
                    symbol,
                    left,
                    right,
                } => {
                    let left_set = left.transform_set(trans_fn).into_iter().map(Box::new);
                    let right_set = right.transform_set_vec(trans_fn).into_iter().map(Box::new);
                    let binop_set =
                        left_set
                            .cartesian_product(right_set)
                            .map(|(left, right)| Binop {
                                symbol,
                                left,
                                right,
                            });
                    set.extend(binop_set);
                }

                // Add the Cartesian product of the transformation sets of the
                // sub-nodes in `exprs`
                AssocBinop { symbol, exprs } => {
                    set.extend(
                        exprs
                            .into_iter()
                            .map(|expr| expr.transform_set_vec(trans_fn))
                            .multi_cartesian_product()
                            .map(|exprs| AssocBinop { symbol, exprs }),
                    );
                }

                // Add the set of transformations of `body`
                Quantifier { symbol, name, body } => {
                    let body_set = body.transform_set(trans_fn).into_iter().map(Box::new);
                    let quant_set = body_set.map(|body| Quantifier {
                        symbol,
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
    /// Strategy: Apply this to all ~(A ^ B) constructions
    /// This should leave us with an expression in "DeMorgans'd normal form"
    /// With no ~(A ^ B) / ~(A v B) expressions
    pub fn normalize_demorgans(self) -> Expr {
        use Expr::*;

        self.transform(&|expr| {
            let demorgans = |new_symbol, exprs: Vec<Expr>| AssocBinop {
                symbol: new_symbol,
                exprs: exprs
                    .into_iter()
                    .map(|expr| Unop {
                        symbol: USymbol::Not,
                        operand: Box::new(expr),
                    })
                    .collect(),
            };

            match expr {
                Unop {
                    symbol: USymbol::Not,
                    operand,
                } => match *operand {
                    AssocBinop {
                        symbol: ASymbol::And,
                        exprs,
                    } => (demorgans(ASymbol::Or, exprs), true),
                    AssocBinop {
                        symbol: ASymbol::Or,
                        exprs,
                    } => (demorgans(ASymbol::And, exprs), true),
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
        use Expr::*;

        self.transform(&|expr| {
            match expr {
                AssocBinop {
                    symbol: symbol @ ASymbol::And,
                    exprs,
                }
                | AssocBinop {
                    symbol: symbol @ ASymbol::Or,
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
                        (AssocBinop { symbol, exprs }, false)
                    }
                }
                _ => (expr, false),
            }
        })
    }

    /*
    pub fn to_prenex(self) -> Expr {
        use Expr::*; use QSymbol::*;
        match self {
            Contradiction => Contradiction,
            Predicate { .. } => e.clone(),
            Unop { symbol: USymbol::Not, operand } => match to_prenex(&operand) {
                Quantifier { symbol, name, body } => Quantifier { symbol: match symbol { Forall => Exists, Exists => Forall }, name, body: Box::new(expression_builders::not(*body)) },
                e => e
            },
            Binop { symbol: BSymbol::Implies, left, right } => unimplemented!(),
            Binop { symbol: _, left, right } => unimplemented!(),
            AssocBinop { symbol, exprs } => {
                let exprs: Vec<Expr> = exprs.iter().map(to_prenex).collect();
                unimplemented!()
            },
            Quantifier { name, body, .. } => unimplemented!(),
        }
    }
    */
    pub fn disjuncts(&self) -> Vec<Expr> {
        use Expr::*;
        match self {
            Contradiction => vec![],
            AssocBinop {
                symbol: ASymbol::Or,
                exprs,
            } => exprs.clone(),
            _ => vec![self.clone()],
        }
    }
    pub fn from_disjuncts(mut disjuncts: Vec<Expr>) -> Expr {
        use Expr::*;
        match disjuncts.len() {
            0 => Contradiction,
            1 => disjuncts.pop().unwrap(),
            _ => AssocBinop {
                symbol: ASymbol::Or,
                exprs: disjuncts,
            },
        }
    }
    pub fn conjuncts(&self) -> Vec<Expr> {
        use Expr::*;
        match self {
            Tautology => vec![],
            AssocBinop {
                symbol: ASymbol::And,
                exprs,
            } => exprs.clone(),
            _ => vec![self.clone()],
        }
    }
    pub fn from_conjuncts(mut conjuncts: Vec<Expr>) -> Expr {
        use Expr::*;
        match conjuncts.len() {
            0 => Tautology,
            1 => conjuncts.pop().unwrap(),
            _ => AssocBinop {
                symbol: ASymbol::And,
                exprs: conjuncts,
            },
        }
    }
    pub fn negate_quantifiers(self) -> Expr {
        use Expr::*;

        self.transform(&|expr| {
            let gen_opposite = |new_symbol, name, body| Quantifier {
                symbol: new_symbol,
                name,
                body: Box::new(Expr::not(body)),
            };

            match expr {
                Unop {
                    symbol: USymbol::Not,
                    operand,
                } => match *operand {
                    Quantifier { symbol, name, body } => match symbol {
                        QSymbol::Exists => (gen_opposite(QSymbol::Forall, name, *body), true),
                        QSymbol::Forall => (gen_opposite(QSymbol::Exists, name, *body), true),
                    },
                    _ => (Expr::not(*operand), false),
                },
                _ => (expr, false),
            }
        })
    }
    pub fn normalize_null_quantifiers(self) -> Expr {
        use Expr::*;

        self.transform(&|expr| {
            match expr {
                Quantifier { symbol, name, body } => {
                    if freevars(&body).contains(&name) {
                        (Quantifier { symbol, name, body }, false)
                    } else {
                        // if name is not free in body, then the quantifier isn't binding anything and can be removed
                        (*body, true)
                    }
                }
                _ => (expr, false),
            }
        })
    }
    pub fn replacing_bound_vars(self) -> Expr {
        use Expr::*;

        // replaces the letter names with numbers
        fn aux(expr: Expr, mut gamma: Vec<String>) -> Expr {
            match expr {
                Var { name } => {
                    // look up the name in gamma, get the index
                    let i = gamma
                        .into_iter()
                        .enumerate()
                        .find(|(_, n)| n == &name)
                        .unwrap()
                        .0;
                    Var {
                        name: format!("{}", i),
                    }
                }
                // push the name onto gamma from the actual quantifier,
                // Example: for forall x, P(x)
                // push x onto gamma
                // save the length of gamma before recursing, to use as the new name
                Quantifier { symbol, name, body } => {
                    let current_level = format!("{}", gamma.len());
                    gamma.push(name);
                    let new_body = aux(*body, gamma);
                    Quantifier {
                        symbol,
                        name: current_level,
                        body: Box::new(new_body),
                    }
                }
                // All the remainder cases
                Contradiction => Contradiction,
                Tautology => Tautology,
                Apply { func, args } => {
                    let func = aux(*func, gamma.clone());
                    let args = args.into_iter().map(|e| aux(e, gamma.clone())).collect();
                    Apply {
                        func: Box::new(func),
                        args,
                    }
                }
                Unop { symbol, operand } => Unop {
                    symbol,
                    operand: Box::new(aux(*operand, gamma)),
                },
                Binop {
                    symbol,
                    left,
                    right,
                } => {
                    let left = Box::new(aux(*left, gamma.clone()));
                    let right = Box::new(aux(*right, gamma));
                    Binop {
                        symbol,
                        left,
                        right,
                    }
                }
                AssocBinop { symbol, exprs } => {
                    let exprs = exprs.into_iter().map(|e| aux(e, gamma.clone())).collect();
                    AssocBinop { symbol, exprs }
                }
            }
        }

        let mut gamma = vec![];
        gamma.extend(freevars(&self));

        let mut ret = aux(self, gamma.clone());
        // at this point, we've numbered all the vars, including the free ones
        // replace the free vars with their original names
        for (i, name) in gamma.into_iter().enumerate() {
            ret = subst(&ret, &format!("{}", i), Var { name });
        }
        ret
    }
    // check for quantifier,
    // as long as the prefix is the same,
    // keep pushing variables into a set.
    // as soon as you reach a non-quantifier,
    // rewrap all the quantifiers in sorted order
    // if the sorted set is the same as the initial set, return false
    pub fn swap_quantifiers(self) -> Expr {
        use Expr::*;

        self.transform(&|expr| {
            let mut mod_expr = expr.clone();
            let mut stack = vec![];
            let mut last_quantifier = None;

            while let Quantifier { symbol, name, body } = mod_expr {
                if last_quantifier.is_none() || last_quantifier == Some(symbol) {
                    last_quantifier = Some(symbol);
                    stack.push(name);
                    mod_expr = *body;
                } else {
                    mod_expr = Quantifier { symbol, name, body };
                    break;
                }
            }

            stack.sort();

            if let Some(sym) = &last_quantifier {
                for l_name in stack {
                    mod_expr = Quantifier {
                        symbol: *sym,
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
    /// 7c1. forall x, (phi(x) → psi) == (exists x, phi(x)) → psi (! Quantifier changes!)
    /// 7c2. exists x, (phi(x) → psi) == (forall x, phi(x)) → psi (! Quantifier changes!)
    /// 7d1. forall x, (psi → phi(x)) == psi → (forall x, phi(x))
    /// 7d2. exists x, (psi → phi(x)) == psi → (exists x, phi(x))
    pub fn normalize_prenex_laws(self) -> Expr {
        use Expr::*;
        let transform_7ab = |asymbol: ASymbol, exprs: Vec<Expr>| {
            // hoist a forall out of an and/or when the binder won't capture any of the other arms
            // if the binder doesn't occur in `all_free` (the union of all the arms freevars), it won't induce capturing
            let mut all_free = HashSet::new();
            for expr in &exprs {
                all_free.extend(freevars(&expr));
            }
            let mut found = None;
            let mut others = vec![];
            for expr in exprs.into_iter() {
                match expr {
                    Quantifier { symbol, name, body } => {
                        if found.is_none() && !all_free.contains(&name) {
                            found = Some((symbol, name));
                            others.push(*body);
                        } else {
                            others.push(Quantifier { symbol, name, body });
                        }
                    }
                    _ => others.push(expr),
                }
            }
            if let Some((symbol, name)) = found {
                let body = Box::new(AssocBinop {
                    symbol: asymbol,
                    exprs: others,
                });
                (Quantifier { symbol, name, body }, true)
            } else {
                // if none of the subexpressions were quantifiers whose binder was free, `others` should be in the same as `exprs`
                (
                    AssocBinop {
                        symbol: asymbol,
                        exprs: others,
                    },
                    false,
                )
            }
        };
        let reconstruct_7cd = |symbol: QSymbol, name: String, left, right| {
            let body = Box::new(Binop {
                symbol: BSymbol::Implies,
                left,
                right,
            });
            (Quantifier { symbol, name, body }, true)
        };
        self.transform(&|expr| {
            match expr {
                AssocBinop { symbol, exprs } => match symbol {
                    ASymbol::And => transform_7ab(ASymbol::And, exprs),
                    ASymbol::Or => transform_7ab(ASymbol::Or, exprs),
                    _ => (AssocBinop { symbol, exprs }, false),
                },
                Binop {
                    symbol: BSymbol::Implies,
                    mut left,
                    mut right,
                } => {
                    let left_free = freevars(&left);
                    let right_free = freevars(&right);
                    left = match *left {
                        Quantifier { symbol, name, body } if !right_free.contains(&name) => {
                            // 7c case, quantifier is flipped
                            match symbol {
                                QSymbol::Forall => {
                                    return reconstruct_7cd(QSymbol::Exists, name, body, right);
                                }
                                QSymbol::Exists => {
                                    return reconstruct_7cd(QSymbol::Forall, name, body, right);
                                }
                            }
                        }
                        left => Box::new(left),
                    };
                    right = match *right {
                        Quantifier { symbol, name, body } if !left_free.contains(&name) => {
                            // 7d case, quantifier is not flipped
                            // exhaustive match despite the bodies being the same: since if more quantifiers are added, should reconsider here instead of blindly hoisting the new quantifier
                            match symbol {
                                QSymbol::Forall => {
                                    return reconstruct_7cd(QSymbol::Forall, name, left, body);
                                }
                                QSymbol::Exists => {
                                    return reconstruct_7cd(QSymbol::Exists, name, left, body);
                                }
                            }
                        }
                        right => Box::new(right),
                    };
                    (
                        Binop {
                            symbol: BSymbol::Implies,
                            left,
                            right,
                        },
                        false,
                    )
                }
                _ => (expr, false),
            }
        })
    }

    pub fn aristotelean_square(self) -> Expr {
        use Expr::*;

        self.transform(&|expr| {
            let gen_opposite = |symbol, name, body| match symbol {
                QSymbol::Exists => (
                    Quantifier {
                        symbol: QSymbol::Forall,
                        name,
                        body,
                    },
                    true,
                ),
                QSymbol::Forall => (
                    Quantifier {
                        symbol: QSymbol::Exists,
                        name,
                        body,
                    },
                    true,
                ),
            };

            let orig_expr = expr.clone();
            match expr {
                // find unop quantifier on the left
                Unop {
                    symbol: USymbol::Not,
                    operand,
                } => {
                    match *operand {
                        Quantifier { symbol, name, body } => {
                            match *body {
                                // find implies, turn into associative binop
                                Binop {
                                    symbol: BSymbol::Implies,
                                    left,
                                    right,
                                } => {
                                    let new_exprs = vec![*left, Expr::not(*right)];

                                    let new_body = AssocBinop {
                                        symbol: ASymbol::And,
                                        exprs: new_exprs,
                                    };
                                    gen_opposite(symbol, name, Box::new(new_body))
                                }

                                // find and with exactly 2 exprs
                                AssocBinop {
                                    symbol: ASymbol::And,
                                    exprs,
                                } => {
                                    if exprs.len() != 2 {
                                        (orig_expr, false)
                                    } else {
                                        let new_body = Binop {
                                            symbol: BSymbol::Implies,
                                            left: Box::new(exprs[0].clone()),
                                            right: Box::new(Expr::not(exprs[1].clone())),
                                        };

                                        gen_opposite(symbol, name, Box::new(new_body))
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

    pub fn quantifier_distribution(self) -> Expr {
        use Expr::*;

        let push_quantifier_inside = |qsymbol: QSymbol, qname: String, exprs: &mut Vec<Expr>| {
            for iter in exprs.iter_mut() {
                match qsymbol {
                    QSymbol::Exists => {
                        let tmp = mem::replace(iter, Contradiction);
                        *iter = Expr::exists(qname.as_str(), tmp);
                    }

                    QSymbol::Forall => {
                        let tmp = mem::replace(iter, Contradiction);
                        *iter = Expr::forall(qname.as_str(), tmp);
                    }
                }
            }
        };

        self.transform(&|expr| {
            let orig_expr = expr.clone();

            match expr {
                Quantifier {
                    symbol: qsymbol,
                    name,
                    body,
                } => {
                    match *body {
                        AssocBinop {
                            symbol: asymbol,
                            mut exprs,
                        } => {
                            // continue only if asymbol is And or Or
                            match asymbol {
                                ASymbol::And | ASymbol::Or => {}
                                _ => return (orig_expr, false),
                            };

                            // inline push_quantifier_inside here
                            push_quantifier_inside(qsymbol, name, &mut exprs);
                            (Expr::assocbinop(asymbol, &exprs), true)
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
        use Expr::*;

        // Helper function for converting a vector of expressions to NNF
        fn map_nnf(exprs: Vec<Expr>) -> Option<Vec<NnfExpr>> {
            exprs.into_iter().map(Expr::into_nnf).collect()
        }

        // Recursively convert to NNF
        match self {
            // Base cases
            Contradiction => Some(NnfExpr::contradiction()),
            Tautology => Some(NnfExpr::tautology()),
            Var { name } => Some(NnfExpr::var(name)),
            Apply { .. } | Quantifier { .. } => None,

            // Recursive cases
            Unop {
                symbol: USymbol::Not,
                operand,
            } => operand.into_nnf().map(NnfExpr::not),
            Binop {
                symbol,
                left,
                right,
            } => match symbol {
                BSymbol::Implies => {
                    let left = left.into_nnf()?;
                    let right = right.into_nnf()?;
                    Some(left.implies(right))
                }
                BSymbol::Mult | BSymbol::Plus => None,
            },
            AssocBinop { symbol, exprs } => match symbol {
                ASymbol::And => map_nnf(exprs).map(|exprs| NnfExpr::And { exprs }),
                ASymbol::Or => map_nnf(exprs).map(|exprs| NnfExpr::Or { exprs }),
                ASymbol::Bicon => exprs
                    .into_iter()
                    .map(Self::into_nnf)
                    .collect::<Option<Vec<NnfExpr>>>()?
                    .into_iter()
                    .fold1(NnfExpr::bicon),
                ASymbol::Equiv => None,
            },
        }
    }
}

impl NnfExpr {
    /// Create a true NNF expression.
    ///
    /// ```rust
    /// use aris::parser::parse_unwrap as p;
    /// # use aris::expr::NnfExpr;
    ///
    /// assert_eq!(p("⊤").into_nnf(), Some(NnfExpr::tautology()));
    /// ```
    pub fn tautology() -> Self {
        // An empty AND
        // AND() ≡ ⊤
        Self::And { exprs: vec![] }
    }

    /// Create a false NNF expression.
    ///
    /// ```rust
    /// use aris::parser::parse_unwrap as p;
    /// # use aris::expr::NnfExpr;
    ///
    /// assert_eq!(p("⊥").into_nnf(), Some(NnfExpr::contradiction()));
    /// ```
    pub fn contradiction() -> Self {
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
    /// Create a true CNF expression.
    ///
    /// ```rust
    /// use aris::parser::parse_unwrap as p;
    /// # use aris::expr::CnfExpr;
    ///
    /// assert_eq!(p("⊤").into_cnf(), Some(CnfExpr::tautology()));
    /// ```
    pub fn tautology() -> Self {
        // An empty AND
        // AND() ≡ ⊤
        CnfExpr(vec![])
    }

    /// Create a false CNF expression.
    ///
    /// ```rust
    /// use aris::parser::parse_unwrap as p;
    /// # use aris::expr::CnfExpr;
    ///
    /// assert_eq!(p("⊥").into_cnf(), Some(CnfExpr::contradiction()));
    /// ```
    pub fn contradiction() -> Self {
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
            CnfExpr::contradiction()
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

pub fn expressions_for_depth(
    depth: usize,
    max_assoc: usize,
    mut vars: BTreeSet<String>,
) -> BTreeSet<Expr> {
    let mut ret = BTreeSet::new();
    if depth == 0 {
        ret.insert(Expr::Contradiction);
        ret.insert(Expr::Tautology);
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
        for symbol in &[BSymbol::Implies /*BSymbol::Plus, BSymbol::Mult*/] {
            for lhs in smaller.iter() {
                for rhs in smaller.iter() {
                    ret.insert(Expr::binop(*symbol, lhs.clone(), rhs.clone()));
                }
            }
        }
        for symbol in &[ASymbol::And, ASymbol::Or, ASymbol::Bicon, ASymbol::Equiv] {
            for arglist in products.iter() {
                ret.insert(Expr::assocbinop(*symbol, &arglist));
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

/*
pub struct ExpressionGenerator {
    num_freevars: usize,
    max_depth: usize,
    vars_in_scope: BTreeSet<String>,
    queue: VecDeque<Expr>,
}

impl ExpressionGenerator {
    pub fn new() -> ExpressionGenerator {
        ExpressionGenerator {
            num_freevars: 0,
            max_depth: 0,
            vars_in_scope: BTreeSet::new(),
            queue: VecDeque::from_iter(vec![Expr::Contradiction, Expr::Tautology]),
        }
    }
    pub fn generate_batch(&mut self) {
        //let queue = vec![];
    }
}

impl Iterator for ExpressionGenerator {
    type Item = Expr;
    fn next(&mut self) -> Option<Expr> {
        self.queue.pop_front()
    }
}
*/
