use super::*;
use frunk::coproduct::*;
use std::ops::Range;
use std::fmt::{Display, Formatter};

/// This module houses different proof representations with different performance/simplicity tradeoffs
/// The intent is that once experimentation is done data-structure-wise, these can be packaged up as java objects

/// treeproof represents a proof as a vec of premises and a vec of non-premise lines with either rule applications (with explicit line numbers for dependencies) or nested subproofs, with hooks for annotations
/// Pros:
/// - rules out invalid nesting
/// - close to the presentation of the system in the textbook
/// Cons:
/// - many operations of interest {adding and deleting lines, recomputing line numbers for dependencies} are O(n)
pub mod treeproof;
#[cfg(test)] mod treeproof_tests;

/// pooledproof represents proofs as ZipperVec's of indices into three seperate pools of {premises, justifications, subproofs}
/// Pros:
/// - allows approximate O(1) inserts/removals of nearby lines (gracefully degrading to O(n) if edits are made at opposite ends of the proof)
/// - references to pool entries are never invalidated, which means that moving lines around can be efficiently supported
/// - looking up steps by line number should be O(1), which enables efficiently checking individual rules, elimintating the need for a check-cache
///     (if I understand correctly, the current java version caches rule checks, which might lead to soundness bugs if the cache isn't invalidated correctly)
/// Cons:
/// - potentially more implementation effort than treeproof
/// - nontrivial mapping between references and line numbers might require some additional design
pub mod pooledproof;

/// DisplayIndented gives a convention for passing around state to pretty printers
/// it is intended that objects that implement this implement display as:
/// `fn fmt(&self, fmt: &mut Formatter) -> std::result::Result<(), std::fmt::Error> { self.display_indented(fmt, 1, &mut 1) }`
/// but this cannot be given as a blanket implementation due to coherence rules
pub trait DisplayIndented {
    fn display_indented(&self, fmt: &mut Formatter, indent: usize, linecount: &mut usize) -> Result<(), std::fmt::Error>;
}

pub trait Proof: Sized {
    type Reference: Clone;
    fn new() -> Self;
    fn lookup(&self, r: Self::Reference) -> Coprod!(Expr, Justification<Self::Reference>, Self);
    fn add_premise(&mut self, e: Expr) -> Self::Reference;
    fn add_subproof(&mut self, sub: Self) -> Self::Reference;
    fn add_step(&mut self, just: Justification<Self::Reference>) -> Self::Reference;
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Rule {
    AndIntro, AndElim,
    OrIntro, OrElim,
    ImpIntro, ImpElim,
    NotIntro, NotElim,
    BotIntro, BotElim,
    ForallIntro, ForallElim,
    ExistsIntro, ExistsElim,
    Reit,
}

impl Rule {
    fn get_depcount(&self) -> Option<(usize, usize)> /* (lines, subproofs) */ {
        use Rule::*;
        match self {
            Reit | AndElim | OrIntro | NotElim | BotElim | ExistsIntro => Some((1, 0)),
            BotIntro | ImpElim | ForallElim => Some((2, 0)),
            NotIntro | ImpIntro | ForallIntro => Some((0, 1)),
            ExistsElim => Some((1, 1)),
            AndIntro | OrElim => None, // AndIntro and OrElim can have arbitrarily many conjuncts/disjuncts in one application
        }
    }
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Justification<R>(Expr, Rule, Vec<R>);

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct LineAndIndent { pub line: usize, pub indent: usize }

#[derive(Debug, PartialEq, Eq)]
pub enum ProofCheckError {
    LineDoesNotExist(usize),
    ReferencesLaterLine(LineAndIndent, usize),
    IncorrectDepCount(Vec<Range<usize>>, usize, usize),
    DepOfWrongForm(String),
    DoesNotOccur(Expr, Expr),
}
