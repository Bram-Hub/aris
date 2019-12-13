use super::*;
use std::collections::{HashMap, HashSet};
use frunk::Coproduct::{self, Inl, Inr};
use petgraph::algo::tarjan_scc;
use petgraph::graphmap::DiGraphMap;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum PrepositionalInference {
    Reit,
    AndIntro, AndElim,
    OrIntro, OrElim,
    ImpIntro, ImpElim,
    NotIntro, NotElim,
    ContradictionIntro, ContradictionElim,
    BiconditionalIntro, BiconditionalElim,
    EquivalenceIntro, EquivalenceElim,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum PredicateInference {
    ForallIntro, ForallElim,
    ExistsIntro, ExistsElim,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Equivalence {
    DeMorgan, Association, Commutation, Idempotence, Distribution, 
    DoubleNegation, Complement, Identity, Annihilation, Inverse, Absorption,
    Reduction, Adjacency
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ConditionalEquivalence {
    Complement, Identity, Annihilation, Implication, BiImplication, Contraposition,
    Currying
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum RedundantPrepositionalInference {
    ModusTollens, HypotheticalSyllogism, ExcludedMiddle, ConstructiveDilemma
}

/// The RuleT instance for SharedChecks does checking that is common to all the rules;
///  it should always be the outermost constructor of the Rule type alias.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct SharedChecks<T>(T);

pub type Rule = SharedChecks<Coprod!(PrepositionalInference, PredicateInference,
    Equivalence, ConditionalEquivalence, RedundantPrepositionalInference)>;

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
    pub static EquivalenceIntro: Rule = SharedChecks(Inl(PrepositionalInference::EquivalenceIntro));
    pub static EquivalenceElim: Rule = SharedChecks(Inl(PrepositionalInference::EquivalenceElim));

    pub static ForallIntro: Rule = SharedChecks(Inr(Inl(PredicateInference::ForallIntro)));
    pub static ForallElim: Rule = SharedChecks(Inr(Inl(PredicateInference::ForallElim)));
    pub static ExistsIntro: Rule = SharedChecks(Inr(Inl(PredicateInference::ExistsIntro)));
    pub static ExistsElim: Rule = SharedChecks(Inr(Inl(PredicateInference::ExistsElim)));

    pub static DeMorgan: Rule = SharedChecks(Inr(Inr(Inl(Equivalence::DeMorgan))));
    pub static Association: Rule = SharedChecks(Inr(Inr(Inl(Equivalence::Association))));
    pub static Commutation: Rule = SharedChecks(Inr(Inr(Inl(Equivalence::Commutation))));
    pub static Idempotence: Rule = SharedChecks(Inr(Inr(Inl(Equivalence::Idempotence))));
    pub static Distribution: Rule = SharedChecks(Inr(Inr(Inl(Equivalence::Distribution))));
    pub static DoubleNegation: Rule = SharedChecks(Inr(Inr(Inl(Equivalence::DoubleNegation))));
    pub static Complement: Rule = SharedChecks(Inr(Inr(Inl(Equivalence::Complement))));
    pub static Identity: Rule = SharedChecks(Inr(Inr(Inl(Equivalence::Identity))));
    pub static Annihilation: Rule = SharedChecks(Inr(Inr(Inl(Equivalence::Annihilation))));
    pub static Inverse: Rule = SharedChecks(Inr(Inr(Inl(Equivalence::Inverse))));
    pub static Absorption: Rule = SharedChecks(Inr(Inr(Inl(Equivalence::Absorption))));
    pub static Reduction: Rule = SharedChecks(Inr(Inr(Inl(Equivalence::Reduction))));
    pub static Adjacency: Rule = SharedChecks(Inr(Inr(Inl(Equivalence::Adjacency))));

    pub static CondComplement: Rule = SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::Complement)))));
    pub static CondIdentity: Rule = SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::Identity)))));
    pub static CondAnnihilation: Rule = SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::Annihilation)))));
    pub static Implication: Rule = SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::Implication)))));
    pub static BiImplication: Rule = SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::BiImplication)))));
    pub static Contraposition: Rule = SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::Contraposition)))));
    pub static Currying: Rule = SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::Currying)))));
    
    pub static ModusTollens: Rule = SharedChecks(Inr(Inr(Inr(Inr(Inl(RedundantPrepositionalInference::ModusTollens))))));
    pub static HypotheticalSyllogism: Rule = SharedChecks(Inr(Inr(Inr(Inr(Inl(RedundantPrepositionalInference::HypotheticalSyllogism))))));
    pub static ExcludedMiddle: Rule = SharedChecks(Inr(Inr(Inr(Inr(Inl(RedundantPrepositionalInference::ExcludedMiddle))))));
    pub static ConstructiveDilemma: Rule = SharedChecks(Inr(Inr(Inr(Inr(Inl(RedundantPrepositionalInference::ConstructiveDilemma))))));

    pub fn from_serialized_name(name: &str) -> Option<Rule> {
        Some(match name {
            "REITERATION" => RuleM::Reit,
            "CONJUNCTION" => RuleM::AndIntro,
            "SIMPLIFICATION" => RuleM::AndElim,
            "ADDITION" => RuleM::OrIntro,
            "DISJUNCTIVE_SYLLOGISM" => RuleM::OrElim,
            "CONDITIONAL_PROOF" => RuleM::ImpIntro,
            "MODUS_PONENS" => RuleM::ImpElim,
            "PROOF_BY_CONTRADICTION" => RuleM::NotIntro,
            "DOUBLENEGATION" => RuleM::NotElim,
            "CONTRADICTION" => RuleM::ContradictionIntro,
            "PRINCIPLE_OF_EXPLOSION" => RuleM::ContradictionElim,
            "BICONDITIONAL_INTRO" => RuleM::BiconditionalIntro,
            "BICONDITIONAL_ELIM" => RuleM::BiconditionalElim,
            "EQUIVALENCE_INTRO" => RuleM::EquivalenceIntro,
            "EQUIVALENCE_ELIM" => RuleM::EquivalenceElim,

            "UNIVERSAL_GENERALIZATION" => RuleM::ForallIntro,
            "UNIVERSAL_INSTANTIATION" => RuleM::ForallElim,
            "EXISTENTIAL_GENERALIZATION" => RuleM::ExistsIntro,
            "EXISTENTIAL_INSTANTIATION" => RuleM::ExistsElim,

            "MODUS_TOLLENS" => RuleM::ModusTollens,
            "HYPOTHETICAL_SYLLOGISM" => RuleM::HypotheticalSyllogism,
            "EXCLUDED_MIDDLE" => RuleM::ExcludedMiddle,
            "CONSTRUCTIVE_DILEMMA" => RuleM::ConstructiveDilemma,

            "ASSOCIATION" => RuleM::Association,
            "COMMUTATION" => RuleM::Commutation,
            "IDEMPOTENCE" => RuleM::Idempotence,
            "DE_MORGAN" => RuleM::DeMorgan,
            "DISTRIBUTION" => RuleM::Distribution,
            "DOUBLENEGATION_EQUIV" => RuleM::DoubleNegation,
            "COMPLEMENT" => RuleM::Complement,
            "IDENTITY" => RuleM::Identity,
            "ANNIHILATION" => RuleM::Annihilation,
            "INVERSE" => RuleM::Inverse,
            "ABSORPTION" => RuleM::Absorption,
            "REDUCTION" => RuleM::Reduction,
            "ADJACENCY" => RuleM::Adjacency,
            
            "CONDITIONAL_COMPLEMENT" => RuleM::CondComplement,
            "CONDITIONAL_IDENTITY" => RuleM::CondIdentity,
            "CONDITIONAL_ANNIHILATION" => RuleM::CondAnnihilation,
            "IMPLICATION" => RuleM::Implication,
            "BI_IMPLICATION" => RuleM::BiImplication,
            "CONTRAPOSITION" => RuleM::Contraposition,
            "CURRYING" => RuleM::Currying,
            _ => { return None },
        })
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum RuleClassification {
    Introduction, Elimination, Equivalence, Inference, Predicate
}

pub trait RuleT {
    /// get_name gets the name of the rule for display in the GUI
    fn get_name(&self) -> String;
    /// get_classifications is used to tell the GUI which panes/right click menus to put the rule under
    fn get_classifications(&self) -> HashSet<RuleClassification>;
    /// num_deps is used by SharedChecks to ensure that the right number of dependencies are provided, None indicates that no checking is done (e.g. for variadic rules)
    fn num_deps(&self) -> Option<usize>;
    /// num_subdeps is used by SharedChecks to ensure that the right number of subproof dependencies are provided, None indicates that no checking is done (e.g. for variadic rules)
    fn num_subdeps(&self) -> Option<usize>;
    /// check that expr is a valid conclusion of the rule given the corresponding lists of dependencies and subproof dependencies, returning Ok(()) on success, and an error to display in the GUI on failure
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
            EquivalenceIntro => "≡ Introduction",
            EquivalenceElim => "≡ Elimination",
        }.into()
    }
    fn get_classifications(&self) -> HashSet<RuleClassification> {
        use RuleClassification::*; use PrepositionalInference::*;
        let mut ret = [Inference].iter().cloned().collect::<HashSet<_>>();
        match self {
            Reit => (),
            AndIntro | OrIntro | ImpIntro | NotIntro | ContradictionIntro | BiconditionalIntro | EquivalenceIntro => { ret.insert(Introduction); },
            AndElim | OrElim | ImpElim | NotElim | ContradictionElim | BiconditionalElim | EquivalenceElim => { ret.insert(Elimination); },
        }
        ret
    }
    fn num_deps(&self) -> Option<usize> {
        use PrepositionalInference::*;
        match self {
            Reit | AndElim | OrIntro | OrElim | NotElim | ContradictionElim => Some(1),
            ContradictionIntro | ImpElim | BiconditionalElim | EquivalenceElim => Some(2),
            NotIntro | ImpIntro => Some(0),
            AndIntro | BiconditionalIntro | EquivalenceIntro => None, // AndIntro can have arbitrarily many conjuncts in one application
        }
    }
    fn num_subdeps(&self) -> Option<usize> {
        use PrepositionalInference::*;
        match self {
            NotIntro | ImpIntro => Some(1),
            Reit | AndElim | OrIntro | NotElim | ContradictionElim | ContradictionIntro | ImpElim | AndIntro | BiconditionalElim | EquivalenceElim => Some(0),
            OrElim | BiconditionalIntro | EquivalenceIntro => None,
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
                            return Err(DepDoesNotExist(e.clone(), false));
                        }
                    }
                    return Ok(());
                } else {
                    return Err(ConclusionOfWrongForm(expression_builders::assocplaceholder(ASymbol::And)));
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
                    return Err(DepDoesNotExist(expression_builders::assocplaceholder(ASymbol::And), true));
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
                    return Err(ConclusionOfWrongForm(expression_builders::assocplaceholder(ASymbol::Or)));
                }
            },
            OrElim => {
                let prem = p.lookup_expr_or_die(deps[0].clone())?;
                if let Expr::AssocBinop { symbol: ASymbol::Or, ref exprs } = prem {
                    let sproofs = sdeps.into_iter().map(|r| p.lookup_subproof_or_die(r.clone())).collect::<Result<Vec<_>,_>>()?;
                    // if not all the subproofs have lines whose expressions contain the conclusion, return an error
                    if !sproofs.iter().all(|sproof| {
                            sproof.lines().into_iter().filter_map(|x| x.get::<P::Reference,_>().and_then(|y| p.lookup_expr(y.clone())).map(|y| y.clone())).find(|c| *c == conclusion).is_some()
                        }) {
                        return Err(DepDoesNotExist(conclusion.clone(), false));
                    }
                    if let Some(e) = exprs.iter().find(|&e| {
                        !sproofs.iter().any(|sproof| {
                            sproof.premises().into_iter().next().and_then(|r| p.lookup_expr(r)).map(|x| x == *e) == Some(true)
                            })
                        }) {
                        return Err(DepDoesNotExist(e.clone(), false));
                    }
                    return Ok(());
                } else {
                    return Err(DepDoesNotExist(expression_builders::assocplaceholder(ASymbol::Or), true));
                }
            },
            ImpIntro => {
                let sproof = p.lookup_subproof_or_die(sdeps[0].clone())?;
                // TODO: allow generalized premises
                assert_eq!(sproof.premises().len(), 1);
                if let Expr::Binop{symbol: BSymbol::Implies, ref left, ref right} = conclusion {
                    let prem = sproof.premises().into_iter().map(|r| p.lookup_expr_or_die(r)).collect::<Result<Vec<Expr>,_>>()?;
                    if **left != prem[0] {
                        return Err(DoesNotOccur(*left.clone(), prem[0].clone()));
                    }
                    let conc = sproof.lines().into_iter().filter_map(|x| x.get::<P::Reference,_>().map(|y| y.clone()))
                        .map(|r| p.lookup_expr_or_die(r.clone())).collect::<Result<Vec<Expr>,_>>()?;
                    if conc.iter().find(|c| *c == &**right).is_none() {
                        return Err(DepDoesNotExist(*right.clone(), false));
                    }
                    return Ok(());
                } else {
                    return Err(ConclusionOfWrongForm(expression_builders::binopplaceholder(BSymbol::Implies)));
                }
            },
            ImpElim => {
                let prem1 =p.lookup_expr_or_die(deps[0].clone())?;
                let prem2 =p.lookup_expr_or_die(deps[1].clone())?;
                either_order(&prem1, &prem2, |i, j| {
                    if let Expr::Binop{symbol: BSymbol::Implies, ref left, ref right} = i{
                        //bad case, p -> q, a therefore --doesn't matter, nothing can be said
                        //with a
                        if **left != *j {
                            return Err(DoesNotOccur(i.clone(), j.clone()));
                        }

                        //bad case, p -> q, p therefore a which does not follow
                        if **right != conclusion{
                            return Err(DoesNotOccur(conclusion.clone(), *right.clone()));
                        }

                        //good case, p -> q, p therefore q
                        if **left == *j && **right == conclusion{
                            return Ok(Some(()));
                        }
                    }
                    Ok(None)
                }, || Err(DepDoesNotExist(expression_builders::binopplaceholder(BSymbol::Implies), true)))

            },
            NotIntro => {
                let sproof = p.lookup_subproof_or_die(sdeps[0].clone())?;
                // TODO: allow generalized premises
                assert_eq!(sproof.premises().len(), 1);
                if let Expr::Unop { symbol: USymbol::Not, ref operand } = conclusion {
                    let prem = sproof.premises().into_iter().map(|r| p.lookup_expr_or_die(r)).collect::<Result<Vec<Expr>,_>>()?;
                    if **operand != prem[0] {
                        return Err(DoesNotOccur(*operand.clone(), prem[0].clone()));
                    }
                    let conc = sproof.lines().into_iter().filter_map(|x| x.get::<P::Reference,_>().map(|y| y.clone()))
                        .map(|r| p.lookup_expr_or_die(r.clone())).collect::<Result<Vec<Expr>,_>>()?;
                    if conc.iter().find(|x| **x == Expr::Contradiction).is_none() {
                        return Err(DepDoesNotExist(Expr::Contradiction, false));
                    }
                    return Ok(());
                } else {
                    return Err(ConclusionOfWrongForm({use expression_builders::*; not(var("_")) }));
                }
            },
            NotElim => {
                let prem = p.lookup_expr_or_die(deps[0].clone())?;
                if let Expr::Unop{symbol: USymbol::Not, ref operand} = prem {
                    if let Expr::Unop{symbol: USymbol::Not, ref operand} = **operand {
                        if **operand == conclusion {
                            return Ok(());
                        }
                        return Err(ConclusionOfWrongForm({use expression_builders::*; not(not(var("_"))) }));
                    } else {
                        return Err(DepDoesNotExist({use expression_builders::*; not(not(var("_"))) }, true));
                    }
                } else {
                    return Err(DepDoesNotExist({use expression_builders::*; not(not(var("_"))) }, true));
                }
            },
            ContradictionIntro => {
                if let Expr::Contradiction = conclusion {
                    let prem1 = p.lookup_expr_or_die(deps[0].clone())?;
                    let prem2 = p.lookup_expr_or_die(deps[1].clone())?;
                    either_order(&prem1, &prem2, |i, j| {
                        if let Expr::Unop { symbol: USymbol::Not, ref operand } = i {
                            if **operand == *j {
                                return Ok(Some(()));
                            }
                        }
                       Ok(None)
                    }, || Err(Other("Expected one dependency to be the negation of the other.".into())))
                } else {
                    return Err(ConclusionOfWrongForm(Expr::Contradiction));
                }
            },
            ContradictionElim => {
                let prem = p.lookup_expr_or_die(deps[0].clone())?;
                if let Expr::Contradiction = prem {
                    return Ok(());
                } else {
                    return Err(DepOfWrongForm(prem.clone(), Expr::Contradiction));
                }
            },
            BiconditionalElim => {
                let prem1 = p.lookup_expr_or_die(deps[0].clone())?;
                let prem2 = p.lookup_expr_or_die(deps[1].clone())?;
                either_order(&prem1, &prem2, |i, j| {
                    if let Expr::AssocBinop { symbol: ASymbol::Bicon, ref exprs } = i {
                        let mut s = HashSet::new();
                        if let Expr::AssocBinop { symbol: ASymbol::Bicon, ref exprs } = j {
                            s.extend(exprs.iter().cloned());
                        } else {
                            s.insert(j.clone());
                        }
                        for prem in s.iter() {
                            if exprs.iter().find(|x| x == &prem).is_none() {
                                return Err(DoesNotOccur(prem.clone(), i.clone()));
                            }
                        }
                        let terms = exprs.iter().filter(|x| !s.contains(x)).cloned().collect::<Vec<_>>();
                        let expected = if terms.len() == 1 { terms[0].clone() } else { expression_builders::assocbinop(ASymbol::Bicon, &terms[..]) };
                        // TODO: maybe commutativity
                        if conclusion != expected {
                            return Err(DoesNotOccur(conclusion.clone(), expected));
                        }
                        return Ok(Some(()));
                    }
                  Ok(None)
                }, || Err(DepDoesNotExist(expression_builders::assocplaceholder(ASymbol::Bicon), true)))
            },
            EquivalenceIntro | BiconditionalIntro => {
                let sym = if let EquivalenceIntro = self { ASymbol::Equiv } else { ASymbol::Bicon };
                if let Expr::AssocBinop { symbol, ref exprs } = conclusion { if sym == symbol {
                    if let BiconditionalIntro = self { if exprs.len() != 2 {
                        use expression_builders::var;
                        return Err(ConclusionOfWrongForm(Expr::AssocBinop { symbol: ASymbol::Bicon, exprs: vec![var("_"), var("_")] }))
                    }}
                    let prems = deps.into_iter().map(|r| p.lookup_expr_or_die(r)).collect::<Result<Vec<Expr>, _>>()?;
                    let sproofs = sdeps.into_iter().map(|r| p.lookup_subproof_or_die(r)).collect::<Result<Vec<_>, _>>()?;
                    let mut slab = HashMap::new();
                    let mut counter = 0;
                    let next: &mut dyn FnMut() -> _ = &mut || {
                        counter += 1;
                        counter
                    };
                    let mut g = DiGraphMap::new();
                    for prem in prems.iter() {
                        match prem {
                            Expr::AssocBinop { symbol, ref exprs } if &sym == symbol => {
                                for e1 in exprs.iter() {
                                    for e2 in exprs.iter() {
                                        slab.entry(e1.clone()).or_insert_with(|| next());
                                        slab.entry(e2.clone()).or_insert_with(|| next());
                                        g.add_edge(slab[e1], slab[e2], ());
                                    }
                                }
                            },
                            Expr::Binop { symbol: BSymbol::Implies, ref left, ref right } => {
                                slab.entry(*left.clone()).or_insert_with(|| next());
                                slab.entry(*right.clone()).or_insert_with(|| next());
                                g.add_edge(slab[left], slab[right], ());
                            },
                            _ => return Err(OneOf(vec![
                                DepOfWrongForm(prem.clone(), expression_builders::assocplaceholder(sym)),
                                DepOfWrongForm(prem.clone(), expression_builders::binopplaceholder(BSymbol::Implies)),
                            ])),
                        }
                    }
                    for sproof in sproofs.iter() {
                        assert_eq!(sproof.premises().len(), 1);
                        let prem = sproof.lookup_expr_or_die(sproof.premises()[0].clone())?;
                        slab.entry(prem.clone()).or_insert_with(|| next());
                        for r in sproof.exprs() {
                            let e = sproof.lookup_expr_or_die(r)?.clone();
                            slab.entry(e.clone()).or_insert_with(|| next());
                            g.add_edge(slab[&prem], slab[&e], ());
                        }
                    }
                    let rslab = slab.clone().into_iter().map(|(k, v)| (v, k)).collect::<HashMap<_, _>>();
                    let sccs = tarjan_scc(&g).iter().map(|x| x.iter().map(|i| rslab[i].clone()).collect()).collect::<Vec<HashSet<_>>>();
                    println!("sccs: {:?}", sccs);
                    if sccs.iter().any(|s| exprs.iter().all(|e| s.contains(e))) {
                        return Ok(());
                    } else {
                        let mut errstring = format!("Not all elements of the conclusion are mutually implied by the premises.");
                        if let Some(e) = exprs.iter().find(|e| !sccs.iter().any(|s| s.contains(e))) {
                            errstring += &format!("\nThe expression {} occurs in the conclusion, but not in any of the premises.", e);
                        } else {
                            exprs.iter().any(|e1| exprs.iter().any(|e2| {
                                for i in 0..sccs.len() {
                                    if sccs[i].contains(e2) && !sccs[i..].iter().any(|s| s.contains(e1)) {
                                        errstring += &format!("\nThe expression {} is unreachable from {} by the premises.", e2, e1);
                                        return true;
                                    }
                                }
                                false
                            }));
                        }
                        return Err(Other(errstring));
                    }}
                }
                return Err(ConclusionOfWrongForm(expression_builders::assocplaceholder(sym)));
            },
            EquivalenceElim => {
                let prem1 = p.lookup_expr_or_die(deps[0].clone())?;
                let prem2 = p.lookup_expr_or_die(deps[1].clone())?;
                either_order(&prem1, &prem2, |i, j| {
                    if let Expr::AssocBinop { symbol: ASymbol::Equiv, ref exprs } = i {
                        // TODO: Negation?
                        if exprs.iter().find(|x| x == &j).is_none() {
                            return Err(DoesNotOccur(j.clone(), i.clone()));
                        }
                        if exprs.iter().find(|x| x == &&conclusion).is_none() {
                            return Err(DoesNotOccur(conclusion.clone(), i.clone()));
                        }
                        return Ok(Some(()));
                    }
                  Ok(None)
                }, || Err(DepDoesNotExist(expression_builders::assocplaceholder(ASymbol::Equiv), true)))
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
            ExistsIntro | ExistsElim | ForallElim => Some(1),
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
    fn check<P: Proof>(self, p: &P, conclusion: Expr, deps: Vec<P::Reference>, sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<P::Reference, P::SubproofReference>> {
        use ProofCheckError::*; use PredicateInference::*;
        fn unifies_wrt_var<P: Proof>(e1: &Expr, e2: &Expr, var: &str) -> Result<Expr, ProofCheckError<P::Reference, P::SubproofReference>> {
            let constraints = vec![Constraint::Equal(e1.clone(), e2.clone())].into_iter().collect();
            if let Some(substitutions) = unify(constraints) {
                if substitutions.0.len() == 0 {
                    assert_eq!(e1, e2);
                    return Ok(expression_builders::var(var));
                } else if substitutions.0.len() == 1 {
                    if &substitutions.0[0].0 == var {
                        assert_eq!(&subst(e1, &substitutions.0[0].0, substitutions.0[0].1.clone()), e2);
                        return Ok(substitutions.0[0].1.clone());
                    } else {
                        // TODO: standardize non-string error messages for unification-based rules
                        return Err(Other(format!("Attempted to substitute for a variable other than the binder: {}", substitutions.0[0].0)));
                    }
                } else {
                    return Err(Other(format!("More than one variable was substituted: {:?}", substitutions)));
                }
            } else {
                return Err(Other(format!("No substitution found between {} and {}.", e1, e2)));
            }
        }
        fn generalizable_variable_counterexample<P: Proof>(sproof: &P, line: P::Reference, var: &str) -> Option<Expr> {
            let mut contained = sproof.contained_justifications();
            contained.extend(sproof.premises().into_iter());
            //println!("gvc contained {:?}", contained.iter().map(|x| sproof.lookup_expr(x.clone())).collect::<Vec<_>>());
            let reachable = sproof.transitive_dependencies(line);
            //println!("gvc reachable {:?}", reachable.iter().map(|x| sproof.lookup_expr(x.clone())).collect::<Vec<_>>());
            let outside = reachable.difference(&contained);
            //println!("gvc outside {:?}", outside.clone().map(|x| sproof.lookup_expr(x.clone())).collect::<Vec<_>>());
            outside.filter_map(|x| sproof.lookup_expr(x.clone())).find(|e| freevars(e).contains(var))
        }
        match self {
            ForallIntro => {
                let sproof = p.lookup_subproof_or_die(sdeps[0].clone())?;
                if let Expr::Quantifier { symbol: QSymbol::Forall, ref name, ref body } = conclusion {
                    for (r, expr) in sproof.exprs().into_iter().map(|r| sproof.lookup_expr_or_die(r.clone()).map(|e| (r, e))).collect::<Result<Vec<_>, _>>()? {
                        if let Ok(Expr::Var { name: constant }) = unifies_wrt_var::<P>(body, &expr, name) {
                            println!("ForallIntro constant {:?}", constant);
                            if let Some(dangling) = generalizable_variable_counterexample(&sproof, r, &constant) {
                                return Err(Other(format!("The constant {} occurs in dependency {} that's outside the subproof.", constant, dangling)));
                            } else {
                                let expected = subst(body, &constant, expression_builders::var(name));
                                if &expected != &**body {
                                    return Err(Other(format!("Not all free occurrences of {} are replaced with {} in {}.", constant, name, body)));
                                }
                                return Ok(());
                            }
                        }
                    }
                    return Err(Other(format!("Couldn't find a subproof line that unifies with the conclusion ({}).", conclusion)));
                } else {
                    Err(ConclusionOfWrongForm(expression_builders::quantifierplaceholder(QSymbol::Forall)))
                }
            },
            ForallElim => {
                let prem = p.lookup_expr_or_die(deps[0].clone())?;
                if let Expr::Quantifier { symbol: QSymbol::Forall, ref name, ref body } = prem {
                    unifies_wrt_var::<P>(body, &conclusion, name)?;
                    Ok(())
                } else {
                    Err(DepOfWrongForm(prem, expression_builders::quantifierplaceholder(QSymbol::Forall)))
                }
            },
            ExistsIntro => {
                if let Expr::Quantifier { symbol: QSymbol::Exists, ref name, ref body } = conclusion {
                    let prem = p.lookup_expr_or_die(deps[0].clone())?;
                    unifies_wrt_var::<P>(body, &prem, name)?;
                    Ok(())
                } else {
                    Err(ConclusionOfWrongForm(expression_builders::quantifierplaceholder(QSymbol::Exists)))
                }
            },
            ExistsElim => {
/*
1 | exists x, phi(x)
  | ---
2 | | phi(a)
  | | ---
3 | | psi, SomeRule, 2
4 | psi, ExistElim, 2-3

- the body of the existential in dep 1 must unify with the premise of the subproof at 2, this infers the skolem constant
- the conclusion 4 must occur in some line of the subproof at 2-3 (in this, 3)
- the skolem constant must not occur in the transitive dependencies of the conclusion (generalizable variable conterexample check)
- the skolem constant must not escape to the conclusion (freevars check)
*/
                let prem = p.lookup_expr_or_die(deps[0].clone())?;
                let sproof = p.lookup_subproof_or_die(sdeps[0].clone())?;
                let skolemname = {
                    if let Expr::Quantifier { symbol: QSymbol::Exists, ref name, ref body } = prem {
                        let subprems = sproof.premises();
                        if subprems.len() != 1 {
                            // TODO: can/should this be generalized?
                            return Err(Other(format!("Subproof has {} premises, expected 1.", subprems.len())));
                        }
                        let subprem = p.lookup_expr_or_die(subprems[0].clone())?;
                        if let Ok(Expr::Var { name: skolemname }) = unifies_wrt_var::<P>(body, &subprem, name) {
                            skolemname
                        } else {
                            return Err(Other(format!("Premise {} doesn't unify with the body of dependency {}", subprem, prem)));
                        }
                    } else {
                        return Err(DepOfWrongForm(prem, expression_builders::quantifierplaceholder(QSymbol::Exists)));
                    }
                };
                for (r, expr) in sproof.exprs().into_iter().map(|r| sproof.lookup_expr_or_die(r.clone()).map(|e| (r, e))).collect::<Result<Vec<_>, _>>()? {
                    if expr == conclusion {
                        println!("ExistsElim conclusion {:?} skolemname {:?}", conclusion, skolemname);
                        if let Some(dangling) = generalizable_variable_counterexample(&sproof, r, &skolemname) {
                            return Err(Other(format!("The skolem constant {} occurs in dependency {} that's outside the subproof.", skolemname, dangling)));
                        }
                        if freevars(&conclusion).contains(&skolemname) {
                            return Err(Other(format!("The skolem constant {} escapes to the conclusion {}.", skolemname, conclusion)));
                        }
                        return Ok(());
                    }
                }
                return Err(Other(format!("Couldn't find a subproof line equal to the conclusion ({}).", conclusion)));
            },
        }
    }
}

fn check_by_normalize_first_expr<F, P: Proof>(p: &P, deps: Vec<P::Reference>, conclusion: Expr, commutative: bool, normalize_fn: F) -> Result<(), ProofCheckError<P::Reference, P::SubproofReference>>
where F: Fn(Expr) -> Expr {
    let premise = p.lookup_expr_or_die(deps[0].clone())?;
    let mut p = normalize_fn(premise);
    let mut q = normalize_fn(conclusion);
    if commutative {
        p = p.sort_commutative_ops();
        q = q.sort_commutative_ops();
    }
    if p == q { Ok(()) }
    else { Err(ProofCheckError::Other(format!("{} and {} are not equal.", p, q))) }
}

fn check_by_rewrite_rule<P: Proof>(p: &P, deps: Vec<P::Reference>, conclusion: Expr, commutative: bool, rule: &RewriteRule) -> Result<(), ProofCheckError<P::Reference, P::SubproofReference>> {
    check_by_normalize_first_expr(p, deps, conclusion, commutative, |e| rule.reduce(e))
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
            DoubleNegation => "Double Negation",
            Complement => "Complement",
            Identity => "Identity",
            Annihilation => "Annihilation",
            Inverse => "Inverse",
            Absorption => "Absorption",
            Reduction => "Reduction",
            Adjacency => "Adjacency",
        }.into()
    }
    fn get_classifications(&self) -> HashSet<RuleClassification> {
        [RuleClassification::Equivalence].iter().cloned().collect()
    }
    fn num_deps(&self) -> Option<usize> { Some(1) } // all equivalence rules rewrite a single statement
    fn num_subdeps(&self) -> Option<usize> { Some(0) }
    fn check<P: Proof>(self, p: &P, conclusion: Expr, deps: Vec<P::Reference>, _sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<P::Reference, P::SubproofReference>> {
        use ProofCheckError::*; use Equivalence::*;


        match self {
            DeMorgan => check_by_normalize_first_expr(p, deps, conclusion, false, |e| e.normalize_demorgans()),
            Association => check_by_normalize_first_expr(p, deps, conclusion, false, |e| e.combine_associative_ops()),
            Commutation => check_by_normalize_first_expr(p, deps, conclusion, false, |e| e.sort_commutative_ops()),
            Idempotence => check_by_normalize_first_expr(p, deps, conclusion, false, |e| e.normalize_idempotence()),
            DoubleNegation => check_by_rewrite_rule(p, deps, conclusion, false, &DOUBLE_NEGATION_RULES),
            Distribution => check_by_rewrite_rule(p, deps, conclusion, true, &DISTRIBUTION_RULES),
            Complement => check_by_rewrite_rule(p, deps, conclusion, false, &COMPLEMENT_RULES),
            Identity => check_by_rewrite_rule(p, deps, conclusion, false, &IDENTITY_RULES),
            Annihilation => check_by_rewrite_rule(p, deps, conclusion, false, &ANNIHILATION_RULES),
            Inverse => check_by_rewrite_rule(p, deps, conclusion, false, &INVERSE_RULES),
            Absorption => check_by_rewrite_rule(p, deps, conclusion, false, &ABSORPTION_RULES),
            Reduction => check_by_rewrite_rule(p, deps, conclusion, true, &REDUCTION_RULES),
            Adjacency => check_by_rewrite_rule(p, deps, conclusion, false, &ADJACENCY_RULES),
        }
    }
}

impl RuleT for ConditionalEquivalence {
    fn get_name(&self) -> String {
        use ConditionalEquivalence::*;
        match self {
            Complement => "Conditional Complement",
            Identity => "Conditional Identity",
            Annihilation => "Conditional Annihilation",
            Implication => "Implication",
            BiImplication => "BiImplication",
            Contraposition => "Contraposition",
            Currying => "Currying",
        }.into()
    }
    fn get_classifications(&self) -> HashSet<RuleClassification> {
        [RuleClassification::Equivalence].iter().cloned().collect()
    }
    fn num_deps(&self) -> Option<usize> { Some(1) } // all equivalence rules rewrite a single statement
    fn num_subdeps(&self) -> Option<usize> { Some(0) }
    fn check<P: Proof>(self, p: &P, conclusion: Expr, deps: Vec<P::Reference>, _sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<P::Reference, P::SubproofReference>> {
        use ProofCheckError::*; use ConditionalEquivalence::*;
        match self {
            Complement => check_by_rewrite_rule(p, deps, conclusion, false, &CONDITIONAL_COMPLEMENT_RULES),
            Identity => check_by_rewrite_rule(p, deps, conclusion, false, &CONDITIONAL_IDENTITY_RULES),
            Annihilation => check_by_rewrite_rule(p, deps, conclusion, false, &CONDITIONAL_ANNIHILATION_RULES),
            Implication => check_by_rewrite_rule(p, deps, conclusion, false, &CONDITIONAL_IMPLICATION_RULES),
            BiImplication => check_by_rewrite_rule(p, deps, conclusion, false, &CONDITIONAL_BIIMPLICATION_RULES),
            Contraposition => check_by_rewrite_rule(p, deps, conclusion, false, &CONDITIONAL_CONTRAPOSITION_RULES),
            Currying => check_by_rewrite_rule(p, deps, conclusion, false, &CONDITIONAL_CURRYING_RULES)
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

fn either_order<A, T, R: Eq, S: Eq, F: FnMut(&A, &A)->Result<Option<T>, ProofCheckError<R, S>>, G: FnOnce()->Result<T, ProofCheckError<R, S>>>(a1: &A, a2: &A, mut f: F, g: G) -> Result<T, ProofCheckError<R, S>> {
  macro_rules! h {
    ($i:expr, $j:expr) => (
      match f($i, $j) {
        Ok(Some(x)) => return Ok(x),
        Ok(None) => None,
        Err(e) => Some(e),
      }
    );
  };
  match (h!(a1, a2), h!(a2, a1)) {
    (None, None) => g(),
    (Some(e), None) => Err(e),
    (None, Some(e)) => Err(e),
    (Some(e1), Some(e2)) => { if e1 == e2 { Err(e1) } else { Err(ProofCheckError::OneOf(vec![e1, e2]))} },
  }
}

#[derive(Debug, PartialEq, Eq)]
pub enum ProofCheckError<R, S> {
    LineDoesNotExist(R),
    SubproofDoesNotExist(S),
    ReferencesLaterLine(LineAndIndent, usize),
    IncorrectDepCount(Vec<R>, usize),
    IncorrectSubDepCount(Vec<S>, usize),
    DepOfWrongForm(Expr, Expr),
    ConclusionOfWrongForm(Expr),
    DoesNotOccur(Expr, Expr),
    DepDoesNotExist(Expr, bool),
    OneOf(Vec<ProofCheckError<R, S>>),
    Other(String),
}

impl<R: std::fmt::Debug, S: std::fmt::Debug> std::fmt::Display for ProofCheckError<R, S> {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        use ProofCheckError::*;
        match self {
            LineDoesNotExist(r) => write!(f, "The referenced line {:?} does not exist.", r),
            SubproofDoesNotExist(s) => write!(f, "The referenced subproof {:?} does not exist.", s),
            ReferencesLaterLine(li, i) => write!(f, "The dependency on line {} is after the line it occurs on ({}).", li.line, i),
            IncorrectDepCount(deps, n) => write!(f, "Too {} dependencies (expected: {}, provided: {}).", if deps.len() > *n { "many" } else { "few" }, n, deps.len()),
            IncorrectSubDepCount(sdeps, n) => write!(f, "Too {} subproof dependencies (expected: {}, provided: {}).", if sdeps.len() > *n { "many" } else { "few" }, n, sdeps.len()),
            DepOfWrongForm(x, y) => write!(f, "A dependency ({}) is of the wrong form, expected {}.", x, y),
            ConclusionOfWrongForm(kind) => write!(f, "The conclusion is of the wrong form, expected {}.", kind),
            DoesNotOccur(x, y) => write!(f, "{} does not occur in {}.", x, y),
            DepDoesNotExist(x, approx) => write!(f, "{}{} is required as a dependency, but it does not exist.", if *approx { "Something of the shape " } else { "" }, x),
            OneOf(v) => {
                write!(f, "One of the following requirements was not met:\n")?;
                v.iter().map(|e| write!(f, "{}\n", e)).collect::<Result<_,_>>().and_then(|()| Ok(()))
            }
            Other(msg) => write!(f, "{}", msg),
        }
    }
}
