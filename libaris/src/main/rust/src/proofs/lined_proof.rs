use super::*;
use std::fmt::Debug;

#[derive(Clone)]
pub struct Line<P: Proof> {
    pub raw_expr: String,
    pub is_premise: bool,
    pub reference: P::Reference,
    pub subreference: Option<P::SubproofReference>, // None for toplevel lines
}

impl<P: Proof+Debug> Debug for Line<P> where P::Reference: Debug, P::SubproofReference: Debug {
    fn fmt(&self, fmt: &mut std::fmt::Formatter) -> std::fmt::Result {
        fmt.debug_struct("Line")
            .field("raw_expr", &self.raw_expr)
            .field("is_premise", &self.is_premise)
            .field("reference", &self.reference)
            .field("subreference", &self.subreference)
            .finish()
    }
}

impl<P: Proof> Line<P> {
    fn new(raw_expr: String, is_premise: bool, reference: P::Reference, subreference: Option<P::SubproofReference>) -> Self {
        Line { raw_expr, is_premise, reference, subreference }
    }
}

#[derive(Clone)]
pub struct LinedProof<P: Proof> {
    pub proof: P,
    pub lines: ZipperVec<Line<P>>,
}

impl<P: Proof+Debug> Debug for LinedProof<P> where P::Reference: Debug, P::SubproofReference: Debug {
    fn fmt(&self, fmt: &mut std::fmt::Formatter) -> std::fmt::Result {
        fmt.debug_struct("LinedProof")
            .field("proof", &self.proof)
            .field("lines", &self.lines)
            .finish()
    }
}


impl<P: Proof+Debug> LinedProof<P> where P::Reference: Debug, P::SubproofReference: Debug {
    pub fn new() -> Self {
        LinedProof {
            proof: P::new(),
            lines: ZipperVec::new(),
        }
    }
    pub fn len(&self) -> usize {
        self.lines.len()
    }
    pub fn from_proof(p: P) -> Self {
        fn aux<P: Proof>(p: &P::Subproof, ret: &mut LinedProof<P>, current_sub: Option<P::SubproofReference>) {
            use frunk::Coproduct::{Inl, Inr};
            for prem in p.premises() {
                let e = p.lookup_expr(prem.clone()).unwrap();
                ret.lines.push(Line::new(format!("{}", e), true, prem, current_sub.clone()));
            }
            for line in p.lines() {
                match line {
                    Inl(r) => {
                        let e = p.lookup_expr(r.clone()).unwrap();
                        ret.lines.push(Line::new(format!("{}", e), false, r, current_sub.clone()));
                    },
                    Inr(Inl(s)) => {
                        let sub = p.lookup_subproof(s.clone()).unwrap();
                        aux(&sub, ret, Some(s));
                    },
                    Inr(Inr(void)) => match void {},
                }
            }
        }
        let mut ret = Self::new();
        aux(p.top_level_proof(), &mut ret, None);
        ret.proof = p;
        ret
    }
    pub fn delete(&mut self, i: usize) {
        if (i >= 1 || self.proof.premises().len() > 1) && i < self.lines.len() {
            let line = self.lines.pop(i).unwrap();
            println!("Deleting {:?}", line);
            self.proof.remove_line(line.reference.clone());
            if let Some(subreference) = line.subreference {
                if let Some(sub) = self.proof.lookup_subproof(subreference.clone()) {
                    if sub.premises().len() + sub.lines().len() == 0 {
                        self.proof.remove_subproof(subreference);
                    }
                }
            }
        }
    }
}

#[test]
fn test_from_proof() {
    let (p, _, _) = proof_tests::test_forallintro::<super::pooledproof::PooledProof<Hlist![Expr]>>();
    let mut lp = LinedProof::from_proof(p);
    println!("{:?}\n{}", lp, lp.proof);
    let mut f = |i: usize| { println!("Deleting {}:", i+1); lp.delete(i); println!("{}", lp.proof); };
    f(1);
    f(3);
    f(6);
    f(3);
    f(0);
    f(1);
}

