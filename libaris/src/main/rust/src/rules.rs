use super::*;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Rule {
// Prepositional inference rules
    Reit,
    AndIntro, AndElim,
    OrIntro, OrElim,
    ImpIntro, ImpElim,
    NotIntro, NotElim,
    ContradictionIntro, ContradictionElim,
// Predicate inference rules
    ForallIntro, ForallElim,
    ExistsIntro, ExistsElim,
// Equivalence rules
    DeMorgan,
}

pub enum RuleType { Inference, Equivalence }

impl Rule {
    pub fn get_depcount(&self) -> Option<(usize, usize)> /* (lines, subproofs) */ {
        use Rule::*;
        match self {
            Reit | AndElim | OrIntro | NotElim | ContradictionElim | ExistsIntro | DeMorgan => Some((1, 0)),
            ContradictionIntro | ImpElim | ForallElim => Some((2, 0)),
            NotIntro | ImpIntro | ForallIntro => Some((0, 1)),
            ExistsElim => Some((1, 1)),
            AndIntro | OrElim => None, // AndIntro and OrElim can have arbitrarily many conjuncts/disjuncts in one application
        }
    }
    pub fn check<P: Proof>(self, p: &P, expr: Expr, deps: Vec<P::Reference>, sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<P::Reference, P::SubproofReference>> {
        use ProofCheckError::*;
        match self {
            Rule::AndIntro => unimplemented!(),
            Rule::AndElim => {
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
            Rule::OrIntro => unimplemented!(),
            Rule::OrElim => unimplemented!(),
            Rule::ImpIntro => unimplemented!(),
            Rule::ImpElim => unimplemented!(),
            Rule::NotIntro => unimplemented!(),
            Rule::NotElim => unimplemented!(),
            Rule::ContradictionIntro => unimplemented!(),
            Rule::ContradictionElim => {
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
            Rule::ForallIntro => unimplemented!(),
            Rule::ForallElim => unimplemented!(),
            Rule::ExistsIntro => unimplemented!(),
            Rule::ExistsElim => unimplemented!(),
            Rule::Reit => unimplemented!(),
            Rule::DeMorgan => unimplemented!(),
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
    DoesNotOccur(Expr, Expr),
}
