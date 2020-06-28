use super::*;

/// Defines literal data used for a rewrite rule.
/// Example usage: `define_rewrite_rule! { NAME_OF_RULE; ["pattern" -> "replacement"] }`
#[macro_export]
macro_rules! define_rewrite_rule {
    ($name:ident; [$($lhs:literal -> $rhs:literal),+]) => {
        #[cfg(generate_docstrings_from_macros)]
        define_rewrite_rule!{DEFINE_REWRITE_RULE_INTERNAL_CALL, $name, concat!("`", stringify!{$($lhs -> $rhs),+}, "`"), &[$(($lhs, $rhs)),+]}
        #[cfg(not(generate_docstrings_from_macros))]
        define_rewrite_rule!{DEFINE_REWRITE_RULE_INTERNAL_CALL, $name, "", &[$(($lhs, $rhs)),+]}
    };
    (DEFINE_REWRITE_RULE_INTERNAL_CALL, $name:ident, $docstring:expr, $patterns:expr) => {
        lazy_static! {
            #[doc=$docstring]
            pub static ref $name: RewriteRule = RewriteRule::from_patterns($patterns);
        }
    };
}

// Boolean Equivalences
define_rewrite_rule! { DOUBLE_NEGATION_RULES; ["~~phi" -> "phi"] }
define_rewrite_rule! { DISTRIBUTION_RULES; [
    "(phi & psi) | (phi & lambda)" -> "phi & (psi | lambda)",
    "(phi | psi) & (phi | lambda)" -> "phi | (psi & lambda)"
]}
define_rewrite_rule! { COMPLEMENT_RULES; [
    "phi & ~phi" -> "_|_",
    "phi | ~phi" -> "^|^"
]}
define_rewrite_rule! { IDENTITY_RULES; [
    "phi & ^|^" -> "phi",
    "phi | _|_" -> "phi"
]}
define_rewrite_rule! { ANNIHILATION_RULES; [
    "phi & _|_" -> "_|_",
    "phi | ^|^" -> "^|^"
]}
define_rewrite_rule! { INVERSE_RULES; [
    "~^|^" -> "_|_",
    "~_|_" -> "^|^"
]}
define_rewrite_rule! { ABSORPTION_RULES; [
    "phi & (phi | psi)" -> "phi",
    "phi | (phi & psi)" -> "phi"
]}
define_rewrite_rule! { REDUCTION_RULES; [
    "phi & (~phi | psi)" -> "phi & psi",
    "~phi & (phi | psi)" -> "~phi & psi",
    "phi | (~phi & psi)" -> "phi | psi",
    "~phi | (phi & psi)" -> "~phi | psi"
]}
define_rewrite_rule! { ADJACENCY_RULES; [
    "(phi | psi) & (phi | ~psi)" -> "phi",
    "(phi & psi) | (phi & ~psi)" -> "phi"
]}

// Conditional Equivalences
define_rewrite_rule! { CONDITIONAL_COMPLEMENT_RULES; [
    "phi -> phi" -> "^|^",
    "phi <-> phi" -> "^|^",
    "phi <-> ~phi" -> "_|_"
]}
define_rewrite_rule! { CONDITIONAL_IDENTITY_RULES; [
    "phi -> _|_" -> "~phi",
    "^|^ -> phi" -> "phi",
    "phi <-> _|_" -> "~phi",
    "phi <-> ^|^" -> "phi"
]}
define_rewrite_rule! { CONDITIONAL_ANNIHILATION_RULES; [
    "phi -> ^|^" -> "^|^",
    "_|_ -> phi" -> "^|^"
]}
define_rewrite_rule! { CONDITIONAL_IMPLICATION_RULES; [
    "phi -> psi" -> "~phi | psi",
    "~(phi -> psi)" -> "phi & ~psi"
]}
define_rewrite_rule! { CONDITIONAL_BIIMPLICATION_RULES; [ // equivalence
    "(phi -> psi) & (psi -> phi)" -> "phi <-> psi",
    "(phi & psi) | (~phi & ~psi)" -> "phi <-> psi"
]}
define_rewrite_rule! { CONDITIONAL_CONTRAPOSITION_RULES; [
    "~phi -> ~psi" -> "psi -> phi"
]}
define_rewrite_rule! { CONDITIONAL_CURRYING_RULES; [ // exportation
    "phi -> (psi -> lambda)" -> "(phi & psi) -> lambda"
]}
define_rewrite_rule! { CONDITIONAL_DISTRIBUTION_RULES; [
    "phi -> (psi & lambda)" -> "(phi -> psi) & (phi -> lambda)",
    "(phi | psi) -> lambda" -> "(phi -> lambda) & (psi -> lambda)",
    "phi -> (psi | lambda)" -> "(phi -> psi) | (phi -> lambda)",
    "(phi & psi) -> lambda" -> "(phi -> lambda) | (psi -> lambda)"
]}
define_rewrite_rule! { CONDITIONAL_REDUCTION_RULES; [
    "phi & (phi -> psi)" -> "phi & psi",
    "~psi & (phi -> psi)" -> "~psi & ~phi",
    "phi & (phi <-> psi)" -> "phi & psi",
    "~phi & (phi <-> psi)" -> "~phi & ~psi"
]}
define_rewrite_rule! { KNIGHTS_AND_KNAVES_RULES; [
    "phi <-> (phi & psi)" -> "phi -> psi",
    "phi <-> (phi | psi)" -> "psi -> phi"
]}
define_rewrite_rule! { CONDITIONAL_IDEMPOTENCE_RULES; [
    "phi -> ~phi" -> "~phi",
    "~phi -> phi" -> "phi"
]}
define_rewrite_rule! { BICONDITIONAL_NEGATION_RULES; [
    "~phi <-> psi" -> "~(phi <-> psi)",
    "phi <-> ~psi" -> "~(phi <-> psi)"
]}
define_rewrite_rule! { BICONDITIONAL_COMMUTATION_RULES; [
    "phi <-> psi" -> "psi <-> phi"
]}
define_rewrite_rule! { BICONDITIONAL_ASSOCIATION_RULES; [
    "phi <-> (psi <-> lambda)" -> "(phi <-> psi) <-> lambda"
]}
define_rewrite_rule! { BICONDITIONAL_SUBSTITUTION_RULES; [
    "(phi <-> psi) & S(phi)" -> "(phi <-> psi) & S(psi)"
]}

pub fn for_each_truthtable<F>(n: usize, mut f: F) where F: FnMut(&[bool]) {
    let mut table = vec![false; n];
    for x in 0..(2usize.pow(n as _)) {
        for i in 0..n {
            table[i] = (x & (1 << i)) != 0;
        }
        f(&table[..]);
    }
}

#[test]
fn bruteforce_equivalence_truthtables() {
    use std::collections::HashMap;
    let rules: Vec<&RewriteRule> = vec![
        &*DOUBLE_NEGATION_RULES, &*DISTRIBUTION_RULES, &*COMPLEMENT_RULES, &*IDENTITY_RULES, &*ANNIHILATION_RULES, &*INVERSE_RULES, &*ABSORPTION_RULES,
        &*REDUCTION_RULES, &*ADJACENCY_RULES, &*CONDITIONAL_ANNIHILATION_RULES, &*CONDITIONAL_IMPLICATION_RULES, &*CONDITIONAL_CONTRAPOSITION_RULES,
        &*CONDITIONAL_CURRYING_RULES, &*CONDITIONAL_COMPLEMENT_RULES, &*CONDITIONAL_IDENTITY_RULES, &*CONDITIONAL_BIIMPLICATION_RULES, &*CONDITIONAL_DISTRIBUTION_RULES,
        &*CONDITIONAL_REDUCTION_RULES, &*KNIGHTS_AND_KNAVES_RULES, &*CONDITIONAL_IDEMPOTENCE_RULES, &*BICONDITIONAL_NEGATION_RULES, &*BICONDITIONAL_COMMUTATION_RULES,
        &*BICONDITIONAL_ASSOCIATION_RULES, &*BICONDITIONAL_SUBSTITUTION_RULES,
    ];
    for rule in rules {
        for (lhs, rhs) in rule.reductions.iter() {
            println!("Testing {} -> {}", lhs, rhs);
            let mut fvs: Vec<String> = freevars(&lhs).union(&freevars(&rhs)).cloned().collect();
            fvs.sort();
            let mut arities = HashMap::new();
            lhs.infer_arities(&mut arities);
            rhs.infer_arities(&mut arities);
            println!("Inferred arities: {:?}", arities);
            let total_arity = arities.values().map(|v| 2usize.pow(*v as _)).sum();
            for_each_truthtable(total_arity, |table| {
                let mut env = HashMap::new();
                let mut i = 0;
                for fv in fvs.iter().cloned() {
                    let n = 2usize.pow(arities[&fv] as _);
                    env.insert(fv, table[i..i+n].to_vec());
                    i += n;
                }
                println!("{:?} {:?}", table, env);
                assert_eq!(lhs.eval(&env), rhs.eval(&env));
            });
            println!("-----");
        }
    }
}
