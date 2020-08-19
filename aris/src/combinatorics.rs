use itertools::Itertools;

/// Generate all permutations of a list of items.
///
/// E.g. `[1, 2, 3] => [[1, 2, 3], [1, 3, 2], [2, 1, 3], [2, 3, 1], [3, 1, 2], [3, 2, 1]]`
///
/// Generic over `Copy` (because doing this with clone would do a heinous amount of cloning)
pub fn permutations<T: Copy>(list: Vec<T>) -> Vec<Vec<T>> {
    let len = list.len();
    list.into_iter().permutations(len).collect()
}

#[test]
fn test_permutations() {
    assert_eq!(
        permutations(vec![1, 2, 3]),
        vec![
            vec![1, 2, 3],
            vec![1, 3, 2],
            vec![2, 1, 3],
            vec![2, 3, 1],
            vec![3, 1, 2],
            vec![3, 2, 1],
        ]
    );

    let a = 1;
    let b = 2;
    let c = 3;
    assert_eq!(
        permutations(vec![&a, &b, &c]),
        vec![
            vec![&a, &b, &c],
            vec![&a, &c, &b],
            vec![&b, &a, &c],
            vec![&b, &c, &a],
            vec![&c, &a, &b],
            vec![&c, &b, &a],
        ]
    );
}

/// Cartesian product of two vectors:
///
/// E.g. [1, 2] x [3, 4] ==> [[1, 3], [1, 4], [2, 3], [2, 4]]
///
/// Guaranteed ordered by position in the list
pub fn cartesian_product<T1: Clone, T2: Clone>(list_1: Vec<T1>, list_2: Vec<T2>) -> Vec<(T1, T2)> {
    list_1.into_iter().cartesian_product(list_2).collect()
}

/// Cartesian product of many vectors:
///
/// E.g. [1, 2] x [3, 4] x [5, 6] ==> [[1, 3, 5], [1, 3, 6], [1, 4, 5], [1, 4, 6], [2, 3, 5], [2, 3, 6], [2, 4, 5], [2, 4, 6]]
///
/// Guaranteed ordered by position in the list
pub fn multi_cartesian_product<T: Clone>(lists: Vec<Vec<T>>) -> Vec<Vec<T>> {
    lists.into_iter().multi_cartesian_product().collect()
}

#[test]
fn test_cartesian_product() {
    assert_eq!(
        cartesian_product(vec![1, 2], vec![3, 4]),
        vec![(1, 3), (1, 4), (2, 3), (2, 4)]
    );
    assert_eq!(
        multi_cartesian_product(vec![vec![1, 2], vec![3, 4], vec![5, 6]]),
        vec![
            vec![1, 3, 5],
            vec![1, 3, 6],
            vec![1, 4, 5],
            vec![1, 4, 6],
            vec![2, 3, 5],
            vec![2, 3, 6],
            vec![2, 4, 5],
            vec![2, 4, 6],
        ]
    );
}
