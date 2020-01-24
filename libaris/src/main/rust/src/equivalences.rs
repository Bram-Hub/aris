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
