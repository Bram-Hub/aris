use super::*;
use frunk::Coproduct::{self, Inl, Inr};

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum PrepositionalInference {
    Reit,
    AndIntro, AndElim,
    OrIntro, OrElim,
    ImpIntro, ImpElim,
    NotIntro, NotElim,
    ContradictionIntro, ContradictionElim,
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum PredicateInference {
    ForallIntro, ForallElim,
    ExistsIntro, ExistsElim,
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Equivalence {
    DeMorgan,
}

/// The RuleT instance for SharedChecks does checking that is common to all the rules;
///  it should always be the outermost constructor of the Rule type alias.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct SharedChecks<T>(T);

pub type Rule = SharedChecks<Coprod!(PrepositionalInference, PredicateInference, Equivalence)>;

/// Conveniences for constructing rules of the appropriate type, primarily for testing.
/// The non-standard naming conventions here are because a module is being used to pretend to be an enum.
#[allow(non_snake_case)]
pub mod RuleM {
    #![allow(non_upper_case_globals)]
    use super::*;
    pub static Reit: Rule = SharedChecks(Inl(PrepositionalInference::Reit));
    pub static AndIntro: Rule = SharedChecks(Inl(PrepositionalInference::AndIntro));
    pub static AndElim: Rule = SharedChecks(Inl(PrepositionalInference::AndElim));
    pub static OrIntro: Rule = SharedChecks(Inl(PrepositionalInference::OrIntro));
    pub static OrElim: Rule = SharedChecks(Inl(PrepositionalInference::OrElim));
    pub static ImpIntro: Rule = SharedChecks(Inl(PrepositionalInference::ImpIntro));
    pub static ImpElim: Rule = SharedChecks(Inl(PrepositionalInference::ImpElim));
    pub static NotIntro: Rule = SharedChecks(Inl(PrepositionalInference::NotIntro));
    pub static NotElim: Rule = SharedChecks(Inl(PrepositionalInference::NotElim));
    pub static ContradictionIntro: Rule = SharedChecks(Inl(PrepositionalInference::ContradictionIntro));
    pub static ContradictionElim: Rule = SharedChecks(Inl(PrepositionalInference::ContradictionElim));

    pub static ForallIntro: Rule = SharedChecks(Inr(Inl(PredicateInference::ForallIntro)));
    pub static ForallElim: Rule = SharedChecks(Inr(Inl(PredicateInference::ForallElim)));
    pub static ExistsIntro: Rule = SharedChecks(Inr(Inl(PredicateInference::ExistsIntro)));
    pub static ExistsElim: Rule = SharedChecks(Inr(Inl(PredicateInference::ExistsElim)));

    pub static Demorgan: Rule = SharedChecks(Inr(Inr(Inl(Equivalence::DeMorgan))));
}

pub trait RuleT {
    fn num_deps(&self) -> Option<usize>;
    fn num_subdeps(&self) -> Option<usize>;
    fn check<P: Proof>(self, p: &P, expr: Expr, deps: Vec<P::Reference>, sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<P::Reference, P::SubproofReference>>;
}

impl<A: RuleT, B: RuleT> RuleT for Coproduct<A, B> {
    fn num_deps(&self) -> Option<usize> { match self { Inl(x) => x.num_deps(), Inr(x) => x.num_deps(), } }
    fn num_subdeps(&self) -> Option<usize> { match self { Inl(x) => x.num_subdeps(), Inr(x) => x.num_subdeps(), } }
    fn check<P: Proof>(self, p: &P, expr: Expr, deps: Vec<P::Reference>, sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<P::Reference, P::SubproofReference>> {
        match self { Inl(x) => x.check(p, expr, deps, sdeps), Inr(x) => x.check(p, expr, deps, sdeps), }
    }
}
impl RuleT for frunk::coproduct::CNil {
    fn num_deps(&self) -> Option<usize> { match *self {} }
    fn num_subdeps(&self) -> Option<usize> { match *self {} }
    fn check<P: Proof>(self, _p: &P, _expr: Expr, _deps: Vec<P::Reference>, _sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<P::Reference, P::SubproofReference>> {
        match self {}
    }
}

impl<T: RuleT> RuleT for SharedChecks<T> {
    fn num_deps(&self) -> Option<usize> { self.0.num_deps() }
    fn num_subdeps(&self) -> Option<usize> { self.0.num_subdeps() }
    fn check<P: Proof>(self, p: &P, expr: Expr, deps: Vec<P::Reference>, sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<P::Reference, P::SubproofReference>> {
        use ProofCheckError::*;
        if let Some(directs) = self.num_deps() {
            if deps.len() != directs {
                return Err(IncorrectDepCount(deps, directs));
            }
        }
        if let Some(subs) = self.num_subdeps() {
            if sdeps.len() != subs {
                return Err(IncorrectSubDepCount(sdeps, subs));
            }
        }
        // TODO: enforce that each subproof has exactly 1 premise
        self.0.check(p, expr, deps, sdeps)
    }
}

impl RuleT for PrepositionalInference {
    fn num_deps(&self) -> Option<usize> {
        use PrepositionalInference::*;
        match self {
            Reit | AndElim | OrIntro | NotElim | ContradictionElim => Some(1),
            ContradictionIntro | ImpElim => Some(2),
            NotIntro | ImpIntro => Some(0),
            AndIntro | OrElim => None, // AndIntro and OrElim can have arbitrarily many conjuncts/disjuncts in one application
        }
    }
    fn num_subdeps(&self) -> Option<usize> {
        use PrepositionalInference::*;
        match self {
            NotIntro | ImpIntro => Some(1),
            Reit | AndElim | OrIntro | NotElim | ContradictionElim | ContradictionIntro | ImpElim | AndIntro | OrElim => Some(0),
        }
    }
    fn check<P: Proof>(self, p: &P, expr: Expr, deps: Vec<P::Reference>, sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<P::Reference, P::SubproofReference>> {
        use ProofCheckError::*; use PrepositionalInference::*;
        match self {
            Reit => {
                if let Some(prem) = p.lookup_expr(deps[0].clone()) {
                    if prem == expr {
                        return Ok(());
                    } else {
                        return Err(DoesNotOccur(expr, prem.clone()));
                    }
                } else {
                    return Err(LineDoesNotExist(deps[0].clone()))
                }
            },
            AndIntro => unimplemented!(),
            AndElim => {
                if let Some(prem) = p.lookup_expr(deps[0].clone()) {
                    if let Expr::AssocBinop { symbol: ASymbol::And, ref exprs } = prem {
                        for e in exprs.iter() {
                            if e == &expr {
                                return Ok(());
                            }
                        }
                        // TODO: allow `A /\ B /\ C |- C /\ A /\ C`, etc
                        return Err(DoesNotOccur(expr, prem.clone()));
                    } else {
                        return Err(DepOfWrongForm("expected an and-expression".into()));
                    }
                } else {
                    return Err(LineDoesNotExist(deps[0].clone()))
                }
            },
            OrIntro => {
                if let Some(prem) = p.lookup_expr(deps[0].clone()) {
                    if let Expr::AssocBinop { symbol: ASymbol::Or, ref exprs } = expr {
                        for e in exprs.iter() {
                            if e == &prem {
                                return Ok(());
                            }
                        }
                        return Err(DoesNotOccur(prem, expr.clone()));
                    } else {
                        return Err(ConclusionOfWrongForm("expected an or-expression".into()));
                    }
                } else {
                    return Err(LineDoesNotExist(deps[0].clone()))
                }
            },
            OrElim => unimplemented!(),
            ImpIntro => unimplemented!(),
            ImpElim => unimplemented!(),
            NotIntro => unimplemented!(),
            NotElim => unimplemented!(),
            ContradictionIntro => unimplemented!(),
            ContradictionElim => {
                if let Some(prem) = p.lookup_expr(deps[0].clone()) {
                    if let Expr::Bottom = prem {
                        return Ok(());
                    } else {
                        return Err(DepOfWrongForm("premise should be bottom".into()));
                    }
                } else {
                    return Err(LineDoesNotExist(deps[0].clone()))
                }
            },
        }
    }
}

impl RuleT for PredicateInference {
    fn num_deps(&self) -> Option<usize> {
        use PredicateInference::*;
        match self {
            ExistsIntro | ExistsElim => Some(1),
            ForallElim => Some(2),
            ForallIntro => Some(0),
        }
    }
    fn num_subdeps(&self) -> Option<usize> {
        use PredicateInference::*;
        match self {
            ExistsIntro | ForallElim => Some(0),
            ForallIntro | ExistsElim => Some(1),
        }
    }
    fn check<P: Proof>(self, _p: &P, _expr: Expr, _deps: Vec<P::Reference>, _sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<P::Reference, P::SubproofReference>> {
        use ProofCheckError::*; use PredicateInference::*;
        match self {
            ForallIntro => unimplemented!(),
            ForallElim => unimplemented!(),
            ExistsIntro => unimplemented!(),
            ExistsElim => unimplemented!(),
        }
    }
}

impl RuleT for Equivalence {
    fn num_deps(&self) -> Option<usize> { Some(1) } // all equivalence rules rewrite a single statement
    fn num_subdeps(&self) -> Option<usize> { Some(0) }
    fn check<P: Proof>(self, _p: &P, _expr: Expr, _deps: Vec<P::Reference>, _sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<P::Reference, P::SubproofReference>> {
        use ProofCheckError::*; use Equivalence::*;
        match self {
            DeMorgan => unimplemented!(),
        }
    }
}

#[derive(Debug, PartialEq, Eq)]
pub enum ProofCheckError<R, S> {
    LineDoesNotExist(R),
    ReferencesLaterLine(LineAndIndent, usize),
    IncorrectDepCount(Vec<R>, usize),
    IncorrectSubDepCount(Vec<S>, usize),
    DepOfWrongForm(String),
    ConclusionOfWrongForm(String),
    DoesNotOccur(Expr, Expr),
}
