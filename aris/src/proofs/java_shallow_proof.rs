use super::*;
use frunk::Coproduct;

#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub struct JavaShallowProof(pub Vec<Expr>);

impl Proof for JavaShallowProof {
    type PremiseReference = Expr;
    type JustificationReference = Expr;
    type SubproofReference = Self;
    type Subproof = Self;
    fn new() -> Self {
        JavaShallowProof(vec![])
    }
    fn top_level_proof(&self) -> &Self {
        self
    }
    fn lookup_premise(&self, r: &Self::PremiseReference) -> Option<Expr> {
        Some(r.clone())
    }
    fn lookup_step(&self, r: &Self::JustificationReference) -> Option<Justification<Expr, PJRef<Self>, Self::SubproofReference>> {
        Some(Justification(r.clone(), RuleM::Reit, vec![], vec![]))
    }
    fn lookup_subproof(&self, r: &Self::SubproofReference) -> Option<Self> {
        Some(r.clone())
    }
    fn with_mut_premise<A, F: FnOnce(&mut Expr) -> A>(&mut self, _: &Self::PremiseReference, _: F) -> Option<A> {
        unimplemented!()
    }
    fn with_mut_step<A, F: FnOnce(&mut Justification<Expr, PJRef<Self>, Self::SubproofReference>) -> A>(&mut self, _: &Self::JustificationReference, _: F) -> Option<A> {
        unimplemented!()
    }
    fn with_mut_subproof<A, F: FnOnce(&mut Self::Subproof) -> A>(&mut self, _: &Self::SubproofReference, _: F) -> Option<A> {
        unimplemented!()
    }
    fn add_premise(&mut self, e: Expr) -> Self::PremiseReference {
        e
    }
    fn add_subproof(&mut self) -> Self::SubproofReference {
        JavaShallowProof(vec![])
    }
    fn add_step(&mut self, just: Justification<Expr, PJRef<Self>, Self::SubproofReference>) -> Self::JustificationReference {
        just.0
    }
    fn add_premise_relative(&mut self, e: Expr, _: &Self::PremiseReference, _: bool) -> Self::PremiseReference {
        self.add_premise(e)
    }
    fn add_subproof_relative(&mut self, _: &JSRef<Self>, _: bool) -> Self::SubproofReference {
        self.add_subproof()
    }
    fn add_step_relative(&mut self, just: Justification<Expr, PJRef<Self>, Self::SubproofReference>, _: &JSRef<Self>, _: bool) -> Self::JustificationReference {
        self.add_step(just)
    }
    fn remove_line(&mut self, _: &PJRef<Self>) {}
    fn remove_subproof(&mut self, _: &Self::SubproofReference) {}
    fn premises(&self) -> Vec<Self::PremiseReference> {
        if !self.0.is_empty() { vec![self.0[0].clone()] } else { vec![] }
    }
    fn lines(&self) -> Vec<JSRef<Self>> {
        (1..self.0.len()).map(|i| Coproduct::Inl(self.0[i].clone())).collect()
    }
    fn parent_of_line(&self, _: &PJSRef<Self>) -> Option<Self::SubproofReference> { unimplemented!() }
    fn verify_line(&self, r: &PJRef<Self>) -> Result<(), ProofCheckError<PJRef<Self>, Self::SubproofReference>> {
        use self::Coproduct::{Inl, Inr};
        match self.lookup_pj(r) {
            None => Err(ProofCheckError::LineDoesNotExist(r.clone())),
            Some(Inl(_)) => Ok(()), // premises are always valid
            Some(Inr(Inl(Justification(conclusion, rule, deps, sdeps)))) => rule.check(self, conclusion, deps, sdeps),
            Some(Inr(Inr(void))) => match void {},
        }
    }
}
