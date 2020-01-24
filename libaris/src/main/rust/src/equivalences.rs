use super::*;

lazy_static! {
    // Boolean Equivalences

    pub static ref DOUBLE_NEGATION_RULES: RewriteRule = RewriteRule::from_patterns(&[
        ("~~phi", "phi")
    ]);
    pub static ref DISTRIBUTION_RULES: RewriteRule = RewriteRule::from_patterns(&[
        ("(phi & psi) | (phi & lambda)", "phi & (psi | lambda)"),
        ("(phi | psi) & (phi | lambda)", "phi | (psi & lambda)")
    ]);
    pub static ref COMPLEMENT_RULES: RewriteRule = RewriteRule::from_patterns(&[
        ("phi & ~phi", "_|_"),
        ("phi | ~phi", "^|^"),
    ]);
    pub static ref IDENTITY_RULES: RewriteRule = RewriteRule::from_patterns(&[
        ("phi & ^|^", "phi"),
        ("phi | _|_", "phi"),
    ]);
    pub static ref ANNIHILATION_RULES: RewriteRule = RewriteRule::from_patterns(&[
        ("phi & _|_", "_|_"),
        ("phi | ^|^", "^|^"),
    ]);
    pub static ref INVERSE_RULES: RewriteRule = RewriteRule::from_patterns(&[
        ("~^|^", "_|_"),
        ("~_|_", "^|^")
    ]);
    pub static ref ABSORPTION_RULES: RewriteRule = RewriteRule::from_patterns(&[
        ("phi & (phi | psi)", "phi"),
        ("phi | (phi & psi)", "phi")
    ]);
    pub static ref REDUCTION_RULES: RewriteRule = RewriteRule::from_patterns(&[
        ("phi & (~phi | psi)", "phi & psi"),
        ("phi | (~phi & psi)", "phi | psi")
    ]);
    pub static ref ADJACENCY_RULES: RewriteRule = RewriteRule::from_patterns(&[
        ("(phi | psi) & (phi | ~psi)", "phi"),
        ("(phi & psi) | (phi & ~psi)", "phi")
    ]);

    // Conditional Equivalences

    pub static ref CONDITIONAL_COMPLEMENT_RULES: RewriteRule = RewriteRule::from_patterns(&[
        ("phi -> phi", "^|^"),
        ("phi <-> phi", "^|^"),
        ("phi <-> ~phi", "_|_"),
    ]);
    pub static ref CONDITIONAL_IDENTITY_RULES: RewriteRule = RewriteRule::from_patterns(&[
        ("phi -> _|_", "~phi"),
        ("^|^ -> phi", "phi"),
        ("phi <-> _|_", "~phi"),
        ("phi <-> ^|^", "phi"),
    ]);
    pub static ref CONDITIONAL_ANNIHILATION_RULES: RewriteRule = RewriteRule::from_patterns(&[
        ("phi -> ^|^", "^|^"),
        ("_|_ -> phi", "^|^"),
    ]);
    pub static ref CONDITIONAL_IMPLICATION_RULES: RewriteRule = RewriteRule::from_patterns(&[
        ("phi -> psi", "~phi | psi"),
        ("~(phi -> psi)", "phi & ~psi"),
    ]);
    pub static ref CONDITIONAL_BIIMPLICATION_RULES: RewriteRule = RewriteRule::from_patterns(&[
        ("phi <-> psi", "(phi -> psi) & (psi -> phi)"),
        ("phi <-> psi", "(phi & psi) | (~phi & ~psi)"),
    ]);
    pub static ref CONDITIONAL_CONTRAPOSITION_RULES: RewriteRule = RewriteRule::from_patterns(&[
        ("~phi -> ~psi", "psi -> phi")
    ]);
    pub static ref CONDITIONAL_CURRYING_RULES: RewriteRule = RewriteRule::from_patterns(&[
        ("phi -> (psi -> lambda)", "(phi & psi) -> lambda")
    ]);
}

fn for_each_truthtable<F>(n: usize, mut f: F) where F: FnMut(&[bool]) {
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
        &*DOUBLE_NEGATION_RULES, &*DISTRIBUTION_RULES, &*COMPLEMENT_RULES, &*IDENTITY_RULES, &*ANNIHILATION_RULES, &*INVERSE_RULES, &*ABSORPTION_RULES, &*REDUCTION_RULES, &*ADJACENCY_RULES,
        &*CONDITIONAL_ANNIHILATION_RULES, &*CONDITIONAL_IMPLICATION_RULES, &*CONDITIONAL_CONTRAPOSITION_RULES, &*CONDITIONAL_CURRYING_RULES,
        // TODO: need evaluator for biconditional, is this ==? xor?
        //&*CONDITIONAL_COMPLEMENT_RULES, &*CONDITIONAL_IDENTITY_RULES, &*CONDITIONAL_BIIMPLICATION_RULES,
    ];
    for rule in rules {
        for (lhs, rhs) in rule.reductions.iter() {
            println!("Testing {} -> {}", lhs, rhs);
            let mut fv: Vec<String> = freevars(&lhs).union(&freevars(&rhs)).cloned().collect();
            fv.sort();
            for_each_truthtable(fv.len(), |table| {
                let env = fv.iter().cloned().zip(table.iter().cloned()).collect::<HashMap<String, bool>>();
                println!("{:?} {:?}", table, env);
                assert_eq!(lhs.eval(&env), rhs.eval(&env));
            });
            println!("-----");
        }
    }
}
