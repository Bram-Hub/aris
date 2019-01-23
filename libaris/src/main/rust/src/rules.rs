use super::*;

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
    pub fn get_depcount(&self) -> Option<(usize, usize)> /* (lines, subproofs) */ {
        use Rule::*;
        match self {
            Reit | AndElim | OrIntro | NotElim | BotElim | ExistsIntro => Some((1, 0)),
            BotIntro | ImpElim | ForallElim => Some((2, 0)),
            NotIntro | ImpIntro | ForallIntro => Some((0, 1)),
            ExistsElim => Some((1, 1)),
            AndIntro | OrElim => None, // AndIntro and OrElim can have arbitrarily many conjuncts/disjuncts in one application
        }
    }
    pub fn check<P: Proof>(self, p: &P, expr: Expr, deps: Vec<P::Reference>) -> Result<(), ProofCheckError<P::Reference>> {
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
            Rule::BotIntro => unimplemented!(),
            Rule::BotElim => unimplemented!(),
            Rule::ForallIntro => unimplemented!(),
            Rule::ForallElim => unimplemented!(),
            Rule::ExistsIntro => unimplemented!(),
            Rule::ExistsElim => unimplemented!(),
            Rule::Reit => unimplemented!(),
        }
    }
}
