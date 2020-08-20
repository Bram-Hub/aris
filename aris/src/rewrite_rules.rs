//! Fixpoint engine for applying transformations to a formula in a loop until
//! they stop applying

use crate::expr::free_vars;
use crate::expr::gensym;
use crate::expr::subst;
use crate::expr::Equal;
use crate::expr::Expr;

use std::collections::HashMap;
use std::collections::HashSet;

use itertools::Itertools;

#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub struct RewriteRule {
    pub reductions: Vec<(Expr, Expr)>,
}

impl RewriteRule {
    /// Construct a rewrite ruleset from a list of reduction patterns of the form
    /// [("pattern", "replacement"), ...]
    /// Will parse strings into `Expr`s and permute all commutative binops
    pub fn from_patterns(patterns: &[(&str, &str)]) -> Self {
        use crate::parser::parse_unwrap as p;
        let reductions = permute_patterns(
            patterns
                .iter()
                .map(|(premise, conclusion)| (p(premise), p(conclusion)))
                .collect::<Vec<_>>(),
        );

        RewriteRule { reductions }
    }

    /// Reduce an expression with the rewrite rule's reductions
    pub fn reduce(&self, e: Expr) -> Expr {
        reduce_pattern(e, &self.reductions)
    }

    /// Reduce an expression with the rewrite rule's reductions, yielding a set
    /// of possible reductions
    pub fn reduce_set(&self, e: Expr) -> HashSet<Expr> {
        reduce_pattern_set(e, &self.reductions)
    }
}

/// Permute all binary and associative operations in an expression, resulting in a list of
/// expressions of all permutations
/// E.g. ((A & B) & C) ==> [((A & B) & C), ((B & A) & C), (C & (A & B)), (C & (B & A))]
/// This function is extremely slow! Don't use it for large expressions or too often.
fn permute_ops(e: Expr) -> Vec<Expr> {
    match e {
        // Trivial cases
        e @ Expr::Contra => vec![e],
        e @ Expr::Taut => vec![e],
        e @ Expr::Var { .. } => vec![e],
        Expr::Apply { func, args } => std::iter::once(permute_ops(*func))
            .chain(args.into_iter().map(permute_ops))
            .multi_cartesian_product()
            .map(|mut args| {
                let func = Box::new(args.remove(0));
                Expr::Apply { func, args }
            })
            .collect(),
        Expr::Not { operand } => {
            // Just permute the operands and return them
            let results = permute_ops(*operand);
            results
                .into_iter()
                .map(|e| Expr::Not {
                    operand: Box::new(e),
                })
                .collect::<Vec<_>>()
        }
        Expr::Impl { left, right } => {
            let permute_left = permute_ops(*left);
            let permute_right = permute_ops(*right);

            let mut results = vec![];
            for left in &permute_left {
                for right in &permute_right {
                    results.push(Expr::implies(left.clone(), right.clone()));
                }
            }
            results
        }
        Expr::Assoc { op, exprs } => {
            // For every combination of the args, add the cartesian product of the permutations of their parameters

            let len = exprs.len();

            // All orderings of arguments
            exprs
                .into_iter()
                .permutations(len)
                .flat_map(|args| {
                    // Permuting every expression in the current list of args
                    let permutations = args
                        .into_iter()
                        .map(|arg| permute_ops(arg.clone()))
                        .collect::<Vec<_>>();
                    // Convert the Vec<Vec<Expr>> to a Vec<Vec<&Expr>>
                    let ref_perms = permutations
                        .iter()
                        .map(|l| l.iter().collect::<Vec<_>>())
                        .collect::<Vec<_>>();
                    // Then get a cartesian product of all permutations (this is the slow part)
                    // Gives you a list of new argument lists
                    let product = ref_perms.into_iter().multi_cartesian_product();
                    // Then just turn everything from that list into an assoc binop
                    // The `collect()` is necessary to maintain the borrows from permutations
                    product
                        .into_iter()
                        .map(|args| Expr::Assoc {
                            op,
                            exprs: args.into_iter().cloned().collect::<Vec<_>>(),
                        })
                        .collect::<Vec<_>>()
                })
                .collect::<Vec<_>>()
        }
        Expr::Quant { kind, name, body } => {
            let results = permute_ops(*body);
            results
                .into_iter()
                .map(|e| Expr::Quant {
                    kind,
                    name: name.clone(),
                    body: Box::new(e),
                })
                .collect::<Vec<_>>()
        }
    }
}

/// Permute the search expression of every pattern, all mapping to the same replacement
/// E.g. [(A & B) -> C, (A | B) -> C] ==> [(A & B) -> C, (B & A) -> C, (A | B) -> C, (B | A) -> C]
fn permute_patterns(patterns: Vec<(Expr, Expr)>) -> Vec<(Expr, Expr)> {
    // Permute_ops of all input patterns
    patterns
        .into_iter()
        .flat_map(|(find, replace)| {
            permute_ops(find)
                .into_iter()
                .map(move |find| (find, replace.clone()))
        })
        .collect::<Vec<_>>()
}

/// Reduce an expression by a pattern with a set of variables
///
/// Basically this lets you construct pattern-based expression reductions by defining the reduction
/// as a pattern instead of manually matching expressions.
///
/// Patterns are defined as `(match, replace)` where any expression in the tree of `e` that unifies
/// with `match` on all pattern variables defined in `pattern_vars` will be replaced by `replace` with
/// the substitutions from the unification.
///
/// Limitations: Cannot do variadic versions of assoc binops, you need a constant number of args
fn reduce_pattern(e: Expr, patterns: &[(Expr, Expr)]) -> Expr {
    let patterns = freevarsify_pattern(&e, patterns);
    e.transform(&|expr| reduce_transform_func(expr, &patterns))
}

/// Like `reduce_pattern()`, but creates a set of possible reductions. This set
/// will contain all levels of reduction (up to full normalization), and on all
/// sub-nodes of the expression.
fn reduce_pattern_set(e: Expr, patterns: &[(Expr, Expr)]) -> HashSet<Expr> {
    let patterns = freevarsify_pattern(&e, patterns);
    e.transform_set(&|expr| reduce_transform_func(expr, &patterns))
}

/// Helper function for `reduce_pattern()` and `reduce_pattern_set()`; try to
/// reduce `expr` using `patterns`. The returned `bool` in the tuple indicates
/// whether the transformation can be done again.
///
/// Parameters:
///   * `expr` - expression to reduce
///   * `patterns` - patterns returned by `freevarsify_pattern()`
fn reduce_transform_func(expr: Expr, patterns: &[(Expr, Expr, HashSet<String>)]) -> (Expr, bool) {
    // Try all our patterns at every level of the tree
    for (pattern, replace, pattern_vars) in patterns {
        // Unify3D
        let ret = crate::expr::unify(
            vec![Equal {
                left: pattern.clone(),
                right: expr.clone(),
            }]
            .into_iter()
            .collect(),
        );
        if let Some(ret) = ret {
            // Collect all unification results and make sure we actually match exactly
            let mut subs = HashMap::new();
            let mut any_bad = false;
            for subst in ret.0 {
                // We only want to unify our pattern variables. This prevents us from going backwards
                // and unifying a pattern variable in expr with some expression of our pattern variable
                if pattern_vars.contains(&subst.0) {
                    // Sanity check: Only one unification per variable
                    assert!(subs.insert(subst.0, subst.1).is_none());
                } else {
                    any_bad = true;
                }
            }

            // Make sure we have a substitution for every variable in the pattern set (and only for them)
            if !any_bad && subs.len() == pattern_vars.len() {
                let subst_replace = subs
                    .into_iter()
                    .fold(replace.clone(), |z, (x, y)| crate::expr::subst(&z, &x, y));
                return (subst_replace, true);
            }
        }
    }
    (expr, false)
}

/// Helper function for `reduce_pattern()` and `reduce_pattern_set()`; given an
/// expression `e` and a slice of (`pattern`, `replace`) pairs, get a vector of
/// (`new_pattern`, `new_replace`, `pattern_vars`), where:
///
///   * `new_pattern` is the old `pattern` with all free variables in `e` renamed to fresh variables
///   * `new_replace` is the old `replace` with the renames in `new_pattern`
///   * `pattern_vars` is the set of free variables in `new_pattern`
fn freevarsify_pattern(e: &Expr, patterns: &[(Expr, Expr)]) -> Vec<(Expr, Expr, HashSet<String>)> {
    let e_free = free_vars(e);

    // Find all free variables in the patterns and map them to generated names free for e
    patterns
        .iter()
        .map(|(pattern, replace)| {
            let mut pattern = pattern.clone();
            let mut replace = replace.clone();
            let free_pattern = free_vars(&pattern);

            // Make sure our replacement doesn't have any new vars
            let free_replace = free_vars(&replace);
            assert!(free_replace.is_subset(&free_pattern));

            // Replace all the free vars in the pattern with a known fresh variable in e
            let mut pattern_vars = HashSet::new();
            for free_var in free_pattern {
                let new_sym = gensym(&*free_var, &e_free);
                pattern = subst(&pattern, &*free_var, Expr::var(&*new_sym));
                replace = subst(&replace, &*free_var, Expr::var(&*new_sym));
                pattern_vars.insert(new_sym);
            }

            (pattern, replace, pattern_vars)
        })
        .collect::<Vec<_>>()
}

#[cfg(test)]
mod tests {
    use super::*;

    use crate::expr::Op;

    #[test]
    fn test_permute_ops() {
        use crate::parser::parse_unwrap as p;

        // A & B
        // B & A
        let p1 = permute_ops(p("A & B"));
        // (A & B) & (C & D)
        // (B & A) & (C & D)
        // (A & B) & (D & C)
        // (B & A) & (D & C)
        // (C & D) & (A & B)
        // (C & D) & (B & A)
        // (D & C) & (A & B)
        // (D & C) & (B & A)
        let p2 = permute_ops(p("(A & B) & (C & D)"));
        assert_eq!(p1.len(), 2);
        println!("{} {}", p1[0], p1[1]);
        assert_eq!(p2.len(), 8);
        println!(
            "{} {} {} {} {} {} {} {}",
            p2[0], p2[1], p2[2], p2[3], p2[4], p2[5], p2[6], p2[7]
        );
    }

    #[test]
    fn test_reduce_pattern() {
        // DeMorgan's for and/or that have only two parameters

        // ~(phi & psi) ==> ~phi | ~psi
        let pattern1 = Expr::not(Expr::assoc(Op::And, &[Expr::var("phi"), Expr::var("psi")]));
        let replace1 = Expr::assoc(
            Op::Or,
            &[Expr::not(Expr::var("phi")), Expr::not(Expr::var("psi"))],
        );

        // ~(phi | psi) ==> ~phi & ~psi
        let pattern2 = Expr::not(Expr::assoc(Op::Or, &[Expr::var("phi"), Expr::var("psi")]));
        let replace2 = Expr::assoc(
            Op::And,
            &[Expr::not(Expr::var("phi")), Expr::not(Expr::var("psi"))],
        );

        let patterns = vec![(pattern1, replace1), (pattern2, replace2)];
        reduce_pattern(Expr::var("some_expr"), &patterns);
    }
}
