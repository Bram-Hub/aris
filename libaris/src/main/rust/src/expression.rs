use std::collections::HashSet;

#[derive(Copy, Clone, Debug, PartialEq, Eq)]
#[repr(C)]
pub enum USymbol { Not }
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
#[repr(C)]
pub enum BSymbol { Implies, Plus, Mult }
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
#[repr(C)]
pub enum ASymbol { And, Or, Bicon }
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
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

impl std::fmt::Display for USymbol {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        match self { USymbol::Not => write!(f, "~"), }
    }
}

impl std::fmt::Display for BSymbol {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        match self {
            BSymbol::Implies => write!(f, "->"),
            BSymbol::Plus => write!(f, "+"),
            BSymbol::Mult => write!(f, "*"),
        }
    }
}

impl std::fmt::Display for ASymbol {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        match self {
            ASymbol::And => write!(f, "&"),
            ASymbol::Or => write!(f, "|"),
            ASymbol::Bicon => write!(f, "<->"),
        }
    }
}

impl std::fmt::Display for QSymbol {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        match self {
            QSymbol::Forall => write!(f, "forall"),
            QSymbol::Exists => write!(f, "exists"),
        }
    }
}

impl std::fmt::Display for Expr {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        use Expr::*;
        match self {
            Bottom => write!(f, "_|_"),
            Predicate { name, args } => { write!(f, "{}", name)?; if args.len() > 0 { write!(f, "({})", args.join(", "))? }; Ok(()) }
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
    pub fn not(expr: Expr) -> Expr { Expr::Unop { symbol: USymbol::Not, operand: Box::new(expr) } }
    pub fn var(name: &str) -> Expr { predicate(name, &[]) }
    pub fn binop(symbol: BSymbol, l: Expr, r: Expr) -> Expr { Expr::Binop { symbol, left: Box::new(l), right: Box::new(r) } }
    pub fn assocbinop(symbol: ASymbol, exprs: &[Expr]) -> Expr { Expr::AssocBinop { symbol, exprs: exprs.iter().cloned().collect() } }
    pub fn forall(name: &str, body: Expr) -> Expr { Expr::Quantifier { symbol: QSymbol::Forall, name: name.into(), body: Box::new(body) } }
    pub fn exists(name: &str, body: Expr) -> Expr { Expr::Quantifier { symbol: QSymbol::Exists, name: name.into(), body: Box::new(body) } }
}

