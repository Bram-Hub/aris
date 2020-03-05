use super::*;
use frunk::Coproduct;

#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub struct JavaShallowProof(pub Vec<Expr>);

impl Proof for JavaShallowProof {
    type Reference = Expr;
    type SubproofReference = Self;
    type Subproof = Self;
    fn new() -> Self {
        JavaShallowProof(vec![])
    }
    fn top_level_proof(&self) -> &Self {
        self
    }
    fn lookup(&self, r: Self::Reference) -> Option<Coprod!(Expr, Justification<Expr, Self::Reference, Self::SubproofReference>)> {
        Some(Coproduct::inject(r))
    }
    fn lookup_subproof(&self, r: Self::SubproofReference) -> Option<Self> {
        Some(r)
    }
    fn with_mut_subproof<A, F: FnOnce(&mut Self::Subproof) -> A>(&mut self, _: &Self::SubproofReference, _: F) -> Option<A> {
        unimplemented!()
    }
    fn add_premise(&mut self, e: Expr) -> Self::Reference {
        e
    }
    fn add_subproof(&mut self) -> Self::SubproofReference {
        JavaShallowProof(vec![])
    }
    fn add_step(&mut self, just: Justification<Expr, Self::Reference, Self::SubproofReference>) -> Self::Reference {
        just.0
    }
    fn add_premise_relative(&mut self, e: Expr, _: Self::Reference, _: bool) -> Self::Reference {
        self.add_premise(e)
    }
    fn add_subproof_relative(&mut self, _: Self::Reference, _: bool) -> Self::SubproofReference {
        self.add_subproof()
    }
    fn add_step_relative(&mut self, just: Justification<Expr, Self::Reference, Self::SubproofReference>, _: Self::Reference, _: bool) -> Self::Reference {
        self.add_step(just)
    }
    fn remove_line(&mut self, _: Self::Reference) {}
    fn remove_subproof(&mut self, _: Self::SubproofReference) {}
    fn premises(&self) -> Vec<Self::Reference> {
        if self.0.len() >= 1 { vec![self.0[0].clone()] } else { vec![] }
    }
    fn lines(&self) -> Vec<Coprod!(Self::Reference, Self::SubproofReference)> {
        (1..self.0.len()).map(|i| Coproduct::Inl(self.0[i].clone())).collect()
    }
    fn parent_of_line(&self, _: &Coprod!(Self::Reference, Self::SubproofReference)) -> Option<Self::SubproofReference> { unimplemented!() }
    fn verify_line(&self, r: &Self::Reference) -> Result<(), ProofCheckError<Self::Reference, Self::SubproofReference>> {
        use self::Coproduct::{Inl, Inr};
        match self.lookup(r.clone()) {
            None => Err(ProofCheckError::LineDoesNotExist(r.clone())),
            Some(Inl(_)) => Ok(()), // premises are always valid
            Some(Inr(Inl(Justification(conclusion, rule, deps, sdeps)))) => rule.check(self, conclusion, deps, sdeps),
            Some(Inr(Inr(void))) => match void {},
        }
    }
}
