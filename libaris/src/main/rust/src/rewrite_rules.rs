use expression::*;
use std::collections::{HashMap, HashSet};

#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub struct RewriteRule {
    pub reductions: Vec<(Expr, Expr)>
}

impl RewriteRule {
    /// Construct a rewrite ruleset from a list of reduction patterns of the form
    /// [("pattern", "replacement"), ...]
    /// Will parse strings into `Expr`s and permute all commutative binops
    pub fn from_patterns(patterns: &[(&str, &str)]) -> Self {
        use parser::parse_unwrap as p;
        let reductions = permute_patterns(patterns.into_iter().map(|(premise, conclusion)| {
            (p(premise), p(conclusion))
        }).collect::<Vec<_>>());

        RewriteRule {
            reductions
        }
    }

    /// Reduce an expression with the rewrite rule's reductions
    pub fn reduce(&self, e: Expr) -> Expr {
        reduce_pattern(e, &self.reductions)
    }
}


/// Generate all combinations of a list of items
/// E.g. [1, 2, 3] => [[1, 2, 3], [1, 3, 2], [2, 1, 3], [2, 3, 1], [3, 1, 2], [3, 2, 1]]
/// Guaranteed ordered by position in the list
/// Generic over Copy+Sized T (because doing this with clone would do a heinous amount of cloning)
fn combinations<T>(list: Vec<T>) -> Vec<Vec<T>>
    where T: Copy+Sized {
    // Base case
    if list.len() <= 1 {
        return vec![list];
    }

    let mut results = vec![];
    for cur in 0..list.len() {
        // List of all items that are not the current one
        let mut sublist: Vec<T> = vec![];
        sublist.extend_from_slice(&list[..cur]);
        sublist.extend_from_slice(&list[cur+1..]);

        let sub_combinations = combinations(sublist);
        for sub_combination in sub_combinations {
            let mut combination = vec![list[cur]];
            combination.extend(sub_combination);
            results.push(combination);
        }
    }

    results
}

#[test]
fn test_combinate() {
    assert_eq!(combinations(vec![1, 2, 3]), vec![
        vec![1, 2, 3],
        vec![1, 3, 2],
        vec![2, 1, 3],
        vec![2, 3, 1],
        vec![3, 1, 2],
        vec![3, 2, 1],
    ]);

    let a = 1;
    let b = 2;
    let c = 3;
    assert_eq!(combinations(vec![&a, &b, &c]), vec![
        vec![&a, &b, &c],
        vec![&a, &c, &b],
        vec![&b, &a, &c],
        vec![&b, &c, &a],
        vec![&c, &a, &b],
        vec![&c, &b, &a],
    ]);
}

/// Cartesian product of two vectors:
/// E.g. [1, 2] x [3, 4] ==> [[1, 3], [1, 4], [2, 3], [2, 4]]
/// Guaranteed ordered by position in the list
fn cartesian_product_pair<T1, T2>(list1: Vec<T1>, list2: Vec<T2>) -> Vec<(T1, T2)>
    where T1: Clone+Sized, T2: Clone+Sized {
    list1.into_iter().flat_map(|left| {
        list2.iter().map(move |right| (left.clone(), right.clone()))
    }).collect::<Vec<_>>()
}

/// Cartesian product of many vectors:
/// E.g. [1, 2] x [3, 4] x [5, 6] ==> [[1, 3, 5], [1, 3, 6], [1, 4, 5], [1, 4, 6], [2, 3, 5], [2, 3, 6], [2, 4, 5], [2, 4, 6]]
/// Guaranteed ordered by position in the list
fn cartesian_product<T>(mut lists: Vec<Vec<T>>) -> Vec<Vec<T>>
    where T: Clone+Sized {
    // Base case
    if lists.len() <= 1 {
        return lists;
    }
    // Fallback on the pairwise when we can
    if lists.len() == 2 {
        let first = lists.remove(0);
        let second = lists.remove(0);
        return cartesian_product_pair(first, second).into_iter().map(|(a, b)| vec![a, b]).collect::<Vec<_>>();
    }

    let firsts = lists.remove(0);
    let prod_rests = cartesian_product(lists);

    firsts.into_iter().flat_map(|first| {
        prod_rests.iter().map(move |prod| {
            let mut result = vec![first.clone()];
            result.extend(prod.clone());
            result
        })
    }).collect::<Vec<_>>()
}

#[test]
fn test_cartesian_product() {
    assert_eq!(cartesian_product_pair(vec![1, 2], vec![3, 4]), vec![
        (1, 3),
        (1, 4),
        (2, 3),
        (2, 4)
    ]);
    assert_eq!(cartesian_product(vec![vec![1, 2], vec![3, 4], vec![5, 6]]), vec![
        vec![1, 3, 5],
        vec![1, 3, 6],
        vec![1, 4, 5],
        vec![1, 4, 6],
        vec![2, 3, 5],
        vec![2, 3, 6],
        vec![2, 4, 5],
        vec![2, 4, 6],
    ]);
}

/// Permute all binary and associative operations in an expression, resulting in a list of
/// expressions of all permutations
/// E.g. ((A & B) & C) ==> [((A & B) & C), ((B & A) & C), (C & (A & B)), (C & (B & A))]
/// This function is extremely slow! Don't use it for large expressions or too often.
fn permute_ops(e: Expr) -> Vec<Expr> {
    use Expr::*;
    use expression_builders::*;
    match e {
        // Trivial cases
        e @ Contradiction => vec![e],
        e @ Tautology => vec![e],
        e @ Var { .. } => vec![e],
        Apply { func, args } => {
            let mut to_permute: Vec<Vec<Expr>> = vec![permute_ops(*func)];
            to_permute.extend(args.into_iter().map(|e: Expr| permute_ops(e)));
            let permuted = cartesian_product(to_permute);
            permuted.into_iter().map(|mut args| { let func = Box::new(args.remove(0)); Apply { func, args } }).collect()
        },
        Unop { symbol, operand } => {
            // Just permute the operands and return them
            let results = permute_ops(*operand);
            results.into_iter().map(|e| {
                Unop {
                    symbol: symbol.clone(),
                    operand: Box::new(e)
                }
            }).collect::<Vec<_>>()
        }
        Binop { symbol, left, right } => {
            let permute_left = permute_ops( *left);
            let permute_right = permute_ops( *right);

            let mut results = vec![];
            for left in &permute_left {
                for right in &permute_right {
                    results.push(binop(symbol, left.clone(), right.clone()));
                    if symbol.is_commutative() {
                        results.push(binop(symbol, right.clone(), left.clone()));
                    }
                }
            }
            results
        }
        AssocBinop { symbol, exprs } => {
            // For every combination of the args, add the cartesian product of the permutations of their parameters

            // All orderings of arguments
            let arg_combinations = if symbol.is_commutative()  {
                combinations(exprs.iter().collect::<Vec<_>>())
            } else {
                vec![exprs.iter().collect::<Vec<_>>()]
            };
            arg_combinations.into_iter().flat_map(|args| {
                // Permuting every expression in the current list of args
                let permutations = args.into_iter().map(|arg| permute_ops(arg.clone())).collect::<Vec<_>>();
                // Convert the Vec<Vec<Expr>> to a Vec<Vec<&Expr>>
                let ref_perms = permutations.iter().map(|l| l.iter().collect::<Vec<_>>()).collect::<Vec<_>>();
                // Then get a cartesian product of all permutations (this is the slow part)
                // Gives you a list of new argument lists
                let product = cartesian_product(ref_perms);
                // Then just turn everything from that list into an assoc binop
                let new_exprs = product.into_iter().map(|args| {
                    AssocBinop { symbol: symbol.clone(), exprs: args.into_iter().map(|arg| arg.clone()).collect::<Vec<_>>() }
                }).collect::<Vec<_>>(); // This collect is necessary to maintain the borrows from permutations
                new_exprs
            }).collect::<Vec<_>>()
        }
        Quantifier { symbol, name, body } => {
            let results = permute_ops(*body);
            results.into_iter().map(|e| {
                Quantifier {
                    symbol: symbol.clone(),
                    name: name.clone(),
                    body: Box::new(e)
                }
            }).collect::<Vec<_>>()
        }
    }
}

#[test]
fn test_permute_ops() {
    use parser::parse_unwrap as p;

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
    println!("{} {} {} {} {} {} {} {}", p2[0], p2[1], p2[2], p2[3], p2[4], p2[5], p2[6], p2[7]);
}

/// Permute the search expression of every pattern, all mapping to the same replacement
/// E.g. [(A & B) -> C, (A | B) -> C] ==> [(A & B) -> C, (B & A) -> C, (A | B) -> C, (B | A) -> C]
fn permute_patterns(patterns: Vec<(Expr, Expr)>) -> Vec<(Expr, Expr)> {
    // Permute_ops of all input patterns
    patterns.into_iter().flat_map(|(find, replace)| {
        permute_ops(find).into_iter().map(move |find| (find, replace.clone()))
    }).collect::<Vec<_>>()
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
///
/// # Example
/// ```
/// // DeMorgan's for and/or that have only two parameters
/// use libaris::expression::{ASymbol, expression_builders::*};
/// use libaris::rewrite_rules::reduce_pattern;
///
/// // ~(phi & psi) ==> ~phi | ~psi
/// let pattern1 = not(assocbinop(ASymbol::And, &[var("phi"), var("psi")]));
/// let replace1 = assocbinop(ASymbol::Or, &[not(var("phi")), not(var("psi"))]);
///
/// // ~(phi | psi) ==> ~phi & ~psi
/// let pattern2 = not(assocbinop(ASymbol::Or, &[var("phi"), var("psi")]));
/// let replace2 = assocbinop(ASymbol::And, &[not(var("phi")), not(var("psi"))]);
///
/// let patterns = vec![(pattern1, replace1), (pattern2, replace2)];
/// reduce_pattern(var("some_expr"), &patterns);
/// ```
pub fn reduce_pattern(e: Expr, patterns: &Vec<(Expr, Expr)>) -> Expr {
    use expression_builders::*;

    let e_free = freevars(&e);

    // Find all free variables in the patterns and map them to generated names free for e
    let patterns = patterns.iter().map(|(pattern, replace)| {
        let mut pattern = pattern.clone();
        let mut replace = replace.clone();
        let free_pattern = freevars(&pattern);

        // Make sure our replacement doesn't have any new vars
        let free_replace = freevars(&replace);
        assert!(free_replace.is_subset(&free_pattern));

        // Replace all the free vars in the pattern with a known fresh variable in e
        let mut pattern_vars = HashSet::new();
        for free_var in free_pattern {
            let new_sym = gensym(&*free_var, &e_free);
            pattern = subst(&pattern, &*free_var, var(&*new_sym));
            replace = subst(&replace, &*free_var, var(&*new_sym));
            pattern_vars.insert(new_sym);
        }

        (pattern, replace, pattern_vars)
    }).collect::<Vec<_>>();

    e.transform(&|expr| {
        // Try all our patterns at every level of the tree
        for (pattern, replace, pattern_vars) in &patterns {
            // Unify3D
            let ret = unify(vec![Constraint::Equal(pattern.clone(), expr.clone())].into_iter().collect());
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
                    let subst_replace = subs.into_iter().fold(replace.clone(), |z, (x, y)| subst(&z, &x, y));
                    return (subst_replace, true);
                }
            }
        }
        (expr, false)
    })
}
