use crate::expr::Expr;
use crate::proofs::Justification;
use crate::proofs::PjRef;
use crate::proofs::Proof;
use crate::rules::RuleM;
use crate::zipper_vec::ZipperVec;

use std::fmt::Debug;

use frunk_core::coproduct::Coproduct;

pub struct Line<P: Proof> {
    pub raw_expr: String,
    pub is_premise: bool,
    pub reference: PjRef<P>,
    pub subreference: Option<P::SubproofReference>, // None for toplevel lines
}

impl<P: Proof> Clone for Line<P> {
    fn clone(&self) -> Self {
        Line { raw_expr: self.raw_expr.clone(), is_premise: self.is_premise, reference: self.reference.clone(), subreference: self.subreference.clone() }
    }
}

impl<P: Proof> PartialEq for Line<P> {
    fn eq(&self, other: &Self) -> bool {
        self.raw_expr == other.raw_expr && self.is_premise == other.is_premise && self.reference == other.reference && self.subreference == other.subreference
    }
}

impl<P: Proof + Debug> Debug for Line<P>
where
    PjRef<P>: Debug,
    P::SubproofReference: Debug,
{
    fn fmt(&self, fmt: &mut std::fmt::Formatter) -> std::fmt::Result {
        fmt.debug_struct("Line").field("raw_expr", &self.raw_expr).field("is_premise", &self.is_premise).field("reference", &self.reference).field("subreference", &self.subreference).finish()
    }
}

impl<P: Proof> Line<P> {
    fn new(raw_expr: String, is_premise: bool, reference: PjRef<P>, subreference: Option<P::SubproofReference>) -> Self {
        Line { raw_expr, is_premise, reference, subreference }
    }
}

#[derive(Clone)]
pub struct LinedProof<P: Proof> {
    pub proof: P,
    pub lines: ZipperVec<Line<P>>,
}

impl<P: Proof + Debug> Debug for LinedProof<P>
where
    PjRef<P>: Debug,
    P::SubproofReference: Debug,
{
    fn fmt(&self, fmt: &mut std::fmt::Formatter) -> std::fmt::Result {
        fmt.debug_struct("LinedProof").field("proof", &self.proof).field("lines", &self.lines).finish()
    }
}

impl<P: Proof + Debug> Default for LinedProof<P>
where
    PjRef<P>: Debug,
    P::SubproofReference: Debug,
{
    fn default() -> Self {
        Self::new()
    }
}

impl<P: Proof + Debug> LinedProof<P>
where
    PjRef<P>: Debug,
    P::SubproofReference: Debug,
{
    pub fn new() -> Self {
        LinedProof { proof: P::new(), lines: ZipperVec::new() }
    }
    pub fn len(&self) -> usize {
        self.lines.len()
    }
    pub fn is_empty(&self) -> bool {
        self.len() == 0
    }
    pub fn from_proof(p: P) -> Self {
        fn aux<P: Proof>(p: &P::Subproof, ret: &mut LinedProof<P>, current_sub: Option<P::SubproofReference>) {
            use frunk_core::coproduct::Coproduct::{Inl, Inr};
            for prem in p.premises() {
                let e = p.lookup_premise(&prem).unwrap();
                ret.lines.push(Line::new(format!("{e}"), true, Inl(prem), current_sub.clone()));
            }
            for line in p.lines() {
                match line {
                    Inl(r) => {
                        let just = p.lookup_step(&r).unwrap();
                        ret.lines.push(Line::new(format!("{}", just.0), false, Inr(Inl(r)), current_sub.clone()));
                    }
                    Inr(Inl(s)) => {
                        let sub = p.lookup_subproof(&s).unwrap();
                        aux(&sub, ret, Some(s));
                    }
                    Inr(Inr(void)) => match void {},
                }
            }
        }
        let mut ret = Self::new();
        aux(p.top_level_proof(), &mut ret, None);
        ret.proof = p;
        ret
    }
    pub fn add_line(&mut self, i: usize, is_premise: bool, subproof_level: usize) {
        use frunk_core::coproduct::Coproduct::{Inl, Inr};
        println!("add_line {i:?} {is_premise:?} {subproof_level:?}");
        let const_true = Expr::Taut;
        let line: Option<Line<P>> = self.lines.get(i).cloned();
        match line {
            None => {
                let r = if is_premise { Inl(self.proof.add_premise(const_true)) } else { Inr(Inl(self.proof.add_step(Justification(const_true, RuleM::Reit, vec![], vec![])))) };
                self.lines.push(Line { raw_expr: "".into(), is_premise, reference: r, subreference: None });
            }
            Some(line) => {
                let r = match (is_premise, line.reference.clone()) {
                    (true, Inl(pr)) => Inl(self.proof.add_premise_relative(const_true, &pr, true)),
                    (false, Inr(Inl(jr))) => Inr(Inl(self.proof.add_step_relative(Justification(const_true, RuleM::Reit, vec![], vec![]), &Coproduct::inject(jr), true))),
                    (_, Inr(Inr(void))) => match void {},
                    (b, r) => panic!("LinedProof::add_line, is_premise was {b}, but the line reference was {r:?}"),
                };
                self.lines.insert_relative(Line { raw_expr: "".into(), is_premise, reference: r, subreference: None /* TODO */ }, &line, true);
            }
        };

        println!("{:?}", self.proof);
    }
    pub fn set_expr(&mut self, i: usize, text: String) {
        println!("set_expr {i:?} {text:?}");
    }
    pub fn move_cursor(&mut self, i: usize) {
        println!("move_cursor {i:?}");
        self.lines.move_cursor(i);
    }
    pub fn delete(&mut self, i: usize) {
        if (i >= 1 || self.proof.premises().len() > 1) && i < self.lines.len() {
            let line = self.lines.pop(i).unwrap();
            println!("Deleting {line:?}");
            self.proof.remove_line(&line.reference);
            if let Some(subreference) = line.subreference {
                if let Some(sub) = self.proof.lookup_subproof(&subreference) {
                    if sub.premises().len() + sub.lines().len() == 0 {
                        self.proof.remove_subproof(&subreference);
                    }
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    use crate::proofs::pooledproof::PooledProof;

    use frunk_core::HList;

    #[test]
    fn test_from_proof() {
        let (p, _, _) = crate::proofs::proof_tests::test_forallintro::<PooledProof<HList![Expr]>>();
        let mut lp = LinedProof::from_proof(p);
        println!("{:?}\n{}", lp, lp.proof);
        let mut f = |i: usize| {
            println!("Deleting {}:", i + 1);
            lp.delete(i);
            println!("{}", lp.proof);
        };
        f(1);
        f(3);
        f(6);
        f(3);
        f(0);
        f(1);
    }
}
