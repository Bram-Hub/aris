use super::*;
use std::collections::HashSet;

#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash, Ord, PartialOrd)]
#[repr(C)]
pub enum USymbol { Not }
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash, Ord, PartialOrd)]
#[repr(C)]
pub enum BSymbol { Implies, Plus, Mult }
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash, Ord, PartialOrd)]
#[repr(C)]
pub enum ASymbol { And, Or, Bicon, Equiv }
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash, Ord, PartialOrd)]
#[repr(C)]
pub enum QSymbol { Forall, Exists }

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

trait PossiblyCommutative {
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

pub fn gensym(orig: &str, avoid: &HashSet<String>) -> String {
    for i in 0u64.. {
        let ret = format!("{}{}", orig, i);
        if !avoid.contains(&ret[..]) {
            return ret;
        }
    }
    panic!("Somehow gensym used more than 2^{64} ids without finding anything?")
}

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
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
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
/// a == b -> try_unify(&a, &b) == Some(vec![])
pub fn unify(mut c: HashSet<Constraint<Expr>>) -> Option<Substitution<String, Expr>> {
    // inspired by TAPL 22.4
    //println!("\t{:?}", c);
    let mut c_ = c.clone(); ;
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
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
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

pub fn sort_commutative_ops(e: Expr) -> Expr {
    use Expr::*;
    let rec = |x: Box<_>| Box::new(sort_commutative_ops(*x));
    match e {
        Contradiction => Contradiction,
        Tautology => Tautology,
        Var { name } => Var { name },
        Apply { func, args } => Apply { func: rec(func), args: args.into_iter().map(sort_commutative_ops).collect() },
        Unop { symbol, operand } => Unop { symbol, operand: rec(operand) },
        Binop { symbol, left, right } => {
            let a = rec(left); let b = rec(right);
            if symbol.is_commutative() {
                let (left, right) = if a <= b { (a, b) } else { (b, a) };
                Binop { symbol, left, right }
            }
            else { Binop { symbol, left: a, right: b } }
        },
        AssocBinop { symbol, exprs } => {
            let mut exprs: Vec<_> = exprs.into_iter().map(sort_commutative_ops).collect();
            if symbol.is_commutative() { exprs.sort() };
            AssocBinop { symbol, exprs }
        },
        Quantifier { symbol, name, body } => Quantifier { symbol, name, body: rec(body) },
    }
}

pub fn combine_associative_ops(e: Expr) -> Expr {
    use Expr::*;
    let rec = |x: Box<_>| Box::new(combine_associative_ops(*x));
    match e {
        Contradiction => Contradiction,
        Tautology => Tautology,
        Var { name } => Var { name },
        Apply { func, args } => Apply { func: rec(func), args: args.into_iter().map(combine_associative_ops).collect() },
        Unop { symbol, operand } => Unop { symbol, operand: rec(operand) },
        Binop { symbol, left, right } => Binop { symbol, left: rec(left), right: rec(right) },
        AssocBinop { symbol: symbol1, exprs: exprs1 } => {
            let mut result = vec![];
            for expr in exprs1.into_iter().map(combine_associative_ops) {
                if let AssocBinop { symbol: symbol2, exprs: exprs2 } = expr {
                    if symbol1 == symbol2 {
                        result.extend(exprs2);
                    } else {
                        result.push(AssocBinop { symbol: symbol2, exprs: exprs2 });
                    }
                } else {
                    result.push(expr);
                }
            }
            AssocBinop { symbol: symbol1, exprs: result }
        },
        Quantifier { symbol, name, body } => Quantifier { symbol, name, body: rec(body) },
    }
}

#[test]
pub fn test_combine_associative_ops() {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let f = |s: &str| {
        let e = p(s);
        println!("association of {} is {}", e, combine_associative_ops(e.clone()));
    };
    f("a & (b & (c | (p -> (q <-> (r <-> s)))) & ((t === u) === (v === ((w | x) | y))))");
    f("a & ((b & c) | (q | r))");
    f("(a & (b & c)) | (q | r)");
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
pub fn transform_expr<Trans>(e: Expr, trans_fn: &Trans) -> Expr
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
    let (mut result, mut status) = transform_expr_inner(e, trans_fn);
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
pub fn normalize_demorgans(e: Expr) -> Expr {
    use Expr::*;

    transform_expr(e, &|expr| {
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
pub fn normalize_idempotence(e: Expr) -> Expr {
    use Expr::*;

    transform_expr(e, &|expr| {
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
pub fn to_prenex(e: &Expr) -> Expr {
    use Expr::*; use QSymbol::*;
    match e {
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

