/*!
Logical inference rules for checking proof steps

# Organization
`RuleT` is the main trait to implement for rule metadata and implementations.

## Different enums for different types of rule
Rules are split into different enums both for based on what type of rule they are.

This allows metadata to be defined only once for certain classes of rules (e.g. `BooleanEquivalence`s always take 1 plain dep and 0 subdeps).

The `SharedChecks` wrapper and `frunk_core::coproduct::Coproduct` tie the different enums together into `Rule`.

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
- Add tests (both should-pass and should-fail) for the new rule to `aris::proofs::proof_tests`

Adding the tests and implementing the rule can be interleaved; it's convenient to debug the implementation by iterating on `cargo test -- test_your_rule_name`, possibly with `--nocapture` if you're println-debugging.

## Checklist for adding a new rule type
(e.g. for adding `PrepositionalInference` if it wasn't already there)
- Create the new enum, preferably right after all the existing ones
- Add the new enum to the `Rule` type alias, inside the `SharedChecks` and `Coprod!` wrappers
- Add a `RuleT` impl block for the new enum
    - if default metadata applies to all rules of the type, add those (e.g. `BooleanEquivalence`)
    - if default metadata doesn't apply to all rules of the type, add an empty match block (e.g. `PrepositionalInference`)
*/

use crate::equivs;
use crate::expr::Constraint;
use crate::expr::Expr;
use crate::expr::Op;
use crate::expr::QuantKind;
use crate::proofs::PjRef;
use crate::proofs::Proof;
use crate::rewrite_rules::RewriteRule;

use std::collections::BTreeSet;
use std::collections::HashMap;
use std::collections::HashSet;
use std::string::ToString;

use frunk_core::coproduct::Coproduct;
use frunk_core::coproduct::Coproduct::Inl;
use frunk_core::coproduct::Coproduct::Inr;
use frunk_core::Coprod;
use itertools::Itertools;
use maplit::btreeset;
use maplit::hashset;
use petgraph::algo::tarjan_scc;
use petgraph::graphmap::DiGraphMap;
use strum_macros::*;

#[allow(missing_docs)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum PrepositionalInference {
    Reit,
    AndIntro,
    AndElim,
    OrIntro,
    OrElim,
    ImpIntro,
    ImpElim,
    NotIntro,
    NotElim,
    ContradictionIntro,
    ContradictionElim,
    BiconditionalIntro,
    BiconditionalElim,
    EquivalenceIntro,
    EquivalenceElim,
}

#[allow(missing_docs)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum PredicateInference {
    ForallIntro,
    ForallElim,
    ExistsIntro,
    ExistsElim,
}

#[allow(missing_docs)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum BooleanEquivalence {
    DeMorgan,
    Association,
    Commutation,
    Idempotence,
    Distribution,
    DoubleNegation,
    Complement,
    Identity,
    Annihilation,
    Inverse,
    Absorption,
    Reduction,
    Adjacency,
}

#[allow(missing_docs)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ConditionalEquivalence {
    Complement,
    Identity,
    Annihilation,
    Implication,
    Contraposition,
    Currying,
    ConditionalDistribution,
    ConditionalReduction,
    KnightsAndKnaves,
    ConditionalIdempotence,
}

#[allow(missing_docs)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum BiconditionalEquivalence {
    BiconditionalAssociation,
    BiconditionalCommutation,
    BiconditionalEquivalenceRule,
    BiconditionalNegation,
    BiconditionalSubstitution,
}

#[allow(missing_docs)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum RedundantPrepositionalInference {
    ModusTollens,
    HypotheticalSyllogism,
    DisjunctiveSyllogism,
    ExcludedMiddle,
    ConstructiveDilemma,
}

#[allow(missing_docs)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum AutomationRelatedRules {
    Resolution,
    TruthFunctionalConsequence,
}

#[allow(missing_docs)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum QuantifierEquivalence {
    QuantifierNegation,
    NullQuantification,
    ReplacingBoundVars,
    SwappingQuantifiers,
    AristoteleanSquare,
    QuantifierDistribution,
    PrenexLaws,
}

#[allow(missing_docs)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Induction {
    Weak,
    Strong,
}

/// This should be the default rule when creating a new step in a UI. It
/// always fails, and isn't part of any `RuleClassification`s.
///
/// ```rust
/// use aris::rules::EmptyRule;
/// use aris::rules::RuleT;
///
/// assert_eq!(EmptyRule.get_classifications().len(), 0);
/// ```
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct EmptyRule;

/// The RuleT instance for SharedChecks does checking that is common to all the rules;
///  it should always be the outermost constructor of the Rule type alias.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct SharedChecks<T>(T);

pub type Rule = SharedChecks<Coprod!(PrepositionalInference, PredicateInference, BooleanEquivalence, ConditionalEquivalence, BiconditionalEquivalence, RedundantPrepositionalInference, AutomationRelatedRules, QuantifierEquivalence, Induction, EmptyRule)>;

/// Conveniences for constructing rules of the appropriate type, primarily for testing.
/// The non-standard naming conventions here are because a module is being used to pretend to be an enum.
#[allow(non_snake_case)]
#[allow(non_upper_case_globals)]
pub mod RuleM {
    use super::*;
    macro_rules! declare_rules {
        ($([$id:ident, $name:literal, $value:tt]),+) => {
            declare_rules!{ DECLARE_STATICS; $([$id, $value]),+ }

            /// All string constants for the rules declared by `declare_rules!`
            pub static ALL_SERIALIZED_NAMES: &[&'static str] = &[ $($name),+ ];
            /// All `Rule` enums for the rules declared by `declare_rules!`
            pub static ALL_RULES: &[Rule] = &[$($id),+];

            /// Convert a Rule to a string compatible with the Java enum `edu.rpi.aris.rules.RuleList`
            #[allow(unused_parens)]
            pub fn to_serialized_name(rule: Rule) -> &'static str {
                declare_rules! { DECLARE_MATCH; on: rule; default: unreachable!(); $([$value, $name]),+ }
            }
            /// Convert string from the Java enum `edu.rpi.aris.rules.RuleList` to a Rule
            pub fn from_serialized_name(name: &str) -> Option<Rule> {
                Some(declare_rules! { DECLARE_MATCH; on: name; default: { return None; }; $([$name, $id]),+ })
            }
        };
        (DECLARE_STATICS; [$id: ident, $value:expr]) => {
            #[allow(unused_parens, missing_docs)]
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
        [OrElim, "DISJUNCTIVE_ELIMINATION", (SharedChecks(Inl(PrepositionalInference::OrElim)))],
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

        [Association, "ASSOCIATION", (SharedChecks(Inr(Inr(Inl(BooleanEquivalence::Association)))))],
        [Commutation, "COMMUTATION", (SharedChecks(Inr(Inr(Inl(BooleanEquivalence::Commutation)))))],
        [Idempotence, "IDEMPOTENCE", (SharedChecks(Inr(Inr(Inl(BooleanEquivalence::Idempotence)))))],
        [DeMorgan, "DE_MORGAN", (SharedChecks(Inr(Inr(Inl(BooleanEquivalence::DeMorgan)))))],
        [Distribution, "DISTRIBUTION", (SharedChecks(Inr(Inr(Inl(BooleanEquivalence::Distribution)))))],
        [DoubleNegation, "DOUBLENEGATION_EQUIV", (SharedChecks(Inr(Inr(Inl(BooleanEquivalence::DoubleNegation)))))],
        [Complement, "COMPLEMENT", (SharedChecks(Inr(Inr(Inl(BooleanEquivalence::Complement)))))],
        [Identity, "IDENTITY", (SharedChecks(Inr(Inr(Inl(BooleanEquivalence::Identity)))))],
        [Annihilation, "ANNIHILATION", (SharedChecks(Inr(Inr(Inl(BooleanEquivalence::Annihilation)))))],
        [Inverse, "INVERSE", (SharedChecks(Inr(Inr(Inl(BooleanEquivalence::Inverse)))))],
        [Absorption, "ABSORPTION", (SharedChecks(Inr(Inr(Inl(BooleanEquivalence::Absorption)))))],
        [Reduction, "REDUCTION", (SharedChecks(Inr(Inr(Inl(BooleanEquivalence::Reduction)))))],
        [Adjacency, "ADJACENCY", (SharedChecks(Inr(Inr(Inl(BooleanEquivalence::Adjacency)))))],

        [CondComplement, "CONDITIONAL_COMPLEMENT", (SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::Complement))))))],
        [CondIdentity, "CONDITIONAL_IDENTITY", (SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::Identity))))))],
        [CondAnnihilation, "CONDITIONAL_ANNIHILATION", (SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::Annihilation))))))],
        [Implication, "IMPLICATION", (SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::Implication))))))],
        [Contraposition, "CONTRAPOSITION", (SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::Contraposition))))))],
        [Currying, "CURRYING", (SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::Currying))))))],
        [ConditionalDistribution, "CONDITIONAL_DISTRIBUTION", (SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::ConditionalDistribution))))))],
        [ConditionalReduction, "CONDITIONAL_REDUCTION", (SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::ConditionalReduction))))))],
        [KnightsAndKnaves, "KNIGHTS_AND_KNAVES", (SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::KnightsAndKnaves))))))],
        [ConditionalIdempotence, "CONDITIONAL_IDEMPOTENCE", (SharedChecks(Inr(Inr(Inr(Inl(ConditionalEquivalence::ConditionalIdempotence))))))],

        [BiconditionalAssociation, "BICONDITIONAL_ASSOCIATION", (SharedChecks(Inr(Inr(Inr(Inr(Inl(BiconditionalEquivalence::BiconditionalAssociation)))))))],
        [BiconditionalCommutation, "BICONDITIONAL_COMMUTATION", (SharedChecks(Inr(Inr(Inr(Inr(Inl(BiconditionalEquivalence::BiconditionalCommutation)))))))],
        [BiconditionalEquivalenceRule, "BICONDITIONAL_EQUIVALENCE", (SharedChecks(Inr(Inr(Inr(Inr(Inl(BiconditionalEquivalence::BiconditionalEquivalenceRule)))))))],
        [BiconditionalNegation, "BICONDITIONAL_NEGATION", (SharedChecks(Inr(Inr(Inr(Inr(Inl(BiconditionalEquivalence::BiconditionalNegation)))))))],
        [BiconditionalSubstitution, "BICONDITIONAL_SUBSTITUTION", (SharedChecks(Inr(Inr(Inr(Inr(Inl(BiconditionalEquivalence::BiconditionalSubstitution)))))))],

        [ModusTollens, "MODUS_TOLLENS", (SharedChecks(Inr(Inr(Inr(Inr(Inr(Inl(RedundantPrepositionalInference::ModusTollens))))))))],
        [HypotheticalSyllogism, "HYPOTHETICAL_SYLLOGISM", (SharedChecks(Inr(Inr(Inr(Inr(Inr(Inl(RedundantPrepositionalInference::HypotheticalSyllogism))))))))],
        [DisjunctiveSyllogism, "DISJUNCTIVE_SYLLOGISM", (SharedChecks(Inr(Inr(Inr(Inr(Inr(Inl(RedundantPrepositionalInference::DisjunctiveSyllogism))))))))],
        [ExcludedMiddle, "EXCLUDED_MIDDLE", (SharedChecks(Inr(Inr(Inr(Inr(Inr(Inl(RedundantPrepositionalInference::ExcludedMiddle))))))))],
        [ConstructiveDilemma, "CONSTRUCTIVE_DILEMMA", (SharedChecks(Inr(Inr(Inr(Inr(Inr(Inl(RedundantPrepositionalInference::ConstructiveDilemma))))))))],

        [Resolution, "RESOLUTION", (SharedChecks(Inr(Inr(Inr(Inr(Inr(Inr(Inl(AutomationRelatedRules::Resolution)))))))))],
        [TruthFunctionalConsequence, "TRUTHFUNCTIONAL_CONSEQUENCE", (SharedChecks(Inr(Inr(Inr(Inr(Inr(Inr(Inl(AutomationRelatedRules::TruthFunctionalConsequence)))))))))],

        [QuantifierNegation, "QUANTIFIER_NEGATION", (SharedChecks(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inl(QuantifierEquivalence::QuantifierNegation))))))))))],
        [NullQuantification, "NULL_QUANTIFICATION", (SharedChecks(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inl(QuantifierEquivalence::NullQuantification))))))))))],
        [ReplacingBoundVars, "REPLACING_BOUND_VARS", (SharedChecks(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inl(QuantifierEquivalence::ReplacingBoundVars))))))))))],
        [SwappingQuantifiers, "SWAPPING_QUANTIFIERS", (SharedChecks(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inl(QuantifierEquivalence::SwappingQuantifiers))))))))))],
        [AristoteleanSquare, "ARISTOTELEAN_SQUARE", (SharedChecks(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inl(QuantifierEquivalence::AristoteleanSquare))))))))))],
        [QuantifierDistribution, "QUANTIFIER_DISTRIBUTION", (SharedChecks(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inl(QuantifierEquivalence::QuantifierDistribution))))))))))],
        [PrenexLaws, "PRENEX_LAWS", (SharedChecks(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inl(QuantifierEquivalence::PrenexLaws))))))))))],

        [WeakInduction, "WEAK_INDUCTION", (SharedChecks(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inl(Induction::Weak)))))))))))],
        [StrongInduction, "STRONG_INDUCTION", (SharedChecks(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inl(Induction::Strong)))))))))))],

        [EmptyRule, "EMPTY_RULE", (SharedChecks(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inr(Inl(super::EmptyRule))))))))))))]
    }
}

/// Classifications of rules for displaying in a nested drop-down menu in the GUI
#[allow(missing_docs)]
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, Display, EnumIter)]
pub enum RuleClassification {
    Introduction,
    Elimination,
    #[strum(to_string = "Misc. Inference")]
    MiscInference,
    #[strum(to_string = "Boolean Equivalence")]
    BooleanEquivalence,
    #[strum(to_string = "Conditional Equivalence")]
    ConditionalEquivalence,
    #[strum(to_string = "Biconditional Equivalence")]
    BiconditionalEquivalence,
    #[strum(to_string = "Quantifier Equivalence")]
    QuantifierEquivalence,
}

impl RuleClassification {
    /// Get an iterator over the rules in this rule classification
    pub fn rules(self) -> impl Iterator<Item = Rule> {
        RuleM::ALL_RULES.iter().filter(move |rule| rule.get_classifications().contains(&self)).cloned()
    }
}

/// aris::rules::RuleT contains metadata and implementations of the rules
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
    fn check<P: Proof>(self, p: &P, expr: Expr, deps: Vec<PjRef<P>>, sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<PjRef<P>, P::SubproofReference>>;
}

impl<A: RuleT, B: RuleT> RuleT for Coproduct<A, B> {
    fn get_name(&self) -> String {
        match self {
            Inl(x) => x.get_name(),
            Inr(x) => x.get_name(),
        }
    }
    fn get_classifications(&self) -> HashSet<RuleClassification> {
        match self {
            Inl(x) => x.get_classifications(),
            Inr(x) => x.get_classifications(),
        }
    }
    fn num_deps(&self) -> Option<usize> {
        match self {
            Inl(x) => x.num_deps(),
            Inr(x) => x.num_deps(),
        }
    }
    fn num_subdeps(&self) -> Option<usize> {
        match self {
            Inl(x) => x.num_subdeps(),
            Inr(x) => x.num_subdeps(),
        }
    }
    fn check<P: Proof>(self, p: &P, expr: Expr, deps: Vec<PjRef<P>>, sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<PjRef<P>, P::SubproofReference>> {
        match self {
            Inl(x) => x.check(p, expr, deps, sdeps),
            Inr(x) => x.check(p, expr, deps, sdeps),
        }
    }
}
impl RuleT for frunk_core::coproduct::CNil {
    fn get_name(&self) -> String {
        match *self {}
    }
    fn get_classifications(&self) -> HashSet<RuleClassification> {
        match *self {}
    }
    fn num_deps(&self) -> Option<usize> {
        match *self {}
    }
    fn num_subdeps(&self) -> Option<usize> {
        match *self {}
    }
    fn check<P: Proof>(self, _p: &P, _expr: Expr, _deps: Vec<PjRef<P>>, _sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<PjRef<P>, P::SubproofReference>> {
        match self {}
    }
}

impl<T: RuleT> RuleT for SharedChecks<T> {
    fn get_name(&self) -> String {
        self.0.get_name()
    }
    fn get_classifications(&self) -> HashSet<RuleClassification> {
        self.0.get_classifications()
    }
    fn num_deps(&self) -> Option<usize> {
        self.0.num_deps()
    }
    fn num_subdeps(&self) -> Option<usize> {
        self.0.num_subdeps()
    }
    fn check<P: Proof>(self, p: &P, expr: Expr, deps: Vec<PjRef<P>>, sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<PjRef<P>, P::SubproofReference>> {
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

pub fn do_expressions_contradict<P: Proof>(prem1: &Expr, prem2: &Expr) -> Result<(), ProofCheckError<PjRef<P>, P::SubproofReference>> {
    either_order(
        prem1,
        prem2,
        |i, j| {
            if let Expr::Not { ref operand } = i {
                if **operand == *j {
                    return AnyOrderResult::Ok;
                }
            }
            AnyOrderResult::WrongOrder
        },
        || ProofCheckError::Other(format!("Expected one of {{{prem1}, {prem2}}} to be the negation of the other.",)),
    )
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
        }
        .into()
    }
    fn get_classifications(&self) -> HashSet<RuleClassification> {
        use PrepositionalInference::*;
        use RuleClassification::*;
        let mut ret = HashSet::new();
        match self {
            Reit => {
                ret.insert(MiscInference);
            }
            AndIntro | OrIntro | ImpIntro | NotIntro | ContradictionIntro | BiconditionalIntro | EquivalenceIntro => {
                ret.insert(Introduction);
            }
            AndElim | OrElim | ImpElim | NotElim | ContradictionElim | BiconditionalElim | EquivalenceElim => {
                ret.insert(Elimination);
            }
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

    #[allow(clippy::redundant_closure)]
    fn check<P: Proof>(self, p: &P, conclusion: Expr, deps: Vec<PjRef<P>>, sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<PjRef<P>, P::SubproofReference>> {
        use PrepositionalInference::*;
        use ProofCheckError::*;

        match self {
            Reit => {
                let prem = p.lookup_expr_or_die(&deps[0])?;
                if prem == conclusion {
                    Ok(())
                } else {
                    Err(DoesNotOccur(conclusion, prem))
                }
            }
            AndIntro => {
                if let Expr::Assoc { op: Op::And, ref exprs } = conclusion {
                    // ensure each dep appears in exprs
                    for d in deps.iter() {
                        let e = p.lookup_expr_or_die(d)?;
                        if !exprs.iter().any(|x| x == &e) {
                            return Err(DoesNotOccur(e, conclusion.clone()));
                        }
                    }
                    // ensure each expr has a dep
                    for e in exprs {
                        if !deps.iter().any(|d| p.lookup_expr(d).map(|de| &de == e).unwrap_or(false)) {
                            return Err(DepDoesNotExist(e.clone(), false));
                        }
                    }
                    Ok(())
                } else {
                    Err(ConclusionOfWrongForm(Expr::assocplaceholder(Op::And)))
                }
            }
            AndElim => {
                let prem = p.lookup_expr_or_die(&deps[0])?;
                if let Expr::Assoc { op: Op::And, ref exprs } = prem {
                    let premise_set: HashSet<_> = exprs.iter().collect();
                    // If the conclusion is a conjunction of many terms
                    if let Expr::Assoc { op: Op::And, ref exprs } = conclusion {
                        // Check if every term in the conclusion exists in the premise
                        for ce in exprs.iter() {
                            if !premise_set.contains(ce) {
                                return Err(DoesNotOccur(conclusion.clone(), prem.clone()));
                            }
                        }
                        Ok(())
                    } else {
                        // If the conclusion is a single term
                        if premise_set.contains(&conclusion) {
                            Ok(())
                        } else {
                            Err(DoesNotOccur(conclusion.clone(), prem.clone()))
                        }
                    }
                } else {
                    Err(DepDoesNotExist(Expr::assocplaceholder(Op::And), true))
                }
            }
            OrIntro => {
                let prem = p.lookup_expr_or_die(&deps[0])?;
                if let Expr::Assoc { op: Op::Or, ref exprs } = conclusion {
                    if !exprs.iter().any(|e| e == &prem) {
                        return Err(DoesNotOccur(prem, conclusion.clone()));
                    }
                    Ok(())
                } else {
                    Err(ConclusionOfWrongForm(Expr::assocplaceholder(Op::Or)))
                }
            }
            OrElim => {
                let prem = p.lookup_expr_or_die(&deps[0])?;
                if let Expr::Assoc { op: Op::Or, ref exprs } = prem {
                    let sproofs = sdeps.into_iter().map(|r| p.lookup_subproof_or_die(&r)).collect::<Result<Vec<_>, _>>()?;
                    // if not all the subproofs have lines whose expressions contain the conclusion, return an error
                    let all_sproofs_have_conclusion = sproofs.iter().all(|sproof| sproof.lines().into_iter().filter_map(|x| x.get::<P::JustificationReference, _>().and_then(|y| p.lookup_step(y)).map(|y| y.0)).any(|c| c == conclusion));
                    if !all_sproofs_have_conclusion {
                        return Err(DepDoesNotExist(conclusion, false));
                    }
                    if let Some(e) = exprs.iter().find(|&e| !sproofs.iter().any(|sproof| sproof.premises().into_iter().next().and_then(|r| p.lookup_premise(&r)).map(|x| x == *e) == Some(true))) {
                        return Err(DepDoesNotExist(e.clone(), false));
                    }
                    Ok(())
                } else {
                    Err(DepDoesNotExist(Expr::assocplaceholder(Op::Or), true))
                }
            }
            ImpIntro => {
                let sproof = p.lookup_subproof_or_die(&sdeps[0])?;
                // TODO: allow generalized premises
                assert_eq!(sproof.premises().len(), 1);
                if let Expr::Impl { ref left, ref right } = conclusion {
                    let prem = sproof.premises().into_iter().map(|r| p.lookup_premise_or_die(&r)).collect::<Result<Vec<Expr>, _>>()?;
                    if **left != prem[0] {
                        return Err(DoesNotOccur(*left.clone(), prem[0].clone()));
                    }
                    let conc = sproof.lines().into_iter().filter_map(|x| x.get::<P::JustificationReference, _>().cloned()).map(|r| p.lookup_expr_or_die(&Coproduct::inject(r))).collect::<Result<Vec<Expr>, _>>()?;
                    if !conc.iter().any(|c| c == &**right) {
                        return Err(DepDoesNotExist(*right.clone(), false));
                    }
                    Ok(())
                } else {
                    Err(ConclusionOfWrongForm(Expr::impl_place_holder()))
                }
            }
            ImpElim => {
                let prem1 = p.lookup_expr_or_die(&deps[0])?;
                let prem2 = p.lookup_expr_or_die(&deps[1])?;
                either_order(
                    &prem1,
                    &prem2,
                    |i, j| {
                        if let Expr::Impl { ref left, ref right } = i {
                            //bad case, p -> q, a therefore --doesn't matter, nothing can be said
                            //with a
                            if **left != *j {
                                return AnyOrderResult::Err(DoesNotOccur(i.clone(), j.clone()));
                            }

                            //bad case, p -> q, p therefore a which does not follow
                            if **right != conclusion {
                                return AnyOrderResult::Err(DoesNotOccur(conclusion.clone(), *right.clone()));
                            }

                            //good case, p -> q, p therefore q
                            if **left == *j && **right == conclusion {
                                return AnyOrderResult::Ok;
                            }
                        }
                        AnyOrderResult::WrongOrder
                    },
                    || DepDoesNotExist(Expr::impl_place_holder(), true),
                )
            }
            NotIntro => {
                let sproof = p.lookup_subproof_or_die(&sdeps[0])?;
                // TODO: allow generalized premises
                assert_eq!(sproof.premises().len(), 1);
                if let Expr::Not { ref operand } = conclusion {
                    let prem = sproof.premises().into_iter().map(|r| p.lookup_premise_or_die(&r)).collect::<Result<Vec<Expr>, _>>()?;
                    if **operand != prem[0] {
                        return Err(DoesNotOccur(*operand.clone(), prem[0].clone()));
                    }
                    let conc = sproof.lines().into_iter().filter_map(|x| x.get::<P::JustificationReference, _>().cloned()).map(|r| p.lookup_expr_or_die(&Coproduct::inject(r))).collect::<Result<Vec<Expr>, _>>()?;
                    if !conc.iter().any(|x| *x == Expr::Contra) {
                        return Err(DepDoesNotExist(Expr::Contra, false));
                    }
                    Ok(())
                } else {
                    Err(ConclusionOfWrongForm(!Expr::var("_")))
                }
            }
            NotElim => {
                let prem = p.lookup_expr_or_die(&deps[0])?;
                if let Expr::Not { ref operand } = prem {
                    if let Expr::Not { ref operand } = **operand {
                        if **operand == conclusion {
                            return Ok(());
                        }
                        Err(ConclusionOfWrongForm(!(!(Expr::var("_")))))
                    } else {
                        Err(DepDoesNotExist(!(!(Expr::var("_"))), true))
                    }
                } else {
                    Err(DepDoesNotExist(!(!(Expr::var("_"))), true))
                }
            }
            ContradictionIntro => {
                if let Expr::Contra = conclusion {
                    let prem1 = p.lookup_expr_or_die(&deps[0])?;
                    let prem2 = p.lookup_expr_or_die(&deps[1])?;
                    do_expressions_contradict::<P>(&prem1, &prem2)
                } else {
                    Err(ConclusionOfWrongForm(Expr::Contra))
                }
            }
            ContradictionElim => {
                let prem = p.lookup_expr_or_die(&deps[0])?;
                if let Expr::Contra = prem {
                    Ok(())
                } else {
                    Err(DepOfWrongForm(prem, Expr::Contra))
                }
            }
            BiconditionalElim => {
                let prem1 = p.lookup_expr_or_die(&deps[0])?;
                let prem2 = p.lookup_expr_or_die(&deps[1])?;
                either_order(
                    &prem1,
                    &prem2,
                    |i, j| {
                        if let Expr::Assoc { op: Op::Bicon, ref exprs } = i {
                            let mut s = HashSet::new();
                            if let Expr::Assoc { op: Op::Bicon, ref exprs } = j {
                                s.extend(exprs.iter().cloned());
                            } else {
                                s.insert(j.clone());
                            }
                            for prem in s.iter() {
                                if !exprs.iter().any(|x| x == prem) {
                                    return AnyOrderResult::Err(DoesNotOccur(prem.clone(), i.clone()));
                                }
                            }
                            let terms = exprs.iter().filter(|x| !s.contains(x)).cloned().collect::<Vec<_>>();
                            let expected = if terms.len() == 1 { terms[0].clone() } else { Expr::assoc(Op::Bicon, &terms[..]) };
                            // TODO: maybe commutativity
                            if conclusion != expected {
                                return AnyOrderResult::Err(DoesNotOccur(conclusion.clone(), expected));
                            }
                            AnyOrderResult::Ok
                        } else {
                            AnyOrderResult::WrongOrder
                        }
                    },
                    || DepDoesNotExist(Expr::assocplaceholder(Op::Bicon), true),
                )
            }
            EquivalenceIntro | BiconditionalIntro => {
                let oper = if let EquivalenceIntro = self { Op::Equiv } else { Op::Bicon };
                if let Expr::Assoc { op, ref exprs } = conclusion {
                    if oper == op {
                        if let BiconditionalIntro = self {
                            if exprs.len() != 2 {
                                return Err(ConclusionOfWrongForm(Expr::Assoc { op: Op::Bicon, exprs: vec![Expr::var("_"), Expr::var("_")] }));
                            }
                        }
                        let prems = deps.into_iter().map(|r| p.lookup_expr_or_die(&r)).collect::<Result<Vec<Expr>, _>>()?;
                        let sproofs = sdeps.into_iter().map(|r| p.lookup_subproof_or_die(&r)).collect::<Result<Vec<_>, _>>()?;
                        let mut slab = HashMap::new();
                        let mut counter = 0;
                        let next: &mut dyn FnMut() -> _ = &mut || {
                            counter += 1;
                            counter
                        };
                        let mut g = DiGraphMap::new();
                        for prem in prems.iter() {
                            match prem {
                                Expr::Assoc { op, ref exprs } if &oper == op => {
                                    for e1 in exprs.iter() {
                                        for e2 in exprs.iter() {
                                            slab.entry(e1.clone()).or_insert_with(|| next());
                                            slab.entry(e2.clone()).or_insert_with(|| next());
                                            g.add_edge(slab[e1], slab[e2], ());
                                        }
                                    }
                                }
                                Expr::Impl { ref left, ref right } => {
                                    slab.entry(*left.clone()).or_insert_with(|| next());
                                    slab.entry(*right.clone()).or_insert_with(|| next());
                                    g.add_edge(slab[left], slab[right], ());
                                }
                                _ => return Err(OneOf(btreeset![DepOfWrongForm(prem.clone(), Expr::assocplaceholder(oper)), DepOfWrongForm(prem.clone(), Expr::impl_place_holder()),])),
                            }
                        }
                        for sproof in sproofs.iter() {
                            assert_eq!(sproof.premises().len(), 1);
                            let prem = sproof.lookup_premise_or_die(&sproof.premises()[0])?;
                            slab.entry(prem.clone()).or_insert_with(|| next());
                            for r in sproof.exprs() {
                                let e = sproof.lookup_expr_or_die(&r)?.clone();
                                slab.entry(e.clone()).or_insert_with(|| next());
                                g.add_edge(slab[&prem], slab[&e], ());
                            }
                        }
                        let rslab = slab.into_iter().map(|(k, v)| (v, k)).collect::<HashMap<_, _>>();
                        let sccs = tarjan_scc(&g).iter().map(|x| x.iter().map(|i| rslab[i].clone()).collect()).collect::<Vec<HashSet<_>>>();
                        println!("sccs: {sccs:?}");
                        if sccs.iter().any(|s| exprs.iter().all(|e| s.contains(e))) {
                            return Ok(());
                        } else {
                            let mut errstring = "Not all elements of the conclusion are mutually implied by the premises.".to_string();
                            if let Some(e) = exprs.iter().find(|e| !sccs.iter().any(|s| s.contains(e))) {
                                errstring += &format!("\nThe expression {e} occurs in the conclusion, but not in any of the premises.");
                            } else {
                                exprs.iter().any(|e1| {
                                    exprs.iter().any(|e2| {
                                        for i in 0..sccs.len() {
                                            if sccs[i].contains(e2) && !sccs[i..].iter().any(|s| s.contains(e1)) {
                                                errstring += &format!("\nThe expression {e2} is unreachable from {e1} by the premises.");
                                                return true;
                                            }
                                        }
                                        false
                                    })
                                });
                            }
                            return Err(Other(errstring));
                        }
                    }
                }
                Err(ConclusionOfWrongForm(Expr::assocplaceholder(oper)))
            }
            EquivalenceElim => {
                let prem1 = p.lookup_expr_or_die(&deps[0])?;
                let prem2 = p.lookup_expr_or_die(&deps[1])?;
                either_order(
                    &prem1,
                    &prem2,
                    |i, j| {
                        if let Expr::Assoc { op: Op::Equiv, ref exprs } = i {
                            // TODO: Negation?
                            if !exprs.iter().any(|x| x == j) {
                                return AnyOrderResult::Err(DoesNotOccur(j.clone(), i.clone()));
                            }
                            if !exprs.iter().any(|x| x == &conclusion) {
                                return AnyOrderResult::Err(DoesNotOccur(conclusion.clone(), i.clone()));
                            }
                            AnyOrderResult::Ok
                        } else {
                            AnyOrderResult::WrongOrder
                        }
                    },
                    || DepDoesNotExist(Expr::assocplaceholder(Op::Equiv), true),
                )
            }
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
        }
        .into()
    }
    fn get_classifications(&self) -> HashSet<RuleClassification> {
        use PredicateInference::*;
        use RuleClassification::*;
        let mut ret = HashSet::new();
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
    fn check<P: Proof>(self, p: &P, conclusion: Expr, deps: Vec<PjRef<P>>, sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<PjRef<P>, P::SubproofReference>> {
        use PredicateInference::*;
        use ProofCheckError::*;
        fn unifies_wrt_var<P: Proof>(e1: &Expr, e2: &Expr, var: &str) -> Result<Expr, ProofCheckError<PjRef<P>, P::SubproofReference>> {
            let constraints = vec![Constraint::Equal(e1.clone(), e2.clone())].into_iter().collect();
            if let Some(substitutions) = crate::expr::unify(constraints) {
                if substitutions.0.is_empty() {
                    assert_eq!(e1, e2);
                    Ok(Expr::var(var))
                } else if substitutions.0.len() == 1 {
                    if substitutions.0[0].0 == var {
                        assert_eq!(&crate::expr::subst(e1.clone(), &substitutions.0[0].0, substitutions.0[0].1.clone()), e2);
                        Ok(substitutions.0[0].1.clone())
                    } else {
                        // TODO: standardize non-string error messages for unification-based rules
                        Err(Other(format!("Attempted to substitute for a variable other than the binder: {}", substitutions.0[0].0)))
                    }
                } else {
                    Err(Other(format!("More than one variable was substituted: {substitutions:?}")))
                }
            } else {
                Err(Other(format!("No substitution found between {e1} and {e2}.")))
            }
        }
        fn generalizable_variable_counterexample<P: Proof>(sproof: &P, line: PjRef<P>, var: &str) -> Option<Expr> {
            let contained = sproof.contained_justifications(true);
            //println!("gvc contained {:?}", contained.iter().map(|x| sproof.lookup_expr(&x)).collect::<Vec<_>>());
            let reachable = sproof.transitive_dependencies(line);
            //println!("gvc reachable {:?}", reachable.iter().map(|x| sproof.lookup_expr(&x)).collect::<Vec<_>>());
            let outside = reachable.difference(&contained);
            //println!("gvc outside {:?}", outside.clone().map(|x| sproof.lookup_expr(&x)).collect::<Vec<_>>());
            outside.filter_map(|x| sproof.lookup_expr(x)).find(|e| crate::expr::free_vars(e).contains(var))
        }
        match self {
            ForallIntro => {
                let sproof = p.lookup_subproof_or_die(&sdeps[0])?;
                if let Expr::Quant { kind: QuantKind::Forall, name, body } = &conclusion {
                    for (r, expr) in sproof.exprs().into_iter().map(|r| sproof.lookup_expr_or_die(&r).map(|e| (r, e))).collect::<Result<Vec<_>, _>>()? {
                        if let Ok(Expr::Var { name: constant }) = unifies_wrt_var::<P>(body, &expr, name) {
                            println!("ForallIntro constant {constant:?}");
                            if let Some(dangling) = generalizable_variable_counterexample(&sproof, r.clone(), &constant) {
                                return Err(Other(format!("The constant {constant} occurs in dependency {dangling} that's outside the subproof.")));
                            } else {
                                let expected = crate::expr::subst(*body.clone(), &constant, Expr::var(name));
                                if expected != **body {
                                    return Err(Other(format!("Not all free occurrences of {constant} are replaced with {name} in {body}.")));
                                }
                                let tdeps = sproof.transitive_dependencies(r);
                                if sproof.premises().into_iter().any(|subprem| tdeps.contains(&Coproduct::inject(subprem))) {
                                    return Err(Other("ForallIntro should not make use of the subproof's premises.".to_string()));
                                }
                                return Ok(());
                            }
                        }
                    }
                    Err(Other(format!("Couldn't find a subproof line that unifies with the conclusion ({conclusion}).")))
                } else {
                    Err(ConclusionOfWrongForm(Expr::quant_placeholder(QuantKind::Forall)))
                }
            }
            ForallElim => {
                let prem = p.lookup_expr_or_die(&deps[0])?;
                if let Expr::Quant { kind: QuantKind::Forall, ref name, ref body } = prem {
                    unifies_wrt_var::<P>(body, &conclusion, name)?;
                    Ok(())
                } else {
                    Err(DepOfWrongForm(prem, Expr::quant_placeholder(QuantKind::Forall)))
                }
            }
            ExistsIntro => {
                if let Expr::Quant { kind: QuantKind::Exists, ref name, ref body } = conclusion {
                    let prem = p.lookup_expr_or_die(&deps[0])?;
                    unifies_wrt_var::<P>(body, &prem, name)?;
                    Ok(())
                } else {
                    Err(ConclusionOfWrongForm(Expr::quant_placeholder(QuantKind::Exists)))
                }
            }
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
                let prem = p.lookup_expr_or_die(&deps[0])?;
                let sproof = p.lookup_subproof_or_die(&sdeps[0])?;
                let skolemname = {
                    if let Expr::Quant { kind: QuantKind::Exists, ref name, ref body } = prem {
                        let subprems = sproof.premises();
                        if subprems.len() != 1 {
                            // TODO: can/should this be generalized?
                            return Err(Other(format!("Subproof has {} premises, expected 1.", subprems.len())));
                        }
                        let subprem = p.lookup_premise_or_die(&subprems[0])?;
                        if let Ok(Expr::Var { name: skolemname }) = unifies_wrt_var::<P>(body, &subprem, name) {
                            skolemname
                        } else {
                            return Err(Other(format!("Premise {subprem} doesn't unify with the body of dependency {prem}")));
                        }
                    } else {
                        return Err(DepOfWrongForm(prem, Expr::quant_placeholder(QuantKind::Exists)));
                    }
                };
                for (r, expr) in sproof.exprs().into_iter().map(|r| sproof.lookup_expr_or_die(&r).map(|e| (r, e))).collect::<Result<Vec<_>, _>>()? {
                    if expr == conclusion {
                        println!("ExistsElim conclusion {conclusion:?} skolemname {skolemname:?}");
                        if let Some(dangling) = generalizable_variable_counterexample(&sproof, r, &skolemname) {
                            return Err(Other(format!("The skolem constant {skolemname} occurs in dependency {dangling} that's outside the subproof.")));
                        }
                        if crate::expr::free_vars(&conclusion).contains(&skolemname) {
                            return Err(Other(format!("The skolem constant {skolemname} escapes to the conclusion {conclusion}.")));
                        }
                        return Ok(());
                    }
                }
                Err(Other(format!("Couldn't find a subproof line equal to the conclusion ({conclusion}).")))
            }
        }
    }
}

fn check_by_normalize_first_expr<F, P: Proof>(p: &P, deps: Vec<PjRef<P>>, conclusion: Expr, commutative: bool, normalize_fn: F, restriction: &str) -> Result<(), ProofCheckError<PjRef<P>, P::SubproofReference>>
where
    F: Fn(Expr) -> Expr,
{
    let premise = p.lookup_expr_or_die(&deps[0])?;
    let mut p = normalize_fn(premise);
    let mut q = normalize_fn(conclusion);
    if commutative {
        p = p.sort_commutative_ops(restriction);
        q = q.sort_commutative_ops(restriction);
    }
    if p == q {
        Ok(())
    } else {
        Err(ProofCheckError::Other(format!("{p} and {q} are not equal.")))
    }
}

fn check_by_rewrite_rule_confl<P: Proof>(p: &P, deps: Vec<PjRef<P>>, conclusion: Expr, commutative: bool, rule: &RewriteRule, restriction: &str) -> Result<(), ProofCheckError<PjRef<P>, P::SubproofReference>> {
    check_by_normalize_first_expr(p, deps, conclusion, commutative, |e| rule.reduce(e), restriction)
}

fn check_by_rewrite_rule_non_confl<P: Proof>(p: &P, deps: Vec<PjRef<P>>, conclusion: Expr, commutative: bool, rule: &RewriteRule, restriction: &str) -> Result<(), ProofCheckError<PjRef<P>, P::SubproofReference>> {
    let premise = p.lookup_expr_or_die(&deps[0])?;
    let premise_set = rule.reduce_set(premise.clone());
    let conclusion_set = rule.reduce_set(conclusion.clone());
    let (premise_set, conclusion_set) = if commutative {
        let sort_ops = |set: HashSet<Expr>| set.into_iter().map(|expr| expr.sort_commutative_ops(restriction)).collect();
        (sort_ops(premise_set), sort_ops(conclusion_set))
    } else {
        (premise_set, conclusion_set)
    };
    // The premise and conclusion are equal if the set intersection is nonempty
    let is_eq = premise_set.intersection(&conclusion_set).next().is_some();
    if is_eq {
        Ok(())
    } else {
        Err(ProofCheckError::Other(format!("{premise} and {conclusion} are not equal.")))
    }
}

impl RuleT for BooleanEquivalence {
    fn get_name(&self) -> String {
        use BooleanEquivalence::*;
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
        }
        .into()
    }
    fn get_classifications(&self) -> HashSet<RuleClassification> {
        [RuleClassification::BooleanEquivalence].iter().cloned().collect()
    }
    fn num_deps(&self) -> Option<usize> {
        Some(1)
    } // all equivalence rules rewrite a single statement
    fn num_subdeps(&self) -> Option<usize> {
        Some(0)
    }
    fn check<P: Proof>(self, p: &P, conclusion: Expr, deps: Vec<PjRef<P>>, _sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<PjRef<P>, P::SubproofReference>> {
        use BooleanEquivalence::*;
        match self {
            DeMorgan => check_by_normalize_first_expr(p, deps, conclusion, false, |e| e.normalize_demorgans(), "none"),
            Association => check_by_normalize_first_expr(p, deps, conclusion, false, |e| e.combine_associative_ops("bool"), "bool"),
            Commutation => check_by_normalize_first_expr(p, deps, conclusion, false, |e| e.sort_commutative_ops("bool"), "bool"),
            Idempotence => check_by_normalize_first_expr(p, deps, conclusion, false, |e| e.normalize_idempotence(), "none"),
            DoubleNegation => check_by_rewrite_rule_confl(p, deps, conclusion, false, &equivs::DOUBLE_NEGATION, "none"),
            // Distribution and Reduction have outputs containing binops that need commutative sorting
            // because we can't expect people to know the specific order of outputs that our definition
            // of the rules uses
            Distribution => check_by_rewrite_rule_confl(p, deps, conclusion, true, &equivs::DISTRIBUTION, "none"),
            Complement => check_by_rewrite_rule_confl(p, deps, conclusion, false, &equivs::COMPLEMENT, "none"),
            Identity => check_by_rewrite_rule_confl(p, deps, conclusion, false, &equivs::IDENTITY, "none"),
            Annihilation => check_by_rewrite_rule_confl(p, deps, conclusion, false, &equivs::ANNIHILATION, "none"),
            Inverse => check_by_rewrite_rule_confl(p, deps, conclusion, false, &equivs::INVERSE, "none"),
            Absorption => check_by_rewrite_rule_confl(p, deps, conclusion, false, &equivs::ABSORPTION, "none"),
            Reduction => check_by_rewrite_rule_non_confl(p, deps, conclusion, true, &equivs::REDUCTION, "none"),
            Adjacency => check_by_rewrite_rule_confl(p, deps, conclusion, false, &equivs::ADJACENCY, "none"),
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
            Contraposition => "Contraposition",
            Currying => "Exportation",
            ConditionalDistribution => "Conditional Distribution",
            ConditionalReduction => "Conditional Reduction",
            KnightsAndKnaves => "Knights and Knaves",
            ConditionalIdempotence => "Conditional Idempotence",
        }
        .into()
    }
    fn get_classifications(&self) -> HashSet<RuleClassification> {
        [RuleClassification::ConditionalEquivalence].iter().cloned().collect()
    }
    fn num_deps(&self) -> Option<usize> {
        Some(1)
    } // all equivalence rules rewrite a single statement
    fn num_subdeps(&self) -> Option<usize> {
        Some(0)
    }
    fn check<P: Proof>(self, p: &P, conclusion: Expr, deps: Vec<PjRef<P>>, _sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<PjRef<P>, P::SubproofReference>> {
        use ConditionalEquivalence::*;
        match self {
            Complement => check_by_rewrite_rule_confl(p, deps, conclusion, false, &equivs::CONDITIONAL_COMPLEMENT, "none"),
            Identity => check_by_rewrite_rule_confl(p, deps, conclusion, false, &equivs::CONDITIONAL_IDENTITY, "none"),
            Annihilation => check_by_rewrite_rule_confl(p, deps, conclusion, false, &equivs::CONDITIONAL_ANNIHILATION, "none"),
            Implication => check_by_rewrite_rule_confl(p, deps, conclusion, false, &equivs::CONDITIONAL_IMPLICATION, "none"),
            Contraposition => check_by_rewrite_rule_confl(p, deps, conclusion, false, &equivs::CONDITIONAL_CONTRAPOSITION, "none"),
            Currying => check_by_rewrite_rule_confl(p, deps, conclusion, false, &equivs::CONDITIONAL_CURRYING, "none"),
            ConditionalDistribution => check_by_rewrite_rule_confl(p, deps, conclusion, true, &equivs::CONDITIONAL_DISTRIBUTION, "none"),
            ConditionalReduction => check_by_rewrite_rule_confl(p, deps, conclusion, true, &equivs::CONDITIONAL_REDUCTION, "none"),
            KnightsAndKnaves => check_by_rewrite_rule_confl(p, deps, conclusion, true, &equivs::KNIGHTS_AND_KNAVES, "none"),
            ConditionalIdempotence => check_by_rewrite_rule_confl(p, deps, conclusion, true, &equivs::CONDITIONAL_IDEMPOTENCE, "none"),
        }
    }
}

impl RuleT for BiconditionalEquivalence {
    fn get_name(&self) -> String {
        use BiconditionalEquivalence::*;
        match self {
            BiconditionalAssociation => "Biconditional Association",
            BiconditionalCommutation => "Biconditional Commutation",
            BiconditionalEquivalenceRule => "Biconditional Equivalence",
            BiconditionalNegation => "Biconditional Negation",
            BiconditionalSubstitution => "Biconditional Substitution",
        }
        .into()
    }
    fn get_classifications(&self) -> HashSet<RuleClassification> {
        [RuleClassification::BiconditionalEquivalence].iter().cloned().collect()
    }
    fn num_deps(&self) -> Option<usize> {
        Some(1)
    } // all equivalence rules rewrite a single statement
    fn num_subdeps(&self) -> Option<usize> {
        Some(0)
    }
    fn check<P: Proof>(self, p: &P, conclusion: Expr, deps: Vec<PjRef<P>>, _sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<PjRef<P>, P::SubproofReference>> {
        use BiconditionalEquivalence::*;
        match self {
            BiconditionalAssociation => check_by_normalize_first_expr(p, deps, conclusion, false, |e| e.combine_associative_ops("bicon"), "bicon"),
            BiconditionalCommutation => check_by_normalize_first_expr(p, deps, conclusion, false, |e| e.sort_commutative_ops("bicon"), "bicon"),
            BiconditionalEquivalenceRule => check_by_rewrite_rule_confl(p, deps, conclusion, false, &equivs::BICONDITIONAL_EQUIVALENCE, "none"),
            BiconditionalNegation => check_by_rewrite_rule_confl(p, deps, conclusion, true, &equivs::BICONDITIONAL_NEGATION, "none"),
            BiconditionalSubstitution => check_by_rewrite_rule_confl(p, deps, conclusion, true, &equivs::BICONDITIONAL_SUBSTITUTION, "none"),
        }
    }
}

impl RuleT for RedundantPrepositionalInference {
    fn get_name(&self) -> String {
        use RedundantPrepositionalInference::*;
        match self {
            ModusTollens => "Modus Tollens",
            HypotheticalSyllogism => "Hypothetical Syllogism",
            DisjunctiveSyllogism => "Disjunctive Syllogism",
            ExcludedMiddle => "Excluded Middle",
            ConstructiveDilemma => "Constructive Dilemma",
        }
        .into()
    }
    fn get_classifications(&self) -> HashSet<RuleClassification> {
        [RuleClassification::MiscInference].iter().cloned().collect()
    }
    fn num_deps(&self) -> Option<usize> {
        use RedundantPrepositionalInference::*;
        match self {
            ModusTollens | HypotheticalSyllogism | DisjunctiveSyllogism => Some(2),
            ExcludedMiddle => Some(0),
            ConstructiveDilemma => Some(3),
        }
    }
    fn num_subdeps(&self) -> Option<usize> {
        Some(0)
    }
    fn check<P: Proof>(self, proof: &P, conclusion: Expr, deps: Vec<PjRef<P>>, sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<PjRef<P>, P::SubproofReference>> {
        use ProofCheckError::*;
        use RedundantPrepositionalInference::*;

        assert!(sdeps.is_empty());
        match self {
            ModusTollens => {
                // P -> Q, ~Q
                // ----------
                // ~P
                let dep_0 = proof.lookup_expr_or_die(&deps[0])?;
                let dep_1 = proof.lookup_expr_or_die(&deps[1])?;
                either_order(
                    &dep_0,
                    &dep_1,
                    |dep_0, dep_1| {
                        if let Expr::Impl { left: p, right: q } = dep_0 {
                            let not_p = !(*p.clone());
                            let not_q = !(*q.clone());
                            if not_q != *dep_1 {
                                AnyOrderResult::Err(DoesNotOccur(not_q, dep_1.clone()))
                            } else if not_p != conclusion {
                                AnyOrderResult::Err(DoesNotOccur(not_p, conclusion.clone()))
                            } else {
                                AnyOrderResult::Ok
                            }
                        } else {
                            AnyOrderResult::WrongOrder
                        }
                    },
                    || DepDoesNotExist(Expr::impl_place_holder(), true),
                )
            }
            HypotheticalSyllogism => {
                // P -> Q, Q -> R
                // --------------
                // P -> R
                let dep_0 = proof.lookup_expr_or_die(&deps[0])?;
                let dep_1 = proof.lookup_expr_or_die(&deps[1])?;
                either_order(
                    &dep_0,
                    &dep_1,
                    |dep_0, dep_1| {
                        if let (Expr::Impl { left: p_0, right: q_0 }, Expr::Impl { left: q_1, right: r_0 }, Expr::Impl { left: p_1, right: r_1 }) = (dep_0, dep_1, &conclusion) {
                            if p_0 != p_1 {
                                AnyOrderResult::Err(DoesNotOccur(*p_0.clone(), *p_1.clone()))
                            } else if q_0 != q_1 {
                                AnyOrderResult::Err(DoesNotOccur(*q_0.clone(), *q_1.clone()))
                            } else if r_0 != r_1 {
                                AnyOrderResult::Err(DoesNotOccur(*r_0.clone(), *r_1.clone()))
                            } else {
                                AnyOrderResult::Ok
                            }
                        } else {
                            AnyOrderResult::WrongOrder
                        }
                    },
                    || DepDoesNotExist(Expr::impl_place_holder(), true),
                )
            }
            DisjunctiveSyllogism => {
                // P | Q, ~P
                // ---------
                // Q
                let dep_0 = proof.lookup_expr_or_die(&deps[0])?;
                let dep_1 = proof.lookup_expr_or_die(&deps[1])?;
                either_order(
                    &dep_0,
                    &dep_1,
                    |dep_0, dep_1| {
                        if let Expr::Assoc { op: _, exprs: expression } = dep_0 {
                            let p = expression[0].clone();
                            let q = expression[1].clone();
                            let res = either_order(
                                &p,
                                &q,
                                |p, q| {
                                    if let Expr::Not { operand: dep1_body } = dep_1 {
                                        if p != &**dep1_body {
                                            AnyOrderResult::Err(DoesNotOccur(p.clone(), dep_1.clone()))
                                        } else if q.clone() != conclusion {
                                            AnyOrderResult::Err(DoesNotOccur(q.clone(), conclusion.clone()))
                                        } else {
                                            AnyOrderResult::Ok
                                        }
                                    } else {
                                        AnyOrderResult::WrongOrder
                                    }
                                },
                                || DepDoesNotExist(Expr::assocplaceholder(Op::Or), true),
                            );
                            match res {
                                Ok(()) => AnyOrderResult::Ok,
                                Err(e) => AnyOrderResult::Err(e),
                            }
                        } else {
                            AnyOrderResult::WrongOrder
                        }
                    },
                    || DepDoesNotExist(Expr::assocplaceholder(Op::Or), true),
                )
            }
            ExcludedMiddle => {
                // A | ~A
                let wrong_form_err = ConclusionOfWrongForm(Expr::var("_") | !Expr::var("_"));
                let operands = match conclusion {
                    Expr::Assoc { op: Op::Or, exprs } => exprs,
                    _ => return Err(wrong_form_err),
                };

                let (a, not_a) = match operands.as_slice() {
                    [a, not_a] => (a, not_a),
                    _ => return Err(wrong_form_err),
                };

                let not_a_0 = not_a.clone();
                let not_a_1 = !(a.clone());

                if not_a_0 == not_a_1 {
                    Ok(())
                } else {
                    Err(DoesNotOccur(not_a_0, not_a_1))
                }
            }
            ConstructiveDilemma => {
                // P -> Q, R -> S, P | R
                // ---------------------
                // Q | S
                let deps = deps.into_iter().map(|dep| proof.lookup_expr_or_die(&dep)).collect::<Result<Vec<Expr>, _>>()?;
                any_order(
                    &deps,
                    |deps| {
                        let (dep_0, dep_1, dep_2) = deps.iter().collect_tuple().unwrap();
                        if let (Expr::Impl { left: p_0, right: q_0 }, Expr::Impl { left: r_0, right: s_0 }, Expr::Assoc { op: Op::Or, exprs: p_r }, Expr::Assoc { op: Op::Or, exprs: q_s }) = (dep_0, dep_1, dep_2, &conclusion) {
                            let p_0 = *p_0.clone();
                            let q_0 = *q_0.clone();
                            let r_0 = *r_0.clone();
                            let s_0 = *s_0.clone();
                            let dep_2 = (*dep_2).clone();
                            let conclusion = conclusion.clone();

                            let (p_1, r_1) = match p_r.iter().collect_tuple() {
                                Some((p_1, r_1)) => (p_1, r_1),
                                None => return AnyOrderResult::Err(DoesNotOccur(p_0 | r_0, dep_2)),
                            };
                            let (q_1, s_1) = match q_s.iter().collect_tuple() {
                                Some((q_1, s_1)) => (q_1, s_1),
                                None => return AnyOrderResult::Err(DoesNotOccur(q_0 | s_0, conclusion)),
                            };

                            let p_1 = p_1.clone();
                            let q_1 = q_1.clone();
                            let r_1 = r_1.clone();
                            let s_1 = s_1.clone();

                            if p_0 != p_1 {
                                AnyOrderResult::Err(DoesNotOccur(p_0, p_1))
                            } else if q_0 != q_1 {
                                AnyOrderResult::Err(DoesNotOccur(q_0, q_1))
                            } else if r_0 != r_1 {
                                AnyOrderResult::Err(DoesNotOccur(r_0, r_1))
                            } else if s_0 != s_1 {
                                AnyOrderResult::Err(DoesNotOccur(s_0, s_1))
                            } else {
                                AnyOrderResult::Ok
                            }
                        } else {
                            AnyOrderResult::WrongOrder
                        }
                    },
                    || OneOf(btreeset![DepDoesNotExist(Expr::impl_place_holder(), true), DepDoesNotExist(Expr::assocplaceholder(Op::Or), true),]),
                )
            }
        }
    }
}

impl RuleT for AutomationRelatedRules {
    fn get_name(&self) -> String {
        match self {
            AutomationRelatedRules::Resolution => "Resolution",
            AutomationRelatedRules::TruthFunctionalConsequence => "Truth-Functional Consequence",
        }
        .into()
    }
    fn get_classifications(&self) -> HashSet<RuleClassification> {
        [RuleClassification::MiscInference].iter().cloned().collect()
    }
    fn num_deps(&self) -> Option<usize> {
        match self {
            AutomationRelatedRules::Resolution => Some(2),
            AutomationRelatedRules::TruthFunctionalConsequence => None,
        }
    }
    fn num_subdeps(&self) -> Option<usize> {
        match self {
            AutomationRelatedRules::Resolution | AutomationRelatedRules::TruthFunctionalConsequence => Some(0),
        }
    }
    fn check<P: Proof>(self, p: &P, conclusion: Expr, deps: Vec<PjRef<P>>, _sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<PjRef<P>, P::SubproofReference>> {
        match self {
            AutomationRelatedRules::Resolution => {
                let prem0 = p.lookup_expr_or_die(&deps[0])?;
                let prem1 = p.lookup_expr_or_die(&deps[1])?;
                let mut premise_disjuncts = HashSet::new();
                premise_disjuncts.extend(prem0.disjuncts());
                premise_disjuncts.extend(prem1.disjuncts());
                let conclusion_disjuncts = conclusion.disjuncts().into_iter().collect::<HashSet<_>>();
                let mut remainder = premise_disjuncts.difference(&conclusion_disjuncts).cloned().collect::<Vec<Expr>>();
                //println!("resolution remainder of {:?} and {:?} is {:?}", premise_disjuncts, conclusion_disjuncts, remainder);
                remainder.sort();
                match &remainder[..] {
                    [e1, e2] => do_expressions_contradict::<P>(e1, e2),
                    _ => {
                        let mut pretty_remainder: String = "{".into();
                        for (i, expr) in remainder.iter().enumerate() {
                            pretty_remainder += &format!("{}{}", expr, if i != remainder.len() - 1 { ", " } else { "" });
                        }
                        pretty_remainder += "}";
                        Err(ProofCheckError::Other(format!("Difference between premise disjuncts and conclusion disjuncts ({pretty_remainder}) should be exactly 2 expressions that produce a contradiction.")))
                    }
                }
            }
            AutomationRelatedRules::TruthFunctionalConsequence => {
                // Closure for making CNF conversion errors
                let cnf_error = || ProofCheckError::Other("Failed converting to CNF; the propositions for this rule should not use quantifiers, arithmetic, or application.".to_string());

                // Closure to convert expression into CNF and change to result type
                let into_cnf = |expr: Expr| expr.into_cnf().ok_or_else(cnf_error);

                // Convert the premises to a single expression by AND-ing them together
                let premises = deps.into_iter().map(|dep| p.lookup_expr_or_die(&dep)).collect::<Result<Vec<Expr>, _>>()?;
                let premise = Expr::Assoc { op: Op::And, exprs: premises };

                // Create `varisat` formula of `~(P -> Q)`. If this is
                // unsatisfiable, then we've proven `P -> Q`.
                let sat = !(Expr::implies(premise, conclusion));
                let (sat, vars) = into_cnf(sat)?.to_varisat();
                let mut solver = varisat::Solver::new();
                solver.add_formula(&sat);

                // Does not panic on the default config
                solver.solve().expect("varisat error");

                // If unsatisfiable, we know `P -> Q`
                match solver.model() {
                    Some(model) => {
                        // Satisfiable, so `P -> Q` is false. The counterexample is `model`.

                        // Convert model to human-readable variable assignments
                        // for an error message
                        let model = model
                            .into_iter()
                            .map(|lit| {
                                let name = vars.get(&lit.var()).expect("taut con vars map error");
                                let val = if lit.is_positive() { 'T' } else { 'F' };
                                format!("{name} = {val}")
                            })
                            .collect::<Vec<String>>()
                            .join(", ");

                        Err(ProofCheckError::Other(format!("Not true by truth-functional consequence; Counterexample: {model}")))
                    }
                    None => Ok(()),
                }
            }
        }
    }
}

impl RuleT for QuantifierEquivalence {
    fn get_name(&self) -> String {
        use QuantifierEquivalence::*;
        match self {
            QuantifierNegation => "Quantifier Negation",
            NullQuantification => "Null Quantification",
            ReplacingBoundVars => "Replacing Bound Variables",
            SwappingQuantifiers => "Swapping Quantifiers of Same Type",
            AristoteleanSquare => "Aristotelean Square of Opposition",
            QuantifierDistribution => "Quantifier Distribution",
            PrenexLaws => "Prenex Laws",
        }
        .into()
    }
    fn get_classifications(&self) -> HashSet<RuleClassification> {
        [RuleClassification::QuantifierEquivalence].iter().cloned().collect()
    }
    fn num_deps(&self) -> Option<usize> {
        Some(1)
    } // all equivalence rules rewrite a single statement
    fn num_subdeps(&self) -> Option<usize> {
        Some(0)
    }
    fn check<P: Proof>(self, p: &P, conclusion: Expr, deps: Vec<PjRef<P>>, _sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<PjRef<P>, P::SubproofReference>> {
        use QuantifierEquivalence::*;
        match self {
            QuantifierNegation => check_by_normalize_first_expr(p, deps, conclusion, false, Expr::negate_quantifiers, "none"),
            NullQuantification => check_by_normalize_first_expr(p, deps, conclusion, false, Expr::normalize_null_quantifiers, "none"),
            ReplacingBoundVars => check_by_normalize_first_expr(p, deps, conclusion, false, Expr::replacing_bound_vars, "none"),
            SwappingQuantifiers => check_by_normalize_first_expr(p, deps, conclusion, false, Expr::swap_quantifiers, "none"),
            AristoteleanSquare => check_by_normalize_first_expr(p, deps, conclusion, false, Expr::aristotelean_square, "none"),
            QuantifierDistribution => check_by_normalize_first_expr(p, deps, conclusion, false, Expr::quantifier_distribution, "none"),
            PrenexLaws => check_by_normalize_first_expr(p, deps, conclusion, false, Expr::normalize_prenex_laws, "none"),
        }
    }
}

impl RuleT for Induction {
    fn get_name(&self) -> String {
        match self {
            Induction::Weak => "Weak Induction",
            Induction::Strong => "Strong Induction",
        }
        .into()
    }

    fn get_classifications(&self) -> HashSet<RuleClassification> {
        hashset![RuleClassification::MiscInference]
    }

    fn num_deps(&self) -> Option<usize> {
        match self {
            Induction::Weak => Some(2),
            Induction::Strong => Some(1),
        }
    }

    fn num_subdeps(&self) -> Option<usize> {
        Some(0)
    }

    fn check<P: Proof>(self, p: &P, conclusion: Expr, deps: Vec<PjRef<P>>, _sdeps: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<PjRef<P>, P::SubproofReference>> {
        // Check conclusion
        let (quantified_var, property) = match &conclusion {
            Expr::Quant { kind: QuantKind::Forall, name, body } => (name, &**body),
            _ => return Err(ProofCheckError::ConclusionOfWrongForm(Expr::quant_placeholder(QuantKind::Forall))),
        };

        match self {
            Induction::Weak => {
                let prem1 = p.lookup_expr_or_die(&deps[0])?;
                let prem2 = p.lookup_expr_or_die(&deps[1])?;
                either_order(
                    &prem1,
                    &prem2,
                    |base_case, inductive_case| {
                        let zero = Expr::var("0");
                        let succ = Expr::var("s");

                        let expected_base_case = crate::expr::subst(property.clone(), quantified_var, zero);
                        if base_case != &expected_base_case {
                            return AnyOrderResult::WrongOrder;
                        }
                        let (induction_var, induction_impl) = if let Expr::Quant { kind: QuantKind::Forall, name, body } = inductive_case {
                            (name, &**body)
                        } else {
                            return AnyOrderResult::Err(ProofCheckError::DepOfWrongForm(inductive_case.clone(), Expr::quant_placeholder(QuantKind::Forall)));
                        };
                        if crate::expr::free_vars(&conclusion).contains(induction_var) {
                            return AnyOrderResult::Err(ProofCheckError::Other(format!("Induction variable '{induction_var}' is a free variable in the conclusion")));
                        }
                        let (inductive_premise, inductive_conclusion) = if let Expr::Impl { left, right } = induction_impl {
                            (&**left, &**right)
                        } else {
                            return AnyOrderResult::Err(ProofCheckError::DepOfWrongForm(inductive_case.clone(), Expr::forall("_", Expr::impl_place_holder())));
                        };
                        let expected_inductive_premise = crate::expr::subst(property.clone(), quantified_var, Expr::var(induction_var));
                        if inductive_premise != &expected_inductive_premise {
                            return AnyOrderResult::Err(ProofCheckError::DepOfWrongForm(inductive_premise.clone(), expected_inductive_premise));
                        }
                        let expected_inductive_conclusion = crate::expr::subst(property.clone(), quantified_var, Expr::apply(succ, &[Expr::var(induction_var)]));
                        if inductive_conclusion != &expected_inductive_conclusion {
                            return AnyOrderResult::Err(ProofCheckError::DepOfWrongForm(inductive_conclusion.clone(), expected_inductive_conclusion));
                        }
                        AnyOrderResult::Ok
                    },
                    || ProofCheckError::Other("Failed finding base case that matches conclusion".into()),
                )
            }
            Induction::Strong => {
                // ∀ n, (∀ x, x < n → property(x)) → property(n)
                // ----
                // ∀ quantified_var, property(quantified_var)
                let prem = p.lookup_expr_or_die(&deps[0])?;
                let (n, e) = if let Expr::Quant { kind: QuantKind::Forall, name, body } = prem { (name, *body) } else { return Err(ProofCheckError::DepOfWrongForm(prem, Expr::quant_placeholder(QuantKind::Forall))) };
                let (e, property_n) = if let Expr::Impl { left, right } = e { (*left, *right) } else { return Err(ProofCheckError::DepOfWrongForm(e, Expr::impl_place_holder())) };
                if crate::expr::free_vars(&conclusion).contains(&n) {
                    return Err(ProofCheckError::Other(format!("Variable '{n}' is free in '{conclusion}'")));
                }
                let expected_property_n = crate::expr::subst(property.clone(), quantified_var, Expr::var(&n));
                if property_n != expected_property_n {
                    return Err(ProofCheckError::DepOfWrongForm(property_n, expected_property_n));
                }
                let (x, e) = if let Expr::Quant { kind: QuantKind::Forall, name, body } = e { (name, *body) } else { return Err(ProofCheckError::DepOfWrongForm(e, Expr::quant_placeholder(QuantKind::Forall))) };
                let (x_lt_n, property_x) = if let Expr::Impl { left, right } = e {
                    (*left, *right)
                } else {
                    return Err(ProofCheckError::DepOfWrongForm(e, Expr::impl_place_holder()));
                };
                if crate::expr::free_vars(&conclusion).contains(&x) {
                    return Err(ProofCheckError::Other(format!("Variable '{x}' is free in '{conclusion}'")));
                }
                let expected_x_lt_n = Expr::apply(Expr::var("LessThan"), &[Expr::var(&x), Expr::var(&n)]);
                if x_lt_n != expected_x_lt_n {
                    return Err(ProofCheckError::DepOfWrongForm(x_lt_n, expected_x_lt_n));
                }
                let expected_property_x = crate::expr::subst(property.clone(), quantified_var, Expr::var(&x));
                if property_x != expected_property_x {
                    return Err(ProofCheckError::DepOfWrongForm(property_x, expected_property_x));
                }
                Ok(())
            }
        }
    }
}

impl RuleT for EmptyRule {
    fn get_name(&self) -> String {
        "Rule".to_string()
    }
    fn get_classifications(&self) -> HashSet<RuleClassification> {
        HashSet::new()
    }
    fn num_deps(&self) -> Option<usize> {
        None
    }
    fn num_subdeps(&self) -> Option<usize> {
        None
    }
    fn check<P: Proof>(self, _: &P, _: Expr, _: Vec<PjRef<P>>, _: Vec<P::SubproofReference>) -> Result<(), ProofCheckError<PjRef<P>, P::SubproofReference>> {
        Err(ProofCheckError::Other("No rule selected".to_string()))
    }
}

/// Helper type for `any_order()`. The `check_func` parameter of `any_order()`
/// returns this.
enum AnyOrderResult<R, S> {
    /// The rule checked successfully
    Ok,

    /// The rule check failed with an error
    Err(ProofCheckError<R, S>),

    /// The rule check reports that the dependencies are probably in the wrong
    /// order
    WrongOrder,
}

/// Helper for rules that accept multiple dependencies, where order of them
/// doesn't matter
///
/// ## Parameters
///   * `deps` - the dependencies to check
///   * `check_func` - function checking a rule and assuming a given ordering of
///                    the dependencies
///   * `fallthrough_handler` - function to obtain an error that occurs when all
///                             orderings have dependencies that are in the
///                             wrong form.
///
/// ## `check_func`
///
/// `check_func`'s argument is the list of dependencies that it expects to be in
/// a given order. For example, for a constructive dilemma, which takes the
/// form:
///
/// ```text
/// P -> Q, R -> S, P | R
/// ---------------------
/// Q | S
/// ```
///
/// The `check_func` might assume that the first argument takes the form
/// `P -> Q`, the second `R -> S`, and the third `P | R`. `any_order()` will
/// handle trying all orderings to find the correct one.
fn any_order<F, E, R, S>(deps: &[Expr], check_func: F, fallthrough_error: E) -> Result<(), ProofCheckError<R, S>>
where
    R: Ord,
    S: Ord,
    F: Fn(&[&Expr]) -> AnyOrderResult<R, S>,
    E: FnOnce() -> ProofCheckError<R, S>,
{
    // Iterator over the check results of all the permutations that weren't
    // `AnyOrderResult::WrongOrder`
    let mut results = deps.iter().permutations(deps.len()).map(|deps| check_func(&deps)).filter_map(|result: AnyOrderResult<R, S>| match result {
        AnyOrderResult::Ok => Some(Ok(())),
        AnyOrderResult::Err(err) => Some(Err(err)),
        AnyOrderResult::WrongOrder => None,
    });

    if results.any(|result| result.is_ok()) {
        // At least one succeeded, so the rule check succeeds
        Ok(())
    } else {
        // Set of rule check errors
        let errors = results.filter_map(|result| result.err()).collect::<BTreeSet<ProofCheckError<R, S>>>();
        match errors.len() {
            // All the orderings returned `AnyOrderResult::WrongOrder`
            0 => Err(fallthrough_error()),
            // One error
            1 => Err(errors.into_iter().next().unwrap()),
            // Multiple errors
            _ => Err(ProofCheckError::OneOf(errors)),
        }
    }
}

/// Helper for rules that accept two dependencies, where order of them doesn't
/// matter. This is a special case wrapper around `any_order()`.
fn either_order<R, S, F, E>(dep_1: &Expr, dep_2: &Expr, check_func: F, fallthrough_error: E) -> Result<(), ProofCheckError<R, S>>
where
    R: Ord,
    S: Ord,
    F: Fn(&Expr, &Expr) -> AnyOrderResult<R, S>,
    E: FnOnce() -> ProofCheckError<R, S>,
{
    let deps = &[dep_1.clone(), dep_2.clone()];
    // Convert `check_func` so it takes `&[&Expr]` instead of `&Expr, &Expr`
    let check_func = |deps: &[&Expr]| {
        let (dep_1, dep_2) = deps.iter().collect_tuple().unwrap();
        check_func(dep_1, dep_2)
    };
    any_order(deps, check_func, fallthrough_error)
}

/// Errors that can occur when checking a proof
#[derive(Debug, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum ProofCheckError<R, S> {
    /// A line reference lookup failed
    LineDoesNotExist(R),
    /// A subproof reference lookup failed
    SubproofDoesNotExist(S),
    /// The proof is malformed in a way that permits circular references
    ReferencesLaterLine(R, Coproduct<R, Coproduct<S, frunk_core::coproduct::CNil>>),
    /// The wrong number of line dependencies were provided for a rule
    IncorrectDepCount(Vec<R>, usize),
    /// The wrong number of subproof dependencies were provided for a rule
    IncorrectSubDepCount(Vec<S>, usize),
    /// A dependency `.0` was of the wrong form, and a placeholder `.1` was expected
    DepOfWrongForm(Expr, Expr),
    /// The conclusion of a rule was different from what was expected
    ConclusionOfWrongForm(Expr),
    /// `.0` should occur in `.1`, but it doesn't
    DoesNotOccur(Expr, Expr),
    /// A dependency was expected, but wasn't provided. `.1` indicates whether the expected value is approximate
    DepDoesNotExist(Expr, bool),
    /// Multiple errors apply
    OneOf(BTreeSet<ProofCheckError<R, S>>),
    /// Escape hatch for custom errors
    Other(String),
}

impl<R: std::fmt::Debug, S: std::fmt::Debug> std::fmt::Display for ProofCheckError<R, S> {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        use ProofCheckError::*;
        match self {
            LineDoesNotExist(r) => write!(f, "The referenced line {r:?} does not exist."),
            SubproofDoesNotExist(s) => write!(f, "The referenced subproof {s:?} does not exist."),
            ReferencesLaterLine(line, dep) => write!(f, "The dependency {dep:?} is after the step that uses it ({line:?})."),
            IncorrectDepCount(deps, n) => write!(f, "Too {} dependencies (expected: {}, provided: {}).", if deps.len() > *n { "many" } else { "few" }, n, deps.len()),
            IncorrectSubDepCount(sdeps, n) => write!(f, "Too {} subproof dependencies (expected: {}, provided: {}).", if sdeps.len() > *n { "many" } else { "few" }, n, sdeps.len()),
            DepOfWrongForm(x, y) => write!(f, "A dependency ({x}) is of the wrong form, expected {y}."),
            ConclusionOfWrongForm(kind) => write!(f, "The conclusion is of the wrong form, expected {kind}."),
            DoesNotOccur(x, y) => write!(f, "{x} does not occur in {y}."),
            DepDoesNotExist(x, approx) => write!(f, "{}{} is required as a dependency, but it does not exist.", if *approx { "Something of the shape " } else { "" }, x),
            OneOf(errs) => {
                assert!(errs.len() > 1);
                writeln!(f, "One of the following requirements was not met:")?;
                for err in errs {
                    writeln!(f, "{err}")?;
                }
                Ok(())
            }
            Other(msg) => write!(f, "{msg}"),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    use frunk_core::HList;

    #[test]
    fn test_either_order() {
        use crate::parser::parse_unwrap as p;
        use ProofCheckError::*;

        type P = crate::proofs::pooledproof::PooledProof<HList![Expr]>;
        type SRef = <P as Proof>::SubproofReference;

        let dep_1 = p("(A & B) -> C");
        let dep_2 = p("(A & B)");
        let conclusion = p("C");

        let result = either_order::<PjRef<P>, SRef, _, _>(
            &dep_1,
            &dep_2,
            |i, j| {
                if let Expr::Impl { ref left, ref right } = i {
                    //bad case, p -> q, a therefore --doesn't matter, nothing can be said
                    //with a
                    if **left != *j {
                        return AnyOrderResult::Err(DoesNotOccur(i.clone(), j.clone()));
                    }

                    //bad case, p -> q, p therefore a which does not follow
                    if **right != conclusion {
                        return AnyOrderResult::Err(DoesNotOccur(conclusion.clone(), *right.clone()));
                    }

                    //good case, p -> q, p therefore q
                    if **left == *j && **right == conclusion {
                        return AnyOrderResult::Ok;
                    }
                }
                AnyOrderResult::WrongOrder
            },
            || DepDoesNotExist(Expr::impl_place_holder(), true),
        );

        assert!(result.is_ok());
    }
}
