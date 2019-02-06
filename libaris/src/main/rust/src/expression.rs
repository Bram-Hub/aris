use std::collections::HashSet;

#[derive(Clone, Debug, PartialEq, Eq)]
#[repr(C)]
pub enum USymbol { Not }
#[derive(Clone, Debug, PartialEq, Eq)]
#[repr(C)]
pub enum BSymbol { Implies, Plus, Mult }
#[derive(Clone, Debug, PartialEq, Eq)]
#[repr(C)]
pub enum ASymbol { And, Or, Bicon }
#[derive(Clone, Debug, PartialEq, Eq)]
#[repr(C)]
pub enum QSymbol { Forall, Exists }

#[derive(Clone, Debug, PartialEq, Eq)]
#[repr(C)]
pub enum Expr {
    Bottom,
    Predicate { name: String, args: Vec<String> },
    Unop { symbol: USymbol, operand: Box<Expr> },
    Binop { symbol: BSymbol, left: Box<Expr>, right: Box<Expr> },
    AssocBinop { symbol: ASymbol, exprs: Vec<Expr> },
    Quantifier { symbol: QSymbol, name: String, body: Box<Expr> },
}

pub fn freevars(e: &Expr) -> HashSet<String> {
    let mut r = HashSet::new();
    match e {
        Expr::Bottom => (),
        Expr::Predicate { name, args } => { r.insert(name.clone()); r.extend(args.iter().cloned()); },
        Expr::Unop { operand, .. } => { r.extend(freevars(operand)); },
        Expr::Binop { left, right, .. } => { r.extend(freevars(left)); r.extend(freevars(right)); },
        Expr::AssocBinop { exprs, .. } => { for expr in exprs.iter() { r.extend(freevars(expr)); } }
        Expr::Quantifier { name, body, .. } => { r.extend(freevars(body)); r.remove(name); }
    }
    r
}


pub mod expression_builders {
    use super::{Expr, USymbol, BSymbol, ASymbol, QSymbol};
    pub fn predicate(name: &str, args: &[&str]) -> Expr { Expr::Predicate { name: name.into(), args: args.iter().map(|&x| x.into()).collect() } }
    pub fn notexp(expr: Expr) -> Expr { Expr::Unop { symbol: USymbol::Not, operand: Box::new(expr) } }
    pub fn binop(symbol: BSymbol, l: Expr, r: Expr) -> Expr { Expr::Binop { symbol, left: Box::new(l), right: Box::new(r) } }
    pub fn assocbinop(symbol: ASymbol, exprs: &[Expr]) -> Expr { Expr::AssocBinop { symbol, exprs: exprs.iter().cloned().collect() } }
    pub fn forall(name: &str, body: Expr) -> Expr { Expr::Quantifier { symbol: QSymbol::Forall, name: name.into(), body: Box::new(body) } }
    pub fn exists(name: &str, body: Expr) -> Expr { Expr::Quantifier { symbol: QSymbol::Exists, name: name.into(), body: Box::new(body) } }
}

