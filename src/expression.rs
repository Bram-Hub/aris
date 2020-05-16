/*!
# Usage

Parsing an expression that's statically known to be valid:

```
use libaris::parser::parse_unwrap as p;

let expr1 = p("forall x, p(x) -> (Q & R)");
```

Parsing a potentially malformed expression (e.g. user input):
```
use libaris::parser;

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
use libaris::parser::parse_unwrap as p;
use libaris::expression::*;

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
    use libaris::expression::Expr::*;
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
use super::*;
use std::collections::{HashSet, HashMap};
use std::collections::BTreeSet;
use std::mem;

/// Symbol for unary operations
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash, Ord, PartialOrd)]
#[repr(C)]
pub enum USymbol { Not }
/// Symbol for binary operations
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash, Ord, PartialOrd)]
#[repr(C)]
pub enum BSymbol { Implies, Plus, Mult }
/// Symbol for associative binary operations
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash, Ord, PartialOrd)]
#[repr(C)]
pub enum ASymbol { And, Or, Bicon, Equiv }
/// Symbol for quantifiers
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash, Ord, PartialOrd)]
#[repr(C)]
pub enum QSymbol { Forall, Exists }

/// libaris::expression::Expr is the core AST (Abstract Syntax Tree) type for representing logical expressions.
/// For most of the recursive cases, it uses symbols so that code can work on the shape of e.g. a binary operation without worrying about which binary operation it is.
#[derive(Clone, Debug, PartialEq, Eq, Hash, Ord, PartialOrd)]
#[repr(C)]
pub enum Expr {
    Contradiction,
    Tautology,
    Var { name: String },
    Apply { func: Box<Expr>, args: Vec<Expr> },
    Unop { symbol: USymbol, operand: Box<Expr> },
    Binop { symbol: BSymbol, left: Box<Expr>, right: Box<Expr> },
    AssocBinop { symbol: ASymbol, exprs: Vec<Expr> },
    Quantifier { symbol: QSymbol, name: String, body: Box<Expr> },
}

impl std::fmt::Display for USymbol {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        match self { USymbol::Not => write!(f, "¬"), }
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

impl std::fmt::Display for Expr {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        use Expr::*;
        match self {
            Contradiction => write!(f, "⊥"),
            Tautology => write!(f, "⊤"),
            Var { name } => write!(f, "{}", name),
            Apply { func, args } => { write!(f, "{}", func)?; if args.len() > 0 { write!(f, "({})", args.iter().map(|x| format!("{}", x)).collect::<Vec<_>>().join(", "))? }; Ok(()) }
            Unop { symbol, operand } => write!(f, "{}{}", symbol, operand),
            Binop { symbol, left, right } => write!(f, "({} {} {})", left, symbol, right),
            AssocBinop { symbol, exprs } => write!(f, "({})", exprs.iter().map(|x| format!("{}", x)).collect::<Vec<_>>().join(&format!(" {} ", symbol))),
            Quantifier { symbol, name, body } => write!(f, "({} {}, {})", symbol, name, body),
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
        Expr::Var { name } => { r.insert(name.clone()); }
        Expr::Apply { func, args } => { r.extend(freevars(func)); for s in args.iter().map(|x| freevars(x)) { r.extend(s); } },
        Expr::Unop { operand, .. } => { r.extend(freevars(operand)); },
        Expr::Binop { left, right, .. } => { r.extend(freevars(left)); r.extend(freevars(right)); },
        Expr::AssocBinop { exprs, .. } => { for expr in exprs.iter() { r.extend(freevars(expr)); } }
        Expr::Quantifier { name, body, .. } => { r.extend(freevars(body)); r.remove(name); }
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
    panic!("Somehow gensym used more than 2^{64} ids without finding anything?")
}

/// `subst(e, to_replace, with)` performs capture-avoiding substitution of free variables named `to_replace` with `with` in `e`
pub fn subst(e: &Expr, to_replace: &str, with: Expr) -> Expr {
    match e {
        Expr::Contradiction => Expr::Contradiction,
        Expr::Tautology => Expr::Tautology,
        Expr::Var { ref name } => if name == to_replace { with } else { Expr::Var { name: name.clone() } },
        Expr::Apply { ref func, ref args } => Expr::Apply { func: Box::new(subst(func, to_replace, with.clone())), args: args.iter().map(|e2| subst(e2, to_replace, with.clone())).collect() },
        Expr::Unop { symbol, operand } => Expr::Unop { symbol: symbol.clone(), operand: Box::new(subst(operand, to_replace, with)) },
        Expr::Binop { symbol, left, right } => Expr::Binop { symbol: symbol.clone(), left: Box::new(subst(left, to_replace, with.clone())), right: Box::new(subst(right, to_replace, with)) },
        Expr::AssocBinop { symbol, exprs } => Expr::AssocBinop { symbol: symbol.clone(), exprs: exprs.iter().map(|e2| subst(e2, to_replace, with.clone())).collect() },
        Expr::Quantifier { symbol, name, body } => {
            let fv_with = freevars(&with);
            let (newname, newbody) = match (name == to_replace, fv_with.contains(name)) {
                (true, _) => (name.clone(), *body.clone()),
                (false, true) => {
                    let newname = gensym(name, &fv_with);
                    let body0 = subst(body, name, expression_builders::var(&newname[..]));
                    let body1 = subst(&body0, to_replace, with);
                    //println!("{:?}\n{:?}\n{:?}", body, body0, body1);
                    (newname.clone(), body1)
                },
                (false, false) => { (name.clone(), subst(body, to_replace, with)) },
            };
            Expr::Quantifier { symbol: symbol.clone(), name: newname, body: Box::new(newbody) }
        },
    }
}

#[test]
fn test_subst() {
    use parser::parse_unwrap as p;
    assert_eq!(subst(&p("x & forall x, x"), "x", p("y")), p("y & forall x, x")); // hit (true, _) case in Quantifier
    assert_eq!(subst(&p("forall x, x & y"), "y", p("x")), p("forall x0, x0 & x")); // hit (false, true) case in Quantifier
    assert_eq!(subst(&p("forall x, x & y"), "y", p("z")), p("forall x, x & z")); // hit (false, false) case in Quantifier
    assert_eq!(subst(&p("forall f, f(x) & g(y, z)"), "g", p("h")), p("forall f, f(x) & h(y, z)"));
    assert_eq!(subst(&p("forall f, f(x) & g(y, z)"), "g", p("f")), p("forall f0, f0(x) & f(y, z)"));
}


#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub enum Constraint<A> { Equal(A, A) }
#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub struct Substitution<A, B>(pub Vec<(A, B)>);
/// Unifies a set of equality constraints on expressions, giving a list of substitutions that make constrained expressions equal.
/// a == b -> unify(HashSet::from_iter(vec![Constraint::Equal(a, b)])) == Some(vec![])
pub fn unify(mut c: HashSet<Constraint<Expr>>) -> Option<Substitution<String, Expr>> {
    // inspired by TAPL 22.4
    //println!("\t{:?}", c);
    let mut c_ = c.clone();
    let Constraint::Equal(s,t) = if let Some(x) = c_.drain().next() { c.remove(&x); x } else { return Some(Substitution(vec![])) };
    use Expr::*;
    let subst_set = |x, e1: Expr, set: HashSet<_>| { set.into_iter().map(|Constraint::Equal(e2, e3)| Constraint::Equal(subst(&e2, x, e1.clone()), subst(&e3, x, e1.clone()))).collect::<_>() };
    let (fvs, fvt) = (freevars(&s), freevars(&t));
    match (&s, &t) {
        (_, _) if s == t => unify(c),
        (Var { name: ref sname }, _) if !fvt.contains(sname) => unify(subst_set(&sname, t.clone(), c)).map(|mut x| { x.0.push((sname.clone(), t.clone())); x }),
        (_, Var { name: ref tname }) if !fvs.contains(tname) => unify(subst_set(&tname, s.clone(), c)).map(|mut x| { x.0.push((tname.clone(), s.clone())); x }),
        (Unop { symbol: ss, operand: so }, Unop { symbol: ts, operand: to }) if ss == ts => { c.insert(Constraint::Equal(*so.clone(), *to.clone())); unify(c) },
        (Binop { symbol: ss, left: sl, right: sr }, Binop { symbol: ts, left: tl, right: tr }) if ss == ts => {
            c.insert(Constraint::Equal(*sl.clone(), *tl.clone()));
            c.insert(Constraint::Equal(*sr.clone(), *tr.clone()));
            unify(c)
        },
        (Apply { func: sf, args: sa }, Apply { func: tf, args: ta }) if sa.len() == ta.len() => {
            c.insert(Constraint::Equal(*sf.clone(), *tf.clone()));
            c.extend(sa.iter().zip(ta.iter()).map(|(x,y)| Constraint::Equal(x.clone(), y.clone())));
            unify(c)
        }
        (AssocBinop { symbol: ss, exprs: se }, AssocBinop { symbol: ts, exprs: te }) if ss == ts && se.len() == te.len() => {
            c.extend(se.iter().zip(te.iter()).map(|(x,y)| Constraint::Equal(x.clone(), y.clone())));
            unify(c)
        },
        (Quantifier { symbol: ss, name: sn, body: sb }, Quantifier { symbol: ts, name: tn, body: tb }) if ss == ts => {
            let uv = gensym("__unification_var", &fvs.union(&fvt).cloned().collect());
            // require that the bodies of the quantifiers are alpha-equal by substituting a fresh constant
            c.insert(Constraint::Equal(subst(sb, sn, expression_builders::var(&uv)), subst(tb, tn, expression_builders::var(&uv))));
            // if the constant escapes, then a free variable in one formula unified with a captured variable in the other, so the values don't unify
            unify(c).and_then(|sub| if sub.0.iter().any(|(x, y)| x == &uv || freevars(y).contains(&uv)) { None } else { Some(sub) } )
        }
        _ => None,
    }
}

#[test]
fn test_unify() {
    use parser::parse_unwrap as p;
    let u = |s, t| {
        let l = p(s);
        let r = p(t);
        let ret = unify(vec![Constraint::Equal(l.clone(), r.clone())].into_iter().collect());
        if let Some(ref ret) = ret {
            let subst_l = ret.0.iter().fold(l.clone(), |z, (x, y)| subst(&z, x, y.clone()));
            let subst_r = ret.0.iter().fold(r.clone(), |z, (x, y)| subst(&z, x, y.clone()));
            // TODO: assert alpha_equal(subst_l, subst_r);
            println!("{} {} {:?} {} {}", l, r, ret, subst_l, subst_r);
        }
        ret
    };
    println!("{:?}", u("x", "forall y, y"));
    println!("{:?}", u("forall y, y", "y"));
    println!("{:?}", u("x", "x"));
    assert_eq!(u("forall x, x", "forall y, y"), Some(Substitution(vec![]))); // should be equal with no substitution since unification is modulo alpha equivalence
    println!("{:?}", u("f(x,y,z)", "g(x,y,y)"));
    println!("{:?}", u("g(x,y,y)", "f(x,y,z)"));
    println!("{:?}", u("forall foo, foo(x,y,z) & bar", "forall bar, bar(x,y,z) & baz"));

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
    pub fn infer_arities(&self, arities: &mut HashMap<String, usize>) {
        use Expr::*;
        match self {
            Contradiction | Tautology => {},
            Var { name } => { arities.entry(name.clone()).or_insert(0); },
            Apply { func, args } => match &**func {
                Var { name } => {
                    let arity = arities.entry(name.clone()).or_insert(args.len());
                    *arity = args.len().max(*arity);
                    for arg in args {
                        arg.infer_arities(arities);
                    }
                },
                _ => panic!("See note apply_non_literal"),
            },
            Unop { symbol: _, operand } => { operand.infer_arities(arities); },
            Binop { symbol: _, left, right } => { left.infer_arities(arities); right.infer_arities(arities) },
            AssocBinop { symbol: _, exprs } => { for e in exprs { e.infer_arities(arities); } },
            Quantifier { symbol: _, name, body } => {
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
                },
                _ => panic!("See note apply_non_literal"),
            },
            Unop { symbol, operand } => {
                use USymbol::*;
                match symbol {
                    Not => !operand.eval(env),
                }
            }
            Binop { symbol, left, right } => {
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
                for b in exprs.into_iter().map(|e| e.eval(env)) {
                    ret = f(ret, b);
                }
                ret
            },
            Quantifier { .. } => panic!("Expr::eval does not support quantifiers"),
        }
    }
    /// Sort all commutative associative operators to normalize expressions in the case of arbitrary ordering
    /// Eg (B & A) ==> (A & B)
    pub fn sort_commutative_ops(self) -> Expr {
        use Expr::*;

        self.transform(&|e| {
            match e {
                Binop { symbol, left, right } => {
                    if symbol.is_commutative() {
                        let (left, right) = if left <= right { (left, right) } else { (right, left) };
                        (Binop { symbol, left, right }, true)
                    } else {
                        (Binop { symbol, left, right }, false)
                    }
                },
                AssocBinop { symbol, mut exprs } => {
                    let is_sorted = exprs.windows(2).all(|xy| xy[0] <= xy[1]);
                    if symbol.is_commutative() && !is_sorted {
                        exprs.sort();
                        (AssocBinop { symbol, exprs }, true)
                    } else {
                        (AssocBinop { symbol, exprs }, false)
                    }
                },
                _ => (e, false)
            }
        })
    }

    /// Combine associative operators such that nesting is flattened
    /// Eg (A & (B & C)) ==> (A & B & C)
    pub fn combine_associative_ops(self) -> Expr {
        use Expr::*;

        self.transform(&|e| {
            match e {
                AssocBinop { symbol: symbol1, exprs: exprs1 } => {
                    let mut result = vec![];
                    let mut combined = false;
                    for expr in exprs1 {
                        if let AssocBinop { symbol: symbol2, exprs: exprs2 } = expr {
                            if symbol1 == symbol2 {
                                result.extend(exprs2);
                                combined = true;
                            } else {
                                result.push(AssocBinop { symbol: symbol2, exprs: exprs2 });
                            }
                        } else {
                            result.push(expr);
                        }
                    }
                    (AssocBinop { symbol: symbol1, exprs: result }, combined)
                },
                _ => (e, false)
            }
        })
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
    where Trans: Fn(Expr) -> (Expr, bool) {
        use Expr::*;

        // Because I don't like typing
        fn box_transform_expr_inner<Trans>(expr: Box<Expr>, trans: &Trans) -> (Box<Expr>, bool)
        where Trans: Fn(Expr) -> (Expr, bool) {
            let (result, status) = transform_expr_inner(*expr, trans);
            return (Box::new(result), status);
        }

        fn transform_expr_inner<Trans>(expr: Expr, trans: &Trans) -> (Expr, bool)
        where Trans: Fn(Expr) -> (Expr, bool) {
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
                    let (func, fs) = box_transform_expr_inner(func, trans);
                    // Fancy iterator hackery to transform each sub expr and then collect all their results
                    let (args, stats) : (Vec<_>, Vec<_>) = args.into_iter().map(move |expr| transform_expr_inner(expr, trans)).unzip();
                    let success = fs || stats.into_iter().any(|x| x);
                    (Apply { func, args }, success)
                },
                Unop { symbol, operand } => {
                    let (operand, success) = box_transform_expr_inner(operand, trans);
                    (Unop { symbol, operand }, success)
                },
                Binop { symbol, left, right } => {
                    let (left, ls) = box_transform_expr_inner(left, trans);
                    let (right, rs) = box_transform_expr_inner(right, trans);
                    let success = ls || rs;
                    (Binop { symbol, left, right }, success)
                },
                AssocBinop { symbol, exprs } => {
                    let (exprs, stats): (Vec<_>, Vec<_>) = exprs.into_iter().map(move |expr| transform_expr_inner(expr, trans)).unzip();
                    let success = stats.into_iter().any(|x| x);
                    (AssocBinop { symbol, exprs }, success)
                },
                Quantifier { symbol, name, body } => {
                    let (body, success) = box_transform_expr_inner(body, trans);
                    (Quantifier { symbol, name, body }, success)
                },
            };
            // The key to this function is that it returns true if ANYTHING was transformed. That means
            // if either the whole expression or any of the inner expressions, we should re-run on everything.
            (result, status || status2)
        }

        // Worklist: Keep reducing and transforming as long as something changes. This will loop infinitely
        // if your transformation creates patterns that it matches.
        let (mut result, mut status) = transform_expr_inner(self, trans_fn);
        while status {
            // Rust pls
            let (x, y) = transform_expr_inner(result, trans_fn);
            result = x;
            status = y;
        }
        result
    }

    /// Simplify an expression with recursive DeMorgan's
    /// ~(A ^ B) <=> ~A v ~B  /  ~(A v B) <=> ~A ^ ~B
    /// Strategy: Apply this to all ~(A ^ B) constructions
    /// This should leave us with an expression in "DeMorgans'd normal form"
    /// With no ~(A ^ B) / ~(A v B) expressions
    pub fn normalize_demorgans(self) -> Expr {
        use Expr::*;

        self.transform(&|expr| {
            let demorgans = |new_symbol, exprs: Vec<Expr>| {
                AssocBinop {
                    symbol: new_symbol,
                    exprs: exprs.into_iter().map(|expr| Unop {
                        symbol: USymbol::Not,
                        operand: Box::new(expr)
                    }).collect()
                }
            };

            match expr {
                Unop { symbol: USymbol::Not, operand } => {
                    match *operand {
                        AssocBinop { symbol: ASymbol::And, exprs } => (demorgans(ASymbol::Or, exprs), true),
                        AssocBinop { symbol: ASymbol::Or, exprs } => (demorgans(ASymbol::And, exprs), true),
                        _ => (expression_builders::not(*operand), false)
                    }
                }
                _ => (expr, false)
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
                AssocBinop { symbol: symbol @ ASymbol::And, exprs } |
                AssocBinop { symbol: symbol @ ASymbol::Or, exprs } => {

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
                },
                _ => (expr, false)
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
            AssocBinop { symbol: ASymbol::Or, exprs } => exprs.clone(),
            _ => vec![self.clone()],
        }
    }
    pub fn from_disjuncts(mut disjuncts: Vec<Expr>) -> Expr {
        use Expr::*;
        match disjuncts.len() {
            0 => Contradiction,
            1 => disjuncts.pop().unwrap(),
            _ => AssocBinop { symbol: ASymbol::Or, exprs: disjuncts },
        }
    }
    pub fn conjuncts(&self) -> Vec<Expr> {
        use Expr::*;
        match self {
            Tautology => vec![],
            AssocBinop { symbol: ASymbol::And, exprs } => exprs.clone(),
            _ => vec![self.clone()],
        }
    }
    pub fn from_conjuncts(mut conjuncts: Vec<Expr>) -> Expr {
        use Expr::*;
        match conjuncts.len() {
            0 => Tautology,
            1 => conjuncts.pop().unwrap(),
            _ => AssocBinop { symbol: ASymbol::And, exprs: conjuncts },
        }
    }
    pub fn negate_quantifiers(self) -> Expr {
        use Expr::*;

        self.transform(&|expr| {
            let gen_opposite = |new_symbol, name, body | {
                Quantifier {symbol: new_symbol, name, body: Box::new(expression_builders::not(body))}
            };

            match expr {
                Unop {symbol: USymbol::Not, operand} => {
                    match *operand {
                        Quantifier { symbol, name, body } => {
                            match symbol {
                                QSymbol::Exists => (gen_opposite(QSymbol::Forall, name, *body), true),
                                QSymbol::Forall => (gen_opposite(QSymbol::Exists, name, *body), true)
                            }
                        },
                        _ => (expression_builders::not(*operand), false)
                    }
                }
                _ => (expr, false)
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
                },
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
                    let i = gamma.into_iter().enumerate().find(|(_,n)| n == &name).unwrap().0;
                    Var { name: format!("{}", i) }
                },
                // push the name onto gamma from the actual quantifier,
                // Example: for forall x, P(x)
                // push x onto gamma
                // save the length of gamma before recursing, to use as the new name
                Quantifier { symbol, name, body } => {
                    let current_level = format!("{}", gamma.len());
                    gamma.push(name);
                    let new_body = aux(*body, gamma);
                    Quantifier { symbol, name: current_level, body: Box::new(new_body) }
                },
                // All the remainder cases
                Contradiction => Contradiction,
                Tautology => Tautology,
                Apply { func, args } => {
                    let func = aux(*func, gamma.clone());
                    let args = args.into_iter().map(|e| aux(e, gamma.clone())).collect();
                    Apply { func: Box::new(func), args }
                },
                Unop { symbol, operand } => {
                    Unop { symbol, operand: Box::new(aux(*operand, gamma)) }
                },
                Binop { symbol, left, right } => {
                    let left = Box::new(aux(*left, gamma.clone()));
                    let right = Box::new(aux(*right, gamma));
                    Binop { symbol, left, right }
                },
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

            loop {
                match mod_expr {
                    Quantifier { symbol, name, body } => {
                        if last_quantifier.is_none() || last_quantifier == Some(symbol) {
                            last_quantifier = Some(symbol);
                            stack.push( name);
                            mod_expr = *body;
                        } else {
                            mod_expr = Quantifier { symbol, name, body };
                            break;
                        }
                    },
                    _ => break,
                }
            }

            stack.sort();

            if let Some(sym) = &last_quantifier {
                for l_name in stack {
                    mod_expr = Quantifier { symbol: *sym, name: l_name, body: Box::new(mod_expr) };
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
                    },
                    _ => { others.push(expr)},
                }
            }
            if let Some((symbol, name)) = found {
                let body = Box::new(AssocBinop { symbol: asymbol, exprs: others });
                (Quantifier { symbol, name, body }, true)
            } else {
                // if none of the subexpressions were quantifiers whose binder was free, `others` should be in the same as `exprs`
                (AssocBinop { symbol: asymbol, exprs: others }, false)
            }
        };
        let reconstruct_7cd = |symbol: QSymbol, name: String, left, right| {
            let body = Box::new(Binop { symbol: BSymbol::Implies, left, right });
            (Quantifier { symbol, name, body }, true)
        };
        self.transform(&|expr| {
            match expr {
                AssocBinop { symbol, exprs } => {
                    match symbol {
                        ASymbol::And => transform_7ab(ASymbol::And, exprs),
                        ASymbol::Or => transform_7ab(ASymbol::Or, exprs),
                        _ => (AssocBinop { symbol, exprs }, false)
                    }
                },
                Binop { symbol: BSymbol::Implies, mut left, mut right } => {
                    let left_free = freevars(&left);
                    let right_free = freevars(&right);
                    left = match *left {
                        Quantifier { symbol, name, body } if !right_free.contains(&name) => {
                            // 7c case, quantifier is flipped
                            match symbol {
                                QSymbol::Forall => { return reconstruct_7cd(QSymbol::Exists, name, body, right); },
                                QSymbol::Exists => { return reconstruct_7cd(QSymbol::Forall, name, body, right); },
                            }
                        },
                        left => Box::new(left),
                    };
                    right = match *right {
                        Quantifier { symbol, name, body } if !left_free.contains(&name) => {
                            // 7d case, quantifier is not flipped
                            // exhaustive match despite the bodies being the same: since if more quantifiers are added, should reconsider here instead of blindly hoisting the new quantifier
                            match symbol {
                                QSymbol::Forall => { return reconstruct_7cd(QSymbol::Forall, name, left, body); },
                                QSymbol::Exists => { return reconstruct_7cd(QSymbol::Exists, name, left, body); },
                            }
                        },
                        right => Box::new(right),
                    };
                    (Binop { symbol: BSymbol::Implies, left, right }, false)
                },
                _ => (expr, false),
            }
        })
    }

    pub fn aristotelean_square(self) -> Expr {
        use Expr::*;

        self.transform(&|expr| {
            let gen_opposite = |symbol, name, body | {
                match symbol {
                    QSymbol::Exists => (Quantifier { symbol: QSymbol::Forall, name, body }, true),
                    QSymbol::Forall => (Quantifier { symbol: QSymbol::Exists, name, body }, true),
                }
            };

            let orig_expr = expr.clone();
            match expr {
                // find unop quantifier on the left
                Unop { symbol: USymbol::Not, operand } => {
                    match *operand {
                        Quantifier { symbol, name, body } => {
                            match *body {
                                // find implies, turn into associative binop
                                Binop { symbol: BSymbol::Implies, left, right } => {
                                    let new_exprs = vec![*left, expression_builders::not(*right)];

                                    let new_body = AssocBinop { symbol: ASymbol::And, exprs: new_exprs };
                                    gen_opposite(symbol, name, Box::new(new_body))
                                },

                                // find and with exactly 2 exprs
                                AssocBinop {symbol: ASymbol::And, exprs } => {
                                    if exprs.len() != 2 {
                                        (orig_expr, false)
                                    } else {
                                        let new_body = Binop {
                                            symbol: BSymbol::Implies,
                                            left: Box::new(exprs[0].clone()),
                                            right: Box::new(expression_builders::not(exprs[1].clone()))
                                        };

                                        gen_opposite(symbol, name, Box::new(new_body))
                                    }
                                },

                                _ => (orig_expr, false)
                            }
                        },
                        _ => (expression_builders::not(*operand), false)
                    }
                },

                _ => (expr, false)
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
                        *iter = expression_builders::exists(qname.as_str(),  tmp);
                    },

                    QSymbol::Forall => {
                        let tmp = mem::replace(iter, Contradiction);
                        *iter = expression_builders::forall(qname.as_str(), tmp);
                    }
                }
            }
        };

        self.transform(&|expr| {
            let orig_expr = expr.clone();

            match expr {
                Quantifier { symbol: qsymbol, name, body } => {
                    match *body {
                        AssocBinop { symbol: asymbol, mut exprs } => {
                            // continue only if asymbol is And or Or
                            match asymbol {
                                ASymbol::And | ASymbol::Or => {},
                                _ => return (orig_expr, false)
                            };

                            // inline push_quantifier_inside here
                            push_quantifier_inside(qsymbol, name, &mut exprs);
                            (expression_builders::assocbinop(asymbol, &exprs), true)
                        },
                        _ => (orig_expr, false)
                    }
                }
                _ => (expr, false)
            }
        })
    }
}


#[test]
pub fn test_combine_associative_ops() {
    use parser::parse_unwrap as p;
    let f = |s: &str| {
        let e = p(s);
        println!("association of {} is {}", e, e.clone().combine_associative_ops());
    };
    f("a & (b & (c | (p -> (q <-> (r <-> s)))) & ((t === u) === (v === ((w | x) | y))))");
    f("a & ((b & c) | (q | r))");
    f("(a & (b & c)) | (q | r)");
}

/// Convenience functions for constructing `Expr`s inline without needing all the struct boilerplate
pub mod expression_builders {
    use super::{Expr, USymbol, BSymbol, ASymbol, QSymbol};
    pub fn var(name: &str) -> Expr { Expr::Var { name: name.into() } }
    pub fn apply(func: Expr, args: &[Expr]) -> Expr { Expr::Apply { func: Box::new(func), args: args.iter().cloned().collect() } }
    pub fn predicate(name: &str, args: &[&str]) -> Expr { apply(var(name), &args.iter().map(|&x| var(x)).collect::<Vec<_>>()[..]) }
    pub fn not(expr: Expr) -> Expr { Expr::Unop { symbol: USymbol::Not, operand: Box::new(expr) } }
    pub fn binop(symbol: BSymbol, l: Expr, r: Expr) -> Expr { Expr::Binop { symbol, left: Box::new(l), right: Box::new(r) } }
    pub fn binopplaceholder(symbol: BSymbol) -> Expr { binop(symbol, var("_"), var("_")) }
    pub fn assocbinop(symbol: ASymbol, exprs: &[Expr]) -> Expr { Expr::AssocBinop { symbol, exprs: exprs.iter().cloned().collect() } }
    pub fn assocplaceholder(symbol: ASymbol) -> Expr { assocbinop(symbol, &[var("_"), var("_"), var("...")]) }
    pub fn quantifierplaceholder(symbol: QSymbol) -> Expr { Expr::Quantifier { symbol, name: "_".into(), body: Box::new(var("_")) } }
    pub fn forall(name: &str, body: Expr) -> Expr { Expr::Quantifier { symbol: QSymbol::Forall, name: name.into(), body: Box::new(body) } }
    pub fn exists(name: &str, body: Expr) -> Expr { Expr::Quantifier { symbol: QSymbol::Exists, name: name.into(), body: Box::new(body) } }
}

pub fn expressions_for_depth(depth: usize, max_assoc: usize, mut vars: BTreeSet<String>) -> BTreeSet<Expr> {
    let mut ret = BTreeSet::new();
    if depth == 0 {
        ret.insert(Expr::Contradiction);
        ret.insert(Expr::Tautology);
        ret.extend(vars.iter().cloned().map(|name| Expr::Var { name }));
    } else {
        use expression_builders::{var, apply, not, binop, assocbinop, forall, exists};
        let smaller: Vec<_> = expressions_for_depth(depth-1, max_assoc, vars.clone()).into_iter().collect();
        let mut products = vec![];
        for i in 2..=max_assoc {
            products.extend(cartesian_product((0..i).into_iter().map(|_| smaller.clone()).collect()));
        }
        for v in vars.iter() {
            for arglist in products.iter() {
                ret.insert(apply(var(v), arglist));
            }
        }
        for e in smaller.iter() {
            ret.insert(not(e.clone()));
        }
        for symbol in &[BSymbol::Implies, /*BSymbol::Plus, BSymbol::Mult*/] {
            for lhs in smaller.iter() {
                for rhs in smaller.iter() {
                    ret.insert(binop(*symbol, lhs.clone(), rhs.clone()));
                }
            }
        }
        for symbol in &[ASymbol::And, ASymbol::Or, ASymbol::Bicon, ASymbol::Equiv] {
            for arglist in products.iter() {
                ret.insert(assocbinop(*symbol, &arglist));
            }
        }
        let x = format!("x{}", depth);
        vars.insert(x.clone());
        for body in expressions_for_depth(depth-1, max_assoc, vars).into_iter() {
            ret.insert(forall(&x, body.clone()));
            ret.insert(exists(&x, body.clone()));
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
