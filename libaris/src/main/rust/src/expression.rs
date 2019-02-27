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
pub enum ASymbol { And, Or, Bicon }
#[derive(Copy, Clone, Debug, PartialEq, Eq, Hash, Ord, PartialOrd)]
#[repr(C)]
pub enum QSymbol { Forall, Exists }

#[derive(Clone, Debug, PartialEq, Eq, Hash, Ord, PartialOrd)]
#[repr(C)]
pub enum Expr {
    Bottom,
    Predicate { name: String, args: Vec<Expr> },
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
            Bottom => write!(f, "⊥"),
            Predicate { name, args } => { write!(f, "{}", name)?; if args.len() > 0 { write!(f, "({})", args.iter().map(|x| format!("{}", x)).collect::<Vec<_>>().join(", "))? }; Ok(()) }
            Unop { symbol, operand } => write!(f, "{}{}", symbol, operand),
            Binop { symbol, left, right } => write!(f, "({} {} {})", left, symbol, right),
            AssocBinop { symbol, exprs } => write!(f, "({})", exprs.iter().map(|x| format!("{}", x)).collect::<Vec<_>>().join(&format!(" {} ", symbol))),
            Quantifier { symbol, name, body } => write!(f, "({} {}, {})", symbol, name, body),
        }
    }
}

pub fn freevars(e: &Expr) -> HashSet<String> {
    let mut r = HashSet::new();
    match e {
        Expr::Bottom => (),
        Expr::Predicate { name, args } => { r.insert(name.clone()); for s in args.iter().map(|x| freevars(x)) { r.extend(s); } },
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
        Expr::Bottom => Expr::Bottom,
        Expr::Predicate { ref name, ref args } => {
            if name == to_replace {
                with // TODO: seperate predicate and variable ASTs? this is wrong for second-order logic
            } else {
                Expr::Predicate { name: name.clone(), args: args.iter().map(|e2| subst(e2, to_replace, with.clone())).collect() }
            }
        },
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
}

pub mod expression_builders {
    use super::{Expr, USymbol, BSymbol, ASymbol, QSymbol};
    pub fn predicate(name: &str, args: &[&str]) -> Expr { Expr::Predicate { name: name.into(), args: args.iter().map(|&x| var(x)).collect() } }
    pub fn not(expr: Expr) -> Expr { Expr::Unop { symbol: USymbol::Not, operand: Box::new(expr) } }
    pub fn var(name: &str) -> Expr { predicate(name, &[]) }
    pub fn binop(symbol: BSymbol, l: Expr, r: Expr) -> Expr { Expr::Binop { symbol, left: Box::new(l), right: Box::new(r) } }
    pub fn binopplaceholder(symbol: BSymbol) -> Expr { binop(symbol, var("_"), var("_")) }
    pub fn assocbinop(symbol: ASymbol, exprs: &[Expr]) -> Expr { Expr::AssocBinop { symbol, exprs: exprs.iter().cloned().collect() } }
    pub fn assocplaceholder(symbol: ASymbol) -> Expr { assocbinop(symbol, &[var("_"), var("_"), var("...")]) }
    pub fn forall(name: &str, body: Expr) -> Expr { Expr::Quantifier { symbol: QSymbol::Forall, name: name.into(), body: Box::new(body) } }
    pub fn exists(name: &str, body: Expr) -> Expr { Expr::Quantifier { symbol: QSymbol::Exists, name: name.into(), body: Box::new(body) } }
}

