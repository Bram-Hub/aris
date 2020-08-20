/*!
Data structures for representing natural deduction style proofs

# Description
This module houses different proof representations with different performance/simplicity tradeoffs.

# Concrete proof types
You probably want `type P1 = aris::proofs::pooledproof::PooledProof<Hlist![Expr]>;` if you want to interactively mutate a proof, and
`type P2 = aris::proofs::lined_proof::LinedProof<P1>;` if you want to display line numbers for a finalized proof.

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
#[macro_use] extern crate frunk_core;
use aris::expr::Expr;
use aris::proofs::xml_interop::proof_from_xml;
let data = &include_bytes!("../../example-proofs/propositional_logic_arguments_for_proofs_ii_problem_10.bram")[..];
type P = aris::proofs::pooledproof::PooledProof<Hlist![Expr]>;
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
#[macro_use] extern crate frunk_core;
use frunk_core::coproduct::Coproduct;
use aris::expr::Expr;
use aris::proofs::{Proof, Justification, pooledproof::PooledProof};
use aris::rules::RuleM;
fn contraposition_demo<P: Proof>() -> P {
    use aris::parser::parse_unwrap as p;
    let mut prf = P::new();
    let line1 = prf.add_premise(p("P -> Q"));
    let sub2to6 = prf.add_subproof();
    prf.with_mut_subproof(&sub2to6, |sub1| {
        let line2 = sub1.add_premise(p("~Q"));
        let sub3to5 = sub1.add_subproof();
        sub1.with_mut_subproof(&sub3to5, |sub2| {
            let line3 = sub2.add_premise(p("P"));
            let line4 = sub2.add_step(Justification(p("Q"), RuleM::ImpElim, vec![Coproduct::inject(line1.clone()), Coproduct::inject(line3.clone())], vec![]));
            let line5 = sub2.add_step(Justification(p("_|_"), RuleM::ContradictionIntro, vec![Coproduct::inject(line2.clone()), Coproduct::inject(line4.clone())], vec![]));
        }).unwrap();
        let line6 = sub1.add_step(Justification(p("~P"), RuleM::NotIntro, vec![], vec![sub3to5]));
    }).unwrap();
    let line7 = prf.add_step(Justification(p("~Q -> ~P"), RuleM::ImpIntro, vec![], vec![sub2to6]));
    prf
}

type P = aris::proofs::pooledproof::PooledProof<Hlist![Expr]>;
let concrete_proof = contraposition_demo::<P>();
```

# Mutating existing lines of a proof

```
#[macro_use] extern crate frunk_core;
use frunk_core::coproduct::Coproduct;
use aris::expr::Expr;
use aris::parser::parse_unwrap as p;
use aris::proofs::{Proof, Justification, pooledproof::PooledProof};
use aris::rules::RuleM;

let mut prf = PooledProof::<Hlist![Expr]>::new();
let r1 = prf.add_premise(p("A"));
let r2 = prf.add_step(Justification(p("A & A"), RuleM::AndIntro, vec![Coproduct::inject(r1.clone())], vec![]));
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
#[macro_use] extern crate frunk_core;
use aris::proofs::{Proof, pooledproof::PooledProof};
use aris::expr::Expr;
fn should_fail_with_lifetime_error() {
    let mut p = PooledProof::<Hlist![Expr]>::new();
    let r = p.add_subproof();
    let s = p.with_mut_subproof(&r, |x| x);
}
```

This is a similar trick to the rank-2 type of `runST` in Haskell used to prevent the phantom state from escaping.
*/

use crate::expr::Expr;
use crate::rules::ProofCheckError;
use crate::rules::Rule;

use std::collections::HashSet;
use std::hash::Hash;

use frunk_core::coproduct::Coproduct;
use frunk_core::hlist;
use frunk_core::Coprod;

#[cfg(test)]
mod proof_tests;

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
    fn display_indented(
        &self,
        fmt: &mut std::fmt::Formatter,
        indent: usize,
        linecount: &mut usize,
    ) -> Result<(), std::fmt::Error>;
}

pub type PJRef<P> = Coprod!(
    <P as Proof>::PremiseReference,
    <P as Proof>::JustificationReference
);
pub type JSRef<P> = Coprod!(
    <P as Proof>::JustificationReference,
    <P as Proof>::SubproofReference
);
pub type PJSRef<P> = Coprod!(
    <P as Proof>::PremiseReference,
    <P as Proof>::JustificationReference,
    <P as Proof>::SubproofReference
);

pub fn js_to_pjs<P: Proof>(js: JSRef<P>) -> PJSRef<P> {
    js.fold(hlist![|x| Coproduct::inject(x), |x| Coproduct::inject(x)])
}
pub fn pj_to_pjs<P: Proof>(pj: PJRef<P>) -> PJSRef<P> {
    pj.fold(hlist![|x| Coproduct::inject(x), |x| Coproduct::inject(x)])
}

/// aris::proofs::Proof is the core trait for working with proofs.
pub trait Proof: Sized {
    type PremiseReference: Clone + Eq + Ord + Hash;
    type JustificationReference: Clone + Eq + Ord + Hash;
    type SubproofReference: Clone + Eq + Ord + Hash;
    type Subproof: Proof<
        PremiseReference = Self::PremiseReference,
        JustificationReference = Self::JustificationReference,
        SubproofReference = Self::SubproofReference,
        Subproof = Self::Subproof,
    >;
    fn new() -> Self;
    fn top_level_proof(&self) -> &Self::Subproof;
    fn lookup_premise(&self, r: &Self::PremiseReference) -> Option<Expr>;
    fn lookup_step(
        &self,
        r: &Self::JustificationReference,
    ) -> Option<Justification<Expr, PJRef<Self>, Self::SubproofReference>>;
    fn lookup_subproof(&self, r: &Self::SubproofReference) -> Option<Self::Subproof>;
    fn with_mut_premise<A, F: FnOnce(&mut Expr) -> A>(
        &mut self,
        r: &Self::PremiseReference,
        f: F,
    ) -> Option<A>;
    fn with_mut_step<
        A,
        F: FnOnce(&mut Justification<Expr, PJRef<Self>, Self::SubproofReference>) -> A,
    >(
        &mut self,
        r: &Self::JustificationReference,
        f: F,
    ) -> Option<A>;
    fn with_mut_subproof<A, F: FnOnce(&mut Self::Subproof) -> A>(
        &mut self,
        r: &Self::SubproofReference,
        f: F,
    ) -> Option<A>;
    fn add_premise(&mut self, e: Expr) -> Self::PremiseReference;
    fn add_subproof(&mut self) -> Self::SubproofReference;
    fn add_step(
        &mut self,
        just: Justification<Expr, PJRef<Self>, Self::SubproofReference>,
    ) -> Self::JustificationReference;
    fn add_premise_relative(
        &mut self,
        e: Expr,
        r: &Self::PremiseReference,
        after: bool,
    ) -> Self::PremiseReference;
    fn add_subproof_relative(&mut self, r: &JSRef<Self>, after: bool) -> Self::SubproofReference;
    fn add_step_relative(
        &mut self,
        just: Justification<Expr, PJRef<Self>, Self::SubproofReference>,
        r: &JSRef<Self>,
        after: bool,
    ) -> Self::JustificationReference;
    fn remove_line(&mut self, r: &PJRef<Self>);
    fn remove_subproof(&mut self, r: &Self::SubproofReference);
    fn premises(&self) -> Vec<Self::PremiseReference>;
    fn lines(&self) -> Vec<JSRef<Self>>;
    fn parent_of_line(&self, r: &PJSRef<Self>) -> Option<Self::SubproofReference>;
    fn verify_line(
        &self,
        r: &PJRef<Self>,
    ) -> Result<(), ProofCheckError<PJRef<Self>, Self::SubproofReference>>;

    fn lookup_expr(&self, r: &PJRef<Self>) -> Option<Expr> {
        r.clone()
            .fold(hlist![|pr| self.lookup_premise(&pr), |jr| self
                .lookup_step(&jr)
                .map(|x| x.0)])
    }
    fn lookup_expr_or_die(
        &self,
        r: &PJRef<Self>,
    ) -> Result<Expr, ProofCheckError<PJRef<Self>, Self::SubproofReference>> {
        self.lookup_expr(r)
            .ok_or_else(|| ProofCheckError::LineDoesNotExist(r.clone()))
    }
    fn lookup_premise_or_die(
        &self,
        r: &Self::PremiseReference,
    ) -> Result<Expr, ProofCheckError<PJRef<Self>, Self::SubproofReference>> {
        self.lookup_premise(r)
            .ok_or_else(|| ProofCheckError::LineDoesNotExist(Coproduct::inject(r.clone())))
    }
    fn lookup_justification_or_die(
        &self,
        r: &Self::JustificationReference,
    ) -> Result<
        Justification<Expr, PJRef<Self>, Self::SubproofReference>,
        ProofCheckError<PJRef<Self>, Self::SubproofReference>,
    > {
        self.lookup_step(r)
            .ok_or_else(|| ProofCheckError::LineDoesNotExist(Coproduct::inject(r.clone())))
    }
    fn lookup_pj(
        &self,
        r: &PJRef<Self>,
    ) -> Option<Coprod!(Expr, Justification<Expr, PJRef<Self>, Self::SubproofReference>)> {
        r.clone().fold(hlist![
            |pr| self.lookup_premise(&pr).map(Coproduct::inject),
            |jr| self.lookup_step(&jr).map(Coproduct::inject)
        ])
    }
    fn lookup_subproof_or_die(
        &self,
        r: &Self::SubproofReference,
    ) -> Result<Self::Subproof, ProofCheckError<PJRef<Self>, Self::SubproofReference>> {
        self.lookup_subproof(r)
            .ok_or_else(|| ProofCheckError::SubproofDoesNotExist(r.clone()))
    }
    fn direct_lines(&self) -> Vec<Self::JustificationReference> {
        self.lines()
            .iter()
            .filter_map(|x| Coproduct::uninject::<Self::JustificationReference, _>(x.clone()).ok())
            .collect()
    }
    fn exprs(&self) -> Vec<PJRef<Self>> {
        self.premises()
            .into_iter()
            .map(Coproduct::inject)
            .chain(self.direct_lines().into_iter().map(Coproduct::inject))
            .collect()
    }
    fn contained_justifications(&self, include_premises: bool) -> HashSet<PJRef<Self>> {
        let mut ret = self
            .lines()
            .into_iter()
            .filter_map(|x| {
                x.fold(hlist![
                    |r: Self::JustificationReference| Some(
                        vec![r].into_iter().map(Coproduct::inject).collect()
                    ),
                    |r: Self::SubproofReference| self
                        .lookup_subproof(&r)
                        .map(|sub| sub.contained_justifications(include_premises)),
                ])
            })
            .fold(HashSet::new(), |mut x, y| {
                x.extend(y.into_iter());
                x
            });
        if include_premises {
            ret.extend(self.premises().into_iter().map(Coproduct::inject));
        }
        ret
    }
    fn transitive_dependencies(&self, line: PJRef<Self>) -> HashSet<PJRef<Self>> {
        use frunk_core::coproduct::Coproduct::{Inl, Inr};
        // TODO: cycle detection
        let mut stack: Vec<PJSRef<Self>> = vec![pj_to_pjs::<Self>(line)];
        let mut result = HashSet::new();
        while let Some(r) = stack.pop() {
            match r {
                Inl(pr) => {
                    result.insert(Coproduct::inject(pr));
                }
                Inr(Inl(jr)) => {
                    result.insert(Coproduct::inject(jr.clone()));
                    if let Some(Justification(_, _, deps, sdeps)) = self.lookup_step(&jr) {
                        stack.extend(deps.into_iter().map(pj_to_pjs::<Self>));
                        stack.extend(sdeps.into_iter().map(Coproduct::inject));
                    }
                }
                Inr(Inr(Inl(sr))) => {
                    if let Some(sub) = self.lookup_subproof(&sr) {
                        stack.extend(sub.lines().into_iter().map(js_to_pjs::<Self>));
                    }
                }
                Inr(Inr(Inr(void))) => match void {},
            }
        }
        result
    }
    fn depth_of_line(&self, r: &PJSRef<Self>) -> usize {
        let mut result = 0;
        let mut current = r.clone();
        while let Some(parent) = self.parent_of_line(&current) {
            result += 1;
            current = Coproduct::inject(parent);
        }
        result
    }

    fn possible_deps_for_line(
        &self,
        r: &PJRef<Self>,
        deps: &mut HashSet<PJRef<Self>>,
        sdeps: &mut HashSet<Self::SubproofReference>,
    ) {
        // r1 can reference r2 if all of the following hold:
        // 1) r2 occurs before r1
        // 2) if r2 is a line, r2 cannot be deeper in a subproof (e.g. r2 must occur before r1 in the same subproof, or in one of the parent subproofs)
        // 3) if r2 is a subproof reference, r1 must be outside r2's subproof
        // we compute the set of lines satisfying these properties by walking backwards starting from the subproof that r1 is in, adding lines that respect these rules
        fn aux<P: Proof>(
            top: &P,
            sr: Option<P::SubproofReference>,
            r: &PJSRef<P>,
            deps: &mut HashSet<PJRef<P>>,
            sdeps: &mut HashSet<P::SubproofReference>,
        ) {
            use frunk_core::coproduct::Coproduct::{Inl, Inr};
            let prf = sr.and_then(|sr| Some((sr.clone(), top.lookup_subproof(&sr)?)));
            match prf {
                Some((sr, sub)) => {
                    for line in sub
                        .premises()
                        .into_iter()
                        .map(Coproduct::inject)
                        .chain(sub.lines().into_iter().map(js_to_pjs::<P>))
                    {
                        if line == r.clone() {
                            break; // don't traverse lines in the current subproof after the line we're considering, or any subproof transitively containing it
                        }
                        match line {
                            Inl(pr) => {
                                deps.insert(Coproduct::inject(pr));
                            } // premise in a subproof before the considered line
                            Inr(Inl(jr)) => {
                                deps.insert(Coproduct::inject(jr));
                            } // justification in a subproof before the considered line
                            Inr(Inr(Inl(sr))) => {
                                sdeps.insert(sr);
                            } // subproof before the current line, respects scope since we're not recursing into it
                            Inr(Inr(Inr(void))) => match void {},
                        }
                    }
                    aux(
                        top,
                        top.parent_of_line(&Coproduct::inject(sr.clone())),
                        &Coproduct::inject(sr),
                        deps,
                        sdeps,
                    )
                }
                None => {
                    // we've reached the top level
                    for line in top
                        .premises()
                        .into_iter()
                        .map(Coproduct::inject)
                        .chain(top.lines().into_iter().map(js_to_pjs::<P>))
                    {
                        if line == r.clone() {
                            break;
                        }
                        match line {
                            Inl(pr) => {
                                deps.insert(Coproduct::inject(pr));
                            }
                            Inr(Inl(jr)) => {
                                deps.insert(Coproduct::inject(jr));
                            }
                            Inr(Inr(Inl(sr))) => {
                                sdeps.insert(sr);
                            }
                            Inr(Inr(Inr(void))) => match void {},
                        }
                    }
                }
            }
        }

        aux(
            self,
            self.parent_of_line(&pj_to_pjs::<Self>(r.clone())),
            &pj_to_pjs::<Self>(r.clone()),
            deps,
            sdeps,
        );
    }
    fn can_reference_dep(
        &self,
        r1: &PJRef<Self>,
        r2: &Coprod!(PJRef<Self>, Self::SubproofReference),
    ) -> bool {
        use self::Coproduct::{Inl, Inr};
        let mut valid_deps = HashSet::new();
        let mut valid_sdeps = HashSet::new();
        self.possible_deps_for_line(r1, &mut valid_deps, &mut valid_sdeps);
        match r2 {
            Inl(lr) => valid_deps.contains(lr),
            Inr(Inl(sr)) => valid_sdeps.contains(sr),
            Inr(Inr(void)) => match *void {},
        }
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

pub trait JustificationExprDisplay {
    fn fmt_expr(&self, fmt: &mut std::fmt::Formatter) -> std::fmt::Result;
}
impl JustificationExprDisplay for Expr {
    fn fmt_expr(&self, fmt: &mut std::fmt::Formatter) -> std::fmt::Result {
        write!(fmt, "{}", self)
    }
}
impl<Tail> JustificationExprDisplay for frunk_core::hlist::HCons<Expr, Tail> {
    fn fmt_expr(&self, fmt: &mut std::fmt::Formatter) -> std::fmt::Result {
        write!(fmt, "{}", self.head)
    }
}

impl<T: JustificationExprDisplay, R: std::fmt::Debug, S: std::fmt::Debug> DisplayIndented
    for Justification<T, R, S>
{
    fn display_indented(
        &self,
        fmt: &mut std::fmt::Formatter,
        indent: usize,
        linecount: &mut usize,
    ) -> std::result::Result<(), std::fmt::Error> {
        let &Justification(expr, rule, deps, sdeps) = &self;
        write!(fmt, "{}:\t", linecount)?;
        *linecount += 1;
        for _ in 0..indent {
            write!(fmt, "| ")?;
        }
        expr.fmt_expr(fmt)?;
        write!(fmt, "; {:?}; ", rule)?;
        for (i, dep) in deps.iter().enumerate() {
            write!(fmt, "{:?}", dep)?;
            if i != deps.len() - 1 {
                write!(fmt, ", ")?;
            }
        }
        if !deps.is_empty() && !sdeps.is_empty() {
            write!(fmt, "; ")?;
        }
        for (i, dep) in sdeps.iter().enumerate() {
            write!(fmt, "{:?}", dep)?;
            if i != sdeps.len() - 1 {
                write!(fmt, ", ")?;
            }
        }
        writeln!(fmt)
    }
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct LineAndIndent {
    pub line: usize,
    pub indent: usize,
}
