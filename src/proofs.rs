/*!
# Description
This module houses different proof representations with different performance/simplicity tradeoffs.

# Concrete proof types
You probably want `type P1 = libaris::proofs::pooledproof::PooledProof<Hlist![Expr]>;` if you want to interactively mutate a proof, and
`type P2 = libaris::proofs::lined_proof::LinedProof<P1>;` if you want to display line numbers for a finalized proof.

The rest of the types are either experiments that didn't pan out or are internal shims

# Abstract proof types
Most functions that work with proofs should be generic over `P: Proof` instead of working with a concrete proof representation.

If you want to debug-print line references, you may need to additionally bound `<P as Proof>::Reference: Debug` and `<P as Proof>::SubproofReference: Debug`.

In a main method when you're parsing a proof from xml, or constructing a proof from user input, use a concrete proof type.

# Tests
When implementing rules, add tests to the `proof_tests` submodule of this module.

Construct proofs that exhibit correct usages of the rule, as well as common mistakes that you expect.

The test running apparatus expects you to return the proof, a list of line references that should pass, and a list of line references that should fail.

# Examples

## Parsing XML into a PooledProof

```
#[macro_use] extern crate frunk;
use libaris::expression::Expr;
use libaris::proofs::xml_interop::proof_from_xml;
let data = &include_bytes!("../propositional_logic_arguments_for_proofs_ii_problem_10.bram")[..];
type P = libaris::proofs::pooledproof::PooledProof<Hlist![Expr]>;
let (prf, metadata) = proof_from_xml::<P, _>(data).unwrap();
```

## Creating a proof programatically

ASCII art proof:

```text
1 | P -> Q
  | ---
2 | | ~Q
  | | ---
3 | | | P
  | | | ---
4 | | | Q ; ImpElim, [1, 3]
5 | | | _|_ ; ContradictionIntro, [2, 4]
6 | | ~P ; NotIntro, [3..5]
7 | ~Q -> ~P ; ImpIntro [2..6]
```

Code that builds the proof generically and then instantiates it:
```
#[macro_use] extern crate frunk;
use libaris::expression::Expr;
use libaris::proofs::{Proof, Justification, pooledproof::PooledProof};
use libaris::rules::RuleM;
fn contraposition_demo<P: Proof>() -> P {
    use libaris::parser::parse_unwrap as p;
    let mut prf = P::new();
    let line1 = prf.add_premise(p("P -> Q"));
    let sub2to6 = prf.add_subproof();
    prf.with_mut_subproof(&sub2to6, |sub1| {
        let line2 = sub1.add_premise(p("~Q"));
        let sub3to5 = sub1.add_subproof();
        sub1.with_mut_subproof(&sub3to5, |sub2| {
            let line3 = sub2.add_premise(p("P"));
            let line4 = sub2.add_step(Justification(p("Q"), RuleM::ImpElim, vec![line1.clone(), line3.clone()], vec![]));
            let line5 = sub2.add_step(Justification(p("_|_"), RuleM::ContradictionIntro, vec![line2.clone(), line4.clone()], vec![]));
        }).unwrap();
        let line6 = sub1.add_step(Justification(p("~P"), RuleM::NotIntro, vec![], vec![sub3to5]));
    }).unwrap();
    let line7 = prf.add_step(Justification(p("~Q -> ~P"), RuleM::ImpIntro, vec![], vec![sub2to6]));
    prf
}

type P = libaris::proofs::pooledproof::PooledProof<Hlist![Expr]>;
let concrete_proof = contraposition_demo::<P>();
```

# Mutating existing lines of a proof

```
#[macro_use] extern crate frunk;
use libaris::expression::Expr;
use libaris::parser::parse_unwrap as p;
use libaris::proofs::{Proof, Justification, pooledproof::PooledProof};
use libaris::rules::RuleM;

let mut prf = PooledProof::<Hlist![Expr]>::new();
let r1 = prf.add_premise(p("A"));
let r2 = prf.add_step(Justification(p("A & A"), RuleM::AndIntro, vec![r1.clone()], vec![]));
assert_eq!(format!("{}", prf),
"1:\t| A
\t| ----------
2:\t| (A ∧ A); SharedChecks(Inl(AndIntro)); Inl(PremKey(0))
");
prf.with_mut_premise(&r1, |e| { *e = p("B"); }).unwrap();
prf.with_mut_step(&r2, |j| { j.0 = p("A | B"); j.1 = RuleM::OrIntro; }).unwrap();
assert_eq!(format!("{}", prf),
"1:\t| B
\t| ----------
2:\t| (A ∨ B); SharedChecks(Inl(OrIntro)); Inl(PremKey(0))
");

```

# The `with_mut_{premise,step,subproof}` methods and soundness
The subproof pointer in `with_mut_subproof`, if returned directly, could be invalidated by calls to add_subproof on the same proof object, leading to UAF.
This is prevented by the fact that the lifetime parameter of the subproof reference cannot occur in A:

```compile_fail,E0495
#[macro_use] extern crate frunk;
use libaris::proofs::{Proof, pooledproof::PooledProof};
use libaris::expression::Expr;
fn should_fail_with_lifetime_error() {
    let mut p = PooledProof::<Hlist![Expr]>::new();
    let r = p.add_subproof();
    let s = p.with_mut_subproof(&r, |x| x);
}
```

This is a similar trick to the rank-2 type of `runST` in Haskell used to prevent the phantom state from escaping.
*/

use super::*;
use frunk::coproduct::*;
use std::collections::HashSet;
use std::fmt::{Display, Formatter};
use std::hash::Hash;
use std::ops::Range;

#[cfg(test)]
mod proof_tests;

/// treeproof represents a proof as a vec of premises and a vec of non-premise lines with either rule applications (with explicit line numbers for dependencies) or nested subproofs, with hooks for annotations
/// # Tradeoffs
/// ## Pros
/// - rules out invalid nesting
/// - close to the presentation of the system in the textbook
/// ## Cons
/// - many operations of interest {adding and deleting lines, recomputing line numbers for dependencies} are O(n)
/// - abandoned, subproofs are not yet implemented
pub mod treeproof;
#[cfg(test)] mod treeproof_tests;

/// pooledproof represents proofs as ZipperVec's of indices into three seperate pools of {premises, justifications, subproofs}
/// # Tradeoffs
/// ## Pros
/// - it's the de-facto standard, and supports all the operations on the Proof trait well
/// - allows approximate O(1) inserts/removals of nearby lines (gracefully degrading to O(n) if edits are made at opposite ends of the proof)
/// - references to pool entries are never invalidated, which means that moving lines around can be efficiently supported
/// - looking up steps by line number should be O(1), which enables efficiently checking individual rules, elimintating the need for a check-cache
///     (if I understand correctly, the current java version caches rule checks, which might lead to soundness bugs if the cache isn't invalidated correctly)
/// ## Cons
/// - potentially more implementation effort than treeproof
/// - nontrivial mapping between references and line numbers might require some additional design
/// ## Mixed
/// - splicing in a subproof is O(|subproof|), while in treeproof that's O(1), but forces an O(|wholeproof|) line recalculation
pub mod pooledproof;

/// java_shallow_proof only represents things from edu.rpi.aris.rules.Premise, for the purpose of shimming into RuleT::check
/// # Tradeoffs
/// ## Pros
/// - Trivial to construct from Claim objects
/// ## Cons
/// - Doesn't support most operations
/// - Doesn't handle binding structure, so can't be used for first order logic, only prepositional logic
pub mod java_shallow_proof;

/// A LinedProof is a wrapper around another proof type that adds lines and strings, for interfacing with the GUI
pub mod lined_proof;

/// xml_interop contains functions for loading a proof from an xml reader
pub mod xml_interop;

/// DisplayIndented gives a convention for passing around state to pretty printers
/// it is intended that objects that implement this implement display as:
/// `fn fmt(&self, fmt: &mut Formatter) -> std::result::Result<(), std::fmt::Error> { self.display_indented(fmt, 1, &mut 1) }`
/// but this cannot be given as a blanket implementation due to coherence rules
pub trait DisplayIndented {
    fn display_indented(&self, fmt: &mut Formatter, indent: usize, linecount: &mut usize) -> Result<(), std::fmt::Error>;
}

/// libaris::proofs::Proof is the core trait for working with proofs.
pub trait Proof: Sized {
    type Reference: Clone + Eq + Hash;
    type SubproofReference: Clone + Eq + Hash;
    type Subproof: Proof<Reference=Self::Reference, SubproofReference=Self::SubproofReference, Subproof=Self::Subproof>;
    fn new() -> Self;
    fn top_level_proof(&self) -> &Self::Subproof;
    fn lookup(&self, r: Self::Reference) -> Option<Coprod!(Expr, Justification<Expr, Self::Reference, Self::SubproofReference>)>;
    fn lookup_subproof(&self, r: Self::SubproofReference) -> Option<Self::Subproof>;
    fn with_mut_premise<A, F: FnOnce(&mut Expr) -> A>(&mut self, r: &Self::Reference, f: F) -> Option<A>;
    fn with_mut_step<A, F: FnOnce(&mut Justification<Expr, Self::Reference, Self::SubproofReference>) -> A>(&mut self, r: &Self::Reference, f: F) -> Option<A>;
    fn with_mut_subproof<A, F: FnOnce(&mut Self::Subproof) -> A>(&mut self, r: &Self::SubproofReference, f: F) -> Option<A>;
    fn add_premise(&mut self, e: Expr) -> Self::Reference;
    fn add_subproof(&mut self) -> Self::SubproofReference;
    fn add_step(&mut self, just: Justification<Expr, Self::Reference, Self::SubproofReference>) -> Self::Reference;
    fn add_premise_relative(&mut self, e: Expr, r: Self::Reference, after: bool) -> Self::Reference;
    fn add_subproof_relative(&mut self, r: Self::Reference, after: bool) -> Self::SubproofReference;
    fn add_step_relative(&mut self, just: Justification<Expr, Self::Reference, Self::SubproofReference>, r: Self::Reference, after: bool) -> Self::Reference;
    fn remove_line(&mut self, r: Self::Reference);
    fn remove_subproof(&mut self, r: Self::SubproofReference);
    fn premises(&self) -> Vec<Self::Reference>;
    fn lines(&self) -> Vec<Coprod!(Self::Reference, Self::SubproofReference)>;
    fn parent_of_line(&self, r: &Coprod!(Self::Reference, Self::SubproofReference)) -> Option<Self::SubproofReference>;
    fn verify_line(&self, r: &Self::Reference) -> Result<(), ProofCheckError<Self::Reference, Self::SubproofReference>>;

    fn lookup_expr(&self, r: Self::Reference) -> Option<Expr> {
        self.lookup(r).and_then(|x: Coprod!(Expr, Justification<Expr, Self::Reference, Self::SubproofReference>)| x.fold(hlist![|x| Some(x), |x: Justification<_, _, _>| Some(x.0)]))
    }
    fn lookup_expr_or_die(&self, r: Self::Reference) -> Result<Expr, ProofCheckError<Self::Reference, Self::SubproofReference>> {
        self.lookup_expr(r.clone()).ok_or(ProofCheckError::LineDoesNotExist(r))
    }
    fn lookup_subproof_or_die(&self, r: Self::SubproofReference) -> Result<Self::Subproof, ProofCheckError<Self::Reference, Self::SubproofReference>> {
        self.lookup_subproof(r.clone()).ok_or(ProofCheckError::SubproofDoesNotExist(r))
    }
    fn direct_lines(&self) -> Vec<<Self as Proof>::Reference> {
        self.lines().iter().filter_map(|x| Coproduct::uninject::<<Self as Proof>::Reference, _>(x.clone()).ok()).collect()
    }
    fn exprs(&self) -> Vec<<Self as Proof>::Reference> {
        self.premises().iter().cloned().chain(self.direct_lines()).collect()
    }
    fn contained_justifications(&self, include_premises: bool) -> HashSet<Self::Reference> {
        let mut ret = self.lines().into_iter().filter_map(|x| x.fold(hlist![
            |r: Self::Reference| Some(vec![r].into_iter().collect()),
            |r: Self::SubproofReference| self.lookup_subproof(r).map(|sub| sub.contained_justifications(include_premises)),
        ])).fold(HashSet::new(), |mut x, y| { x.extend(y.into_iter()); x });
        if include_premises {
            ret.extend(self.premises());
        }
        ret
    }
    fn transitive_dependencies(&self, line: Self::Reference) -> HashSet<Self::Reference> {
        // TODO: cycle detection
        let mut stack: Vec<Coprod!(Self::Reference, Self::SubproofReference)> = vec![Coproduct::inject(line)];
        let mut result = HashSet::new();
        while let Some(r) = stack.pop() {
            use frunk::Coproduct::{Inl, Inr};
            match r {
                Inl(r) => {
                    result.insert(r.clone());
                    if let Some(Justification(_, _, deps, sdeps)) = self.lookup(r).and_then(|x| Coproduct::uninject(x).ok()) {
                        stack.extend(deps.into_iter().map(|x| Coproduct::inject(x)));
                        stack.extend(sdeps.into_iter().map(|x| Coproduct::inject(x)));
                    }
                },
                Inr(Inl(r)) => {
                    if let Some(sub) = self.lookup_subproof(r) {
                        stack.extend(sub.lines());
                    }
                },
                Inr(Inr(void)) => match void {},
            }
        }
        result
    }
    fn depth_of_line(&self, r: &Coprod!(Self::Reference, Self::SubproofReference)) -> usize {
        let mut result = 0;
        let mut current = r.clone();
        while let Some(parent) = self.parent_of_line(&current) {
            result += 1;
            current = Coproduct::inject(parent);
        }
        result
    }
}

/// A Justification struct represents a step in the proof.
/// It contains an expression, a rule indicating why that expression is justified, and references to previous lines/subproofs for validating the rule.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Justification<T, R, S>(pub T, pub Rule, pub Vec<R>, pub Vec<S>);

impl<T, R, S> Justification<T, R, S> {
    fn map0<U, F: FnOnce(T) -> U>(self, f: F) -> Justification<U, R, S> {
        Justification(f(self.0), self.1, self.2, self.3)
    }
}

pub trait JustificationExprDisplay { fn fmt_expr(&self, fmt: &mut Formatter) -> std::fmt::Result; }
impl JustificationExprDisplay for Expr { fn fmt_expr(&self, fmt: &mut Formatter) -> std::fmt::Result { write!(fmt, "{}", self) } }
impl<Tail> JustificationExprDisplay for frunk::HCons<Expr, Tail> { fn fmt_expr(&self, fmt: &mut Formatter) -> std::fmt::Result { write!(fmt, "{}", self.head) } }

impl<T: JustificationExprDisplay, R: std::fmt::Debug, S: std::fmt::Debug> DisplayIndented for Justification<T, R, S> {
    fn display_indented(&self, fmt: &mut Formatter, indent: usize, linecount: &mut usize) -> std::result::Result<(), std::fmt::Error> {
        let &Justification(expr, rule, deps, sdeps) = &self;
        write!(fmt, "{}:\t", linecount)?;
        *linecount += 1;
        for _ in 0..indent { write!(fmt, "| ")?; }
        expr.fmt_expr(fmt)?;
        write!(fmt, "; {:?}; ", rule)?;
        for (i, dep) in deps.iter().enumerate() {
            write!(fmt, "{:?}", dep)?;
            if i != deps.len()-1 { write!(fmt, ", ")?; }
        }
        if deps.len() > 0 && sdeps.len() > 0 {
            write!(fmt, "; ")?;
        }
        for (i, dep) in sdeps.iter().enumerate() {
            write!(fmt, "{:?}", dep)?;
            if i != sdeps.len()-1 { write!(fmt, ", ")?; }
        }
        write!(fmt, "\n")
    }
}


#[derive(Clone, Debug, PartialEq, Eq)]
pub struct LineAndIndent { pub line: usize, pub indent: usize }
