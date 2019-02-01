use super::*;
use std::collections::HashSet;
use frunk::Coproduct::{self, Inl, Inr};

pub mod java_rule;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum PrepositionalInference {
    Reit,
    AndIntro, AndElim,
    OrIntro, OrElim,
    ImpIntro, ImpElim,
    NotIntro, NotElim,
    ContradictionIntro, ContradictionElim,
    BiconditionalIntro, BiconditionalElim,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum PredicateInference {
    ForallIntro, ForallElim,
    ExistsIntro, ExistsElim,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Equivalence {
    DeMorgan, Association, Commutation, Idempotence, Distribution
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum RedundantPrepositionalInference {
    ModusTollens, HypotheticalSyllogism, ExcludedMiddle, ConstructiveDilemma
}

/// The RuleT instance for SharedChecks does checking that is common to all the rules;
///  it should always be the outermost constructor of the Rule type alias.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct SharedChecks<T>(T);

pub type Rule = SharedChecks<Coprod!(PrepositionalInference, PredicateInference, Equivalence, RedundantPrepositionalInference)>;

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
    pub static BiconditionalIntro: Rule = SharedChecks(Inl(PrepositionalInference::BiconditionalIntro));
    pub static BiconditionalElim: Rule = SharedChecks(Inl(PrepositionalInference::BiconditionalElim));

    pub static ForallIntro: Rule = SharedChecks(Inr(Inl(PredicateInference::ForallIntro)));
    pub static ForallElim: Rule = SharedChecks(Inr(Inl(PredicateInference::ForallElim)));
    pub static ExistsIntro: Rule = SharedChecks(Inr(Inl(PredicateInference::ExistsIntro)));
    pub static ExistsElim: Rule = SharedChecks(Inr(Inl(PredicateInference::ExistsElim)));

    pub static DeMorgan: Rule = SharedChecks(Inr(Inr(Inl(Equivalence::DeMorgan))));
    pub static Association: Rule = SharedChecks(Inr(Inr(Inl(Equivalence::Association))));
    pub static Commutation: Rule = SharedChecks(Inr(Inr(Inl(Equivalence::Commutation))));
    pub static Idempotence: Rule = SharedChecks(Inr(Inr(Inl(Equivalence::Idempotence))));
    pub static Distribution: Rule = SharedChecks(Inr(Inr(Inl(Equivalence::Distribution))));

    pub static ModusTollens: Rule = SharedChecks(Inr(Inr(Inr(Inl(RedundantPrepositionalInference::ModusTollens)))));
    pub static HypotheticalSyllogism: Rule = SharedChecks(Inr(Inr(Inr(Inl(RedundantPrepositionalInference::HypotheticalSyllogism)))));
    pub static ExcludedMiddle: Rule = SharedChecks(Inr(Inr(Inr(Inl(RedundantPrepositionalInference::ExcludedMiddle)))));
    pub static ConstructiveDilemma: Rule = SharedChecks(Inr(Inr(Inr(Inl(RedundantPrepositionalInference::ConstructiveDilemma)))));

}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum RuleClassification {
    Introduction, Elimination, Equivalence, Inference, Predicate
}

pub trait RuleT {
    fn get_name(&self) -> String;
    fn get_classifications(&self) -> HashSet<RuleClassification>;
    fn num_deps(&self) -> Option<usize>;
    fn num_subdeps(&self) -> Option<usize>;
    fn check<P: Proof>(self, p: &P, expr: Expr, deps: Vec<P::Reference>, sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<P::Reference, P::SubproofReference>>;
}

impl<A: RuleT, B: RuleT> RuleT for Coproduct<A, B> {
    fn get_name(&self) -> String { match self { Inl(x) => x.get_name(), Inr(x) => x.get_name(), } }
    fn get_classifications(&self) -> HashSet<RuleClassification> { match self { Inl(x) => x.get_classifications(), Inr(x) => x.get_classifications(), } }
    fn num_deps(&self) -> Option<usize> { match self { Inl(x) => x.num_deps(), Inr(x) => x.num_deps(), } }
    fn num_subdeps(&self) -> Option<usize> { match self { Inl(x) => x.num_subdeps(), Inr(x) => x.num_subdeps(), } }
    fn check<P: Proof>(self, p: &P, expr: Expr, deps: Vec<P::Reference>, sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<P::Reference, P::SubproofReference>> {
        match self { Inl(x) => x.check(p, expr, deps, sdeps), Inr(x) => x.check(p, expr, deps, sdeps), }
    }
}
impl RuleT for frunk::coproduct::CNil {
    fn get_name(&self) -> String { match *self {} }
    fn get_classifications(&self) -> HashSet<RuleClassification> { match *self {} }
    fn num_deps(&self) -> Option<usize> { match *self {} }
    fn num_subdeps(&self) -> Option<usize> { match *self {} }
    fn check<P: Proof>(self, _p: &P, _expr: Expr, _deps: Vec<P::Reference>, _sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<P::Reference, P::SubproofReference>> {
        match self {}
    }
}

impl<T: RuleT> RuleT for SharedChecks<T> {
    fn get_name(&self) -> String { self.0.get_name() }
    fn get_classifications(&self) -> HashSet<RuleClassification> { self.0.get_classifications() }
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
    fn get_name(&self) -> String {
        use PrepositionalInference::*;
        match self {
            Reit => "Reiteration",
            AndIntro => "∧ Introduction",
            AndElim => "∧ Elimination",
            OrIntro => "∨ Introduction",
            OrElim => "∨ Elimination",
            ImpIntro => "→ Introduction",
            ImpElim => "→ Elimination",
            NotIntro => "¬ Introduction",
            NotElim => "¬ Elimination",
            ContradictionIntro => "⊥ Introduction",
            ContradictionElim => "⊥ Elimination",
            BiconditionalIntro => "↔ Introduction",
            BiconditionalElim => "↔ Elimination",
        }.into()
    }
    fn get_classifications(&self) -> HashSet<RuleClassification> {
        use RuleClassification::*; use PrepositionalInference::*;
        let mut ret = [Inference].iter().cloned().collect::<HashSet<_>>();
        match self {
            Reit => (),
            AndIntro | OrIntro | ImpIntro | NotIntro | ContradictionIntro | BiconditionalIntro => { ret.insert(Introduction); },
            AndElim | OrElim | ImpElim | NotElim | ContradictionElim | BiconditionalElim => { ret.insert(Elimination); },
        }
        ret
    }
    fn num_deps(&self) -> Option<usize> {
        use PrepositionalInference::*;
        match self {
            Reit | AndElim | OrIntro | NotElim | ContradictionElim => Some(1),
            ContradictionIntro | ImpElim | BiconditionalElim => Some(2),
            NotIntro | ImpIntro | BiconditionalIntro => Some(0),
            AndIntro | OrElim => None, // AndIntro and OrElim can have arbitrarily many conjuncts/disjuncts in one application
        }
    }
    fn num_subdeps(&self) -> Option<usize> {
        use PrepositionalInference::*;
        match self {
            NotIntro | ImpIntro => Some(1),
            Reit | AndElim | OrIntro | NotElim | ContradictionElim | ContradictionIntro | ImpElim | AndIntro | BiconditionalElim => Some(0),
            OrElim | BiconditionalIntro => None,
        }
    }
    fn check<P: Proof>(self, p: &P, conclusion: Expr, deps: Vec<P::Reference>, sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<P::Reference, P::SubproofReference>> {
        use ProofCheckError::*; use PrepositionalInference::*;
        match self {
            Reit => {
                let prem = p.lookup_expr_or_die(deps[0].clone())?;
                if prem == conclusion {
                    return Ok(());
                } else {
                    return Err(DoesNotOccur(conclusion, prem.clone()));
                }
            },
            AndIntro => {
                if let Expr::AssocBinop { symbol: ASymbol::And, ref exprs } = conclusion {
                    // ensure each dep appears in exprs
                    for d in deps.iter() {
                        let e = p.lookup_expr_or_die(d.clone())?;
                        if !exprs.iter().find(|x| x == &&e).is_some() {
                            return Err(DoesNotOccur(e, conclusion.clone()));
                        }
                    }
                    // ensure each expr has a dep
                    for e in exprs {
                        if deps.iter().find(|&d| p.lookup_expr(d.clone()).map(|de| &de == e).unwrap_or(false)).is_none() {
                            return Err(DepDoesNotExist(e.clone()));
                        }
                    }
                    return Ok(());
                } else {
                    return Err(DepOfWrongForm("expected an and-expression".into()));
                }
            },
            AndElim => {
                let prem = p.lookup_expr_or_die(deps[0].clone())?;
                if let Expr::AssocBinop { symbol: ASymbol::And, ref exprs } = prem {
                    for e in exprs.iter() {
                        if e == &conclusion {
                            return Ok(());
                        }
                    }
                    // TODO: allow `A /\ B /\ C |- C /\ A /\ C`, etc
                    return Err(DoesNotOccur(conclusion, prem.clone()));
                } else {
                    return Err(DepOfWrongForm("expected an and-expression".into()));
                }
            },
            OrIntro => {
                let prem = p.lookup_expr_or_die(deps[0].clone())?;
                if let Expr::AssocBinop { symbol: ASymbol::Or, ref exprs } = conclusion {
                    if exprs.iter().find(|e| e == &&prem).is_none() {
                        return Err(DoesNotOccur(prem, conclusion.clone()));
                    }
                    return Ok(());
                } else {
                    return Err(ConclusionOfWrongForm("expected an or-expression".into()));
                }
            },
            OrElim => unimplemented!(),
            ImpIntro => unimplemented!(),
            ImpElim => {
                let mut prems = vec![];
                prems.push(p.lookup_expr_or_die(deps[0].clone())?);
                prems.push(p.lookup_expr_or_die(deps[1].clone())?);

                for (i, j) in [(0,1), (1,0)].iter().cloned(){
                    if let Expr::Binop{symbol: BSymbol::Implies, ref left, ref right} = prems[i]{
                        //bad case, p -> q, q therefore --doesn't matter, nothing can be said
                        //given q
                        if **right == prems[j] {
                            return Err(DepOfWrongForm("Expected form of p -> q, p therefore q".into()));
                        }
                        //bad case, p -> q, a therefore --doesn't matter, nothing can be said
                        //with a
                        if **left != prems[j] {
                            return Err(DoesNotOccur(prems[i].clone(), prems[j].clone()));
                        }

                        //bad case, p -> q, p therefore a which does not follow
                        if **right != conclusion{
                            return Err(ConclusionOfWrongForm("Expected the antecedent of conditional as conclusion".into()));
                        }
                        //good case, p -> q, p therefore q
                        if **left == prems[j] && **right == conclusion{
                            return Ok(());
                        }
                    }
                }
                return Err(DepOfWrongForm("No conditional in dependencies".into()));

            },
            NotIntro => unimplemented!(),
            NotElim => {
                let prem = p.lookup_expr_or_die(deps[0].clone())?;
                if let Expr::Unop{symbol: USymbol::Not, ref operand} = prem{
                    if let Expr::Unop{symbol: USymbol::Not, ref operand} = **operand{
                        if **operand == conclusion {
                            return Ok(());
                        }

                        return Err(ConclusionOfWrongForm("Double negated expression in premise not found in conclusion".into()));
                    }else{
                        return Err(DepOfWrongForm("Expected a double-negation".into()));
                    }
                }else{
                    return Err(DepOfWrongForm("Expected a negation-expression".into()));
                }
            },
            ContradictionIntro => {
                if let Expr::Bottom = conclusion {
                    let mut prems = vec![];
                    prems.push(p.lookup_expr_or_die(deps[0].clone())?);
                    prems.push(p.lookup_expr_or_die(deps[1].clone())?);
                    for (i, j) in [(0, 1), (1, 0)].iter().cloned() {
                        if let Expr::Unop { symbol: USymbol::Not, ref operand } = prems[i] {
                            if **operand == prems[j] {
                                return Ok(());
                            }
                        }
                    }
                    return Err(DepOfWrongForm("expected one dep to be negation of other".into()));
                } else {
                    return Err(ConclusionOfWrongForm("conclusion should be bottom".into()));
                }
            },
            ContradictionElim => {
                let prem = p.lookup_expr_or_die(deps[0].clone())?;
                if let Expr::Bottom = prem {
                    return Ok(());
                } else {
                    return Err(DepOfWrongForm("premise should be bottom".into()));
                }
            },
            BiconditionalIntro => unimplemented!(),
            BiconditionalElim => {
                let mut prems = vec![];
                prems.push(p.lookup_expr_or_die(deps[0].clone())?);
                prems.push(p.lookup_expr_or_die(deps[1].clone())?);

                for (i, j) in [(0,1), (1,0)].iter().cloned() {
                    if let Expr::AssocBinop { symbol: ASymbol::Bicon, ref exprs } = prems[i] {
                        if exprs.iter().find(|x| x == &&prems[j]).is_none() {
                            return Err(DoesNotOccur(prems[j].clone(), prems[i].clone()));
                        }
                        if exprs.iter().find(|x| x == &&conclusion).is_none() {
                            return Err(DoesNotOccur(conclusion.clone(), prems[i].clone()));
                        }
                        return Ok(());
                    }
                }
                return Err(DepOfWrongForm("at least one dep should be a biconditional".into()));
            },
        }
    }
}

impl RuleT for PredicateInference {
    fn get_name(&self) -> String {
        use PredicateInference::*;
        match self {
            ForallIntro => "∀ Introduction",
            ForallElim => "∀ Elimination",
            ExistsIntro => "∃ Introduction",
            ExistsElim => "∃ Elimination",
        }.into()
    }
    fn get_classifications(&self) -> HashSet<RuleClassification> {
        use RuleClassification::*; use PredicateInference::*;
        let mut ret = [Inference, RuleClassification::Predicate].iter().cloned().collect::<HashSet<_>>();
        match self {
            ForallIntro | ExistsIntro => ret.insert(Introduction),
            ForallElim | ExistsElim => ret.insert(Elimination),
        };
        ret
    }
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
    fn get_name(&self) -> String {
        use Equivalence::*;
        match self {
            DeMorgan => "DeMorgan",
            Association => "Association",
            Commutation => "Commutation",
            Idempotence => "Idempotence",
            Distribution => "Distribution",
        }.into()
    }
    fn get_classifications(&self) -> HashSet<RuleClassification> {
        [RuleClassification::Equivalence].iter().cloned().collect()
    }
    fn num_deps(&self) -> Option<usize> { Some(1) } // all equivalence rules rewrite a single statement
    fn num_subdeps(&self) -> Option<usize> { Some(0) }
    fn check<P: Proof>(self, _p: &P, _expr: Expr, _deps: Vec<P::Reference>, _sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<P::Reference, P::SubproofReference>> {
        use ProofCheckError::*; use Equivalence::*;
        match self {
            DeMorgan => unimplemented!(),
            Association => unimplemented!(),
            Commutation => unimplemented!(),
            Idempotence => unimplemented!(),
            Distribution => unimplemented!(),
        }
    }
}

impl RuleT for RedundantPrepositionalInference {
    fn get_name(&self) -> String {
        use RedundantPrepositionalInference::*;
        match self {
            ModusTollens => "ModusTollens",
            HypotheticalSyllogism => "HypotheticalSyllogism",
            ExcludedMiddle => "ExcludedMiddle",
            ConstructiveDilemma => "ConstructiveDilemma",
        }.into()
    }
    fn get_classifications(&self) -> HashSet<RuleClassification> {
        [RuleClassification::Inference].iter().cloned().collect()
    }
    fn num_deps(&self) -> Option<usize> { unimplemented!() }
    fn num_subdeps(&self) -> Option<usize> { unimplemented!() }
    fn check<P: Proof>(self, _p: &P, _expr: Expr, _deps: Vec<P::Reference>, _sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<P::Reference, P::SubproofReference>> { unimplemented!() }
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
    DepDoesNotExist(Expr),
}
