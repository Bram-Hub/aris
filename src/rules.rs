/*!
# Organization
`RuleT` is the main trait to implement for rule metadata and implementations.

## Different enums for different types of rule
Rules are split into different enums both for based on what type of rule they are.

This allows metadata to be defined only once for certain classes of rules (e.g. `Equivalence`s always take 1 plain dep and 0 subdeps).

The `SharedChecks` wrapper and `frunk::Coproduct` tie the different enums together into `Rule`.

`SharedChecks`'s `RuleT` instance enforces common requirements based on the inner type's metadata (mostly number of dependencies.

## Name metadata

`RuleT::get_name` is a human-readable name, often with unicode, for displaying in the UIs.

`RuleM::from_serialized_name` is used for constructing Java values of type `edu.rpi.aris.rules.RuleList`, and for deserializing rules from XML.

## `RuleT::check` implementations

Each `check` implementation usually starts off with bringing the rules of the relevant enum into scope, and then matching on which rule it is.

`check` should not recursively check the correctness of its dependencies:
- This has the wrong semantics, a green check mark or a red x will should only occur next to a line based on the local validity of that rule
- This is also inefficient, the driver that *does* recursively check the entire proof would be quadratic instead of linear in the size of the proof if each rule checked dependencies recursively.

# Checklists
## Checklist for adding a new rule
(e.g. for adding `AndIntro` if it wasn't already there)
- Add it to the appropriate rule type enum (if it needs a new type, see the next checklist)
- Add it to the end of `edu.rpi.aris.rules.RuleList` (order may matter for the Java code)
- Add an entry for it to the declare_rules! entry in `RuleM`
    - The `Coproduct::{Inl,Inr}` wrapping depends on type of rule that the rule is in
    - For the string, use same name as in the Java (deserializing the UI's usage of it will fail if the name isn't the same)
- In the `impl RuleT for WhicheverEnum` block:
    - Add the metadata, if applicable 
    - Add the new rule to the `check` method's main match block, with an `unimplemented!()` body
- Verify that all the structural changes compile, possibly commit the structural changes so far
    - Commit `b86de7fbe6bea3947ef864b8f253be34ec0c1306` is a good example of what the structure should look like at this point
- Replace the `unimplemented!()` with an actual implementation
- Add tests (both should-pass and should-fail) for the new rule to `libaris::proofs::proof_tests`

Adding the tests and implementing the rule can be interleaved; it's convenient to debug the implementation by iterating on `cargo test -- test_your_rule_name`, possibly with `--nocapture` if you're println-debugging.

## Checklist for adding a new rule type
(e.g. for adding `PrepositionalInference` if it wasn't already there)
- Create the new enum, preferably right after all the existing ones
- Add the new enum to the `Rule` type alias, inside the `SharedChecks` and `Coprod!` wrappers
- Add a `RuleT` impl block for the new enum
    - if default metadata applies to all rules of the type, add those (e.g. `Equivalence`)
    - if default metadata doesn't apply to all rules of the type, add an empty match block (e.g. `PrepositionalInference`)

*/
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
    Currying, ConditionalDistribution, ConditionalReduction, KnightsAndKnaves, ConditionalIdempotence,
    BiconditionalNegation, BiconditionalSubstitution
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum RedundantPrepositionalInference {
    ModusTollens, HypotheticalSyllogism, ExcludedMiddle, ConstructiveDilemma
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum AutomationRelatedRules {
    AsymmetricTautology,
}

/// The RuleT instance for SharedChecks does checking that is common to all the rules;
///  it should always be the outermost constructor of the Rule type alias.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct SharedChecks<T>(T);

pub type Rule = SharedChecks<Coprod!(PrepositionalInference, PredicateInference,
    Equivalence, ConditionalEquivalence, RedundantPrepositionalInference,
    AutomationRelatedRules)>;

/// Conveniences for constructing rules of the appropriate type, primarily for testing.
/// The non-standard naming conventions here are because a module is being used to pretend to be an enum.
#[allow(non_snake_case)]
pub mod RuleM {
    #![allow(non_upper_case_globals)]
    use super::*;
    macro_rules! declare_rules {
        ($([$id:ident, $name:literal, $value:tt]),+) => {
            declare_rules!{ DECLARE_STATICS; $([$id, $value]),+ }

            pub static ALL_SERIALIZED_NAMES: &[&'static str] = &[ $($name),+ ];
            pub static ALL_RULES: &[Rule] = &[$($id),+];

            #[allow(unused_parens)]
            pub fn to_serialized_name(rule: Rule) -> &'static str {
                declare_rules! { DECLARE_MATCH; on: rule; default: unreachable!(); $([$value, $name]),+ }
            }
            pub fn from_serialized_name(name: &str) -> Option<Rule> {
                Some(declare_rules! { DECLARE_MATCH; on: name; default: { return None; }; $([$name, $id]),+ })
            }
        };
        (DECLARE_STATICS; [$id: ident, $value:expr]) => {
            pub static $id: Rule = $value;
        };
        (DECLARE_STATICS; [$id: ident, $value:expr], $([$id_rec:ident, $value_rec:expr]),+) => {
            declare_rules!{ DECLARE_STATICS; [$id, $value] }
            declare_rules!{ DECLARE_STATICS; $([$id_rec, $value_rec]),+ }
        };
        (DECLARE_MATCH; on: $match_arg:expr; default: $default_rhs:expr; $([$lhs:pat, $rhs:expr]),+) => {
            match $match_arg {
                $($lhs => $rhs),+,
                _ => $default_rhs
            }
        }
    }
    // The unused_parens are actually needed in order to capture the entire SharedChecks(...) as a tokentree.
    // If the outer macro captures $value:expr, to_serialized_name breaks (because it needs $value:pat).
    // If the outer macro captures $value:pat, from_serialized_name and DECLARE_STATICS break (because they need $value:expr).
    // If the parens are omitted, $value:tt only captures SharedChecks, without the (...)
    // I haven't yet found a way to use macro_rules! to convert between expr and pat.
    declare_rules! {
        [Reit, "REITERATION", (SharedChecks(Inl(PrepositionalInference::Reit)))],
        [AndIntro, "CONJUNCTION", (SharedChecks(Inl(PrepositionalInference::AndIntro)))],
        [AndElim, "SIMPLIFICATION", (SharedChecks(Inl(PrepositionalInference::AndElim)))],
        [OrIntro, "ADDITION", (SharedChecks(Inl(PrepositionalInference::OrIntro)))],
        [OrElim, "DISJUNCTIVE_SYLLOGISM", (SharedChecks(Inl(PrepositionalInference::OrElim)))],
        [ImpIntro, "CONDITIONAL_PROOF", (SharedChecks(Inl(PrepositionalInference::ImpIntro)))],
        [ImpElim, "MODUS_PONENS", (SharedChecks(Inl(PrepositionalInference::ImpElim)))],
        [NotIntro, "PROOF_BY_CONTRADICTION", (SharedChecks(Inl(PrepositionalInference::NotIntro)))],
        [NotElim, "DOUBLENEGATION", (SharedChecks(Inl(PrepositionalInference::NotElim)))],
        [ContradictionIntro, "CONTRADICTION", (SharedChecks(Inl(PrepositionalInference::ContradictionIntro)))],
        [ContradictionElim, "PRINCIPLE_OF_EXPLOSION", (SharedChecks(Inl(PrepositionalInference::ContradictionElim)))],
        [BiconditionalIntro, "BICONDITIONAL_INTRO", (SharedChecks(Inl(PrepositionalInference::BiconditionalIntro)))],
        [BiconditionalElim, "BICONDITIONAL_ELIM", (SharedChecks(Inl(PrepositionalInference::BiconditionalElim)))],
        [EquivalenceIntro, "EQUIVALENCE_INTRO", (SharedChecks(Inl(PrepositionalInference::EquivalenceIntro)))],
        [EquivalenceElim, "EQUIVALENCE_ELIM", (SharedChecks(Inl(PrepositionalInference::EquivalenceElim)))],

        [ForallIntro, "UNIVERSAL_GENERALIZATION", (SharedChecks(Inr(Inl(PredicateInference::ForallIntro))))],
        [ForallElim, "UNIVERSAL_INSTANTIATION", (SharedChecks(Inr(Inl(PredicateInference::ForallElim))))],
        [ExistsIntro, "EXISTENTIAL_GENERALIZATION", (SharedChecks(Inr(Inl(PredicateInference::ExistsIntro))))],
        [ExistsElim, "EXISTENTIAL_INSTANTIATION", (SharedChecks(Inr(Inl(PredicateInference::ExistsElim))))],

        [ModusTollens, "MODUS_TOLLENS", (SharedChecks(Inr(Inr(Inr(Inr(Inl(RedundantPrepositionalInference::ModusTollens)))))))],
        [HypotheticalSyllogism, "HYPOTHETICAL_SYLLOGISM", (SharedChecks(Inr(Inr(Inr(Inr(Inl(RedundantPrepositionalInference::HypotheticalSyllogism)))))))],
        [ExcludedMiddle, "EXCLUDED_MIDDLE", (SharedChecks(Inr(Inr(Inr(Inr(Inl(RedundantPrepositionalInference::ExcludedMiddle)))))))],
        [ConstructiveDilemma, "CONSTRUCTIVE_DILEMMA", (SharedChecks(Inr(Inr(Inr(Inr(Inl(RedundantPrepositionalInference::ConstructiveDilemma)))))))],
    
        [Association, "ASSOCIATION", (SharedChecks(Inr(Inr(Inl(Equivalence::Association)))))],
        [Commutation, "COMMUTATION", (SharedChecks(Inr(Inr(Inl(Equivalence::Commutation)))))],
        [Idempotence, "IDEMPOTENCE", (SharedChecks(Inr(Inr(Inl(Equivalence::Idempotence)))))],
        [DeMorgan, "DE_MORGAN", (SharedChecks(Inr(Inr(Inl(Equivalence::DeMorgan)))))],
        [Distribution, "DISTRIBUTION", (SharedChecks(Inr(Inr(Inl(Equivalence::Distribution)))))],
        [DoubleNegation, "DOUBLENEGATION_EQUIV", (SharedChecks(Inr(Inr(Inl(Equivalence::DoubleNegation)))))],
        [Complement, "COMPLEMENT", (SharedChecks(Inr(Inr(Inl(Equivalence::Complement)))))],
        [Identity, "IDENTITY", (SharedChecks(Inr(Inr(Inl(Equivalence::Identity)))))],
        [Annihilation, "ANNIHILATION", (SharedChecks(Inr(Inr(Inl(Equivalence::Annihilation)))))],
        [Inverse, "INVERSE", (SharedChecks(Inr(Inr(Inl(Equivalence::Inverse)))))],
        [Absorption, "ABSORPTION", (SharedChecks(Inr(Inr(Inl(Equivalence::Absorption)))))],
        [Reduction, "REDUCTION", (SharedChecks(Inr(Inr(Inl(Equivalence::Reduction)))))],
        [Adjacency, "ADJACENCY", (SharedChecks(Inr(Inr(Inl(Equivalence::Adjacency)))))],

        [CondComplement, "CONDITIONAL_COMPLEMENT", (SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::Complement))))))],
        [CondIdentity, "CONDITIONAL_IDENTITY", (SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::Identity))))))],
        [CondAnnihilation, "CONDITIONAL_ANNIHILATION", (SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::Annihilation))))))],
        [Implication, "IMPLICATION", (SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::Implication))))))],
        [BiImplication, "BI_IMPLICATION", (SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::BiImplication))))))],
        [Contraposition, "CONTRAPOSITION", (SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::Contraposition))))))],
        [Currying, "CURRYING", (SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::Currying))))))],
        [ConditionalDistribution, "CONDITIONAL_DISTRIBUTION", (SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::ConditionalDistribution))))))],
        [ConditionalReduction, "CONDITIONAL_REDUCTION", (SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::ConditionalReduction))))))],
        [KnightsAndKnaves, "KNIGHTS_AND_KNAVES", (SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::KnightsAndKnaves))))))],
        [ConditionalIdempotence, "CONDITIONAL_IDEMPOTENCE", (SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::ConditionalIdempotence))))))],
        [BiconditionalNegation, "BICONDITIONAL_NEGATION", (SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::BiconditionalNegation))))))],
        [BiconditionalSubstitution, "BICONDITIONAL_SUBSTITUTION", (SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::BiconditionalSubstitution))))))],

        [AsymmetricTautology, "ASYMMETRIC_TAUTOLOGY", (SharedChecks(Inr(Inr(Inr(Inr(Inr(Inl(AutomationRelatedRules::AsymmetricTautology))))))))]
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum RuleClassification {
    Introduction, Elimination, Equivalence, Inference, Predicate
}

/// libaris::rules::RuleT contains metadata and implementations of the rules
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
            let contained = sproof.contained_justifications(true);
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
        use Equivalence::*;


        match self {
            DeMorgan => check_by_normalize_first_expr(p, deps, conclusion, false, |e| e.normalize_demorgans()),
            Association => check_by_normalize_first_expr(p, deps, conclusion, false, |e| e.combine_associative_ops()),
            Commutation => check_by_normalize_first_expr(p, deps, conclusion, false, |e| e.sort_commutative_ops()),
            Idempotence => check_by_normalize_first_expr(p, deps, conclusion, false, |e| e.normalize_idempotence()),
            DoubleNegation => check_by_rewrite_rule(p, deps, conclusion, false, &DOUBLE_NEGATION_RULES),
            // Distribution and Reduction have outputs containing binops that need commutative sorting
            // because we can't expect people to know the specific order of outputs that our definition
            // of the rules uses
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
            BiImplication => "Biconditional Equivalence",
            Contraposition => "Contraposition",
            Currying => "Exportation",
            ConditionalDistribution => "Conditional Distribution",
            ConditionalReduction => "Conditional Reduction",
            KnightsAndKnaves => "Knights and Knaves",
            ConditionalIdempotence => "Conditional Idempotence",
            BiconditionalNegation => "Biconditional Negation",
            BiconditionalSubstitution => "Biconditional Substitution"
        }.into()
    }
    fn get_classifications(&self) -> HashSet<RuleClassification> {
        [RuleClassification::Equivalence].iter().cloned().collect()
    }
    fn num_deps(&self) -> Option<usize> { Some(1) } // all equivalence rules rewrite a single statement
    fn num_subdeps(&self) -> Option<usize> { Some(0) }
    fn check<P: Proof>(self, p: &P, conclusion: Expr, deps: Vec<P::Reference>, _sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<P::Reference, P::SubproofReference>> {
        use ConditionalEquivalence::*;
        match self {
            Complement => check_by_rewrite_rule(p, deps, conclusion, false, &CONDITIONAL_COMPLEMENT_RULES),
            Identity => check_by_rewrite_rule(p, deps, conclusion, false, &CONDITIONAL_IDENTITY_RULES),
            Annihilation => check_by_rewrite_rule(p, deps, conclusion, false, &CONDITIONAL_ANNIHILATION_RULES),
            Implication => check_by_rewrite_rule(p, deps, conclusion, false, &CONDITIONAL_IMPLICATION_RULES),
            BiImplication => check_by_rewrite_rule(p, deps, conclusion, false, &CONDITIONAL_BIIMPLICATION_RULES),
            Contraposition => check_by_rewrite_rule(p, deps, conclusion, false, &CONDITIONAL_CONTRAPOSITION_RULES),
            Currying => check_by_rewrite_rule(p, deps, conclusion, false, &CONDITIONAL_CURRYING_RULES),
            ConditionalDistribution => check_by_rewrite_rule(p, deps, conclusion, true, &CONDITIONAL_DISTRIBUTION_RULES),
            ConditionalReduction => check_by_rewrite_rule(p, deps, conclusion, true, &CONDITIONAL_REDUCTION_RULES),
            KnightsAndKnaves => check_by_rewrite_rule(p, deps, conclusion, true, &KNIGHTS_AND_KNAVES_RULES),
            ConditionalIdempotence => check_by_rewrite_rule(p, deps, conclusion, true, &CONDITIONAL_IDEMPOTENCE_RULES),
            BiconditionalNegation => check_by_rewrite_rule(p, deps, conclusion, true, &BICONDITIONAL_NEGATION_RULES),
            BiconditionalSubstitution => check_by_rewrite_rule(p, deps, conclusion, true, &BICONDITIONAL_SUBSTITUTION_RULES)
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

impl RuleT for AutomationRelatedRules {
    fn get_name(&self) -> String {
        match self {
            AutomationRelatedRules::AsymmetricTautology => "AsymmetricTautology",
        }.into()
    }
    fn get_classifications(&self) -> HashSet<RuleClassification> {
        [RuleClassification::Inference].iter().cloned().collect()
    }
    fn num_deps(&self) -> Option<usize> {
        match self {
            AutomationRelatedRules::AsymmetricTautology => None,
        }
    }
    fn num_subdeps(&self) -> Option<usize> {
        match self {
            AutomationRelatedRules::AsymmetricTautology => Some(0),
        }
    }
    fn check<P: Proof>(self, _p: &P, _expr: Expr, _deps: Vec<P::Reference>, _sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<P::Reference, P::SubproofReference>> {
        match self {
            AutomationRelatedRules::AsymmetricTautology => unimplemented!(),
        }
    }
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
    ReferencesLaterLine(R, Coproduct<R, Coproduct<S, frunk::coproduct::CNil>>),
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
            ReferencesLaterLine(line, dep) => write!(f, "The dependency {:?} is after the step that uses it ({:?}).", dep, line),
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
