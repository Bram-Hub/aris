/// Generate all combinations of a list of items
/// E.g. [1, 2, 3] => [[1, 2, 3], [1, 3, 2], [2, 1, 3], [2, 3, 1], [3, 1, 2], [3, 2, 1]]
/// Guaranteed ordered by position in the list
/// Generic over Copy+Sized T (because doing this with clone would do a heinous amount of cloning)
pub fn combinations<T>(list: Vec<T>) -> Vec<Vec<T>>
where
    T: Copy + Sized,
{
    // Base case
    if list.len() <= 1 {
        return vec![list];
    }

    let mut results = vec![];
    for cur in 0..list.len() {
        // List of all items that are not the current one
        let mut sublist: Vec<T> = vec![];
        sublist.extend_from_slice(&list[..cur]);
        sublist.extend_from_slice(&list[cur + 1..]);

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
    assert_eq!(
        combinations(vec![1, 2, 3]),
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
        combinations(vec![&a, &b, &c]),
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
/// E.g. [1, 2] x [3, 4] ==> [[1, 3], [1, 4], [2, 3], [2, 4]]
/// Guaranteed ordered by position in the list
pub fn cartesian_product_pair<T1, T2>(list1: Vec<T1>, list2: Vec<T2>) -> Vec<(T1, T2)>
where
    T1: Clone + Sized,
    T2: Clone + Sized,
{
    list1
        .into_iter()
        .flat_map(|left| list2.iter().map(move |right| (left.clone(), right.clone())))
        .collect::<Vec<_>>()
}

/// Cartesian product of many vectors:
/// E.g. [1, 2] x [3, 4] x [5, 6] ==> [[1, 3, 5], [1, 3, 6], [1, 4, 5], [1, 4, 6], [2, 3, 5], [2, 3, 6], [2, 4, 5], [2, 4, 6]]
/// Guaranteed ordered by position in the list
pub fn cartesian_product<T>(mut lists: Vec<Vec<T>>) -> Vec<Vec<T>>
where
    T: Clone + Sized,
{
    // Base case
    if lists.len() <= 1 {
        return lists;
    }
    // Fallback on the pairwise when we can
    if lists.len() == 2 {
        let first = lists.remove(0);
        let second = lists.remove(0);
        return cartesian_product_pair(first, second)
            .into_iter()
            .map(|(a, b)| vec![a, b])
            .collect::<Vec<_>>();
    }

    let firsts = lists.remove(0);
    let prod_rests = cartesian_product(lists);

    firsts
        .into_iter()
        .flat_map(|first| {
            prod_rests.iter().map(move |prod| {
                let mut result = vec![first.clone()];
                result.extend(prod.clone());
                result
            })
        })
        .collect::<Vec<_>>()
}

#[test]
fn test_cartesian_product() {
    assert_eq!(
        cartesian_product_pair(vec![1, 2], vec![3, 4]),
        vec![(1, 3), (1, 4), (2, 3), (2, 4)]
    );
    assert_eq!(
        cartesian_product(vec![vec![1, 2], vec![3, 4], vec![5, 6]]),
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
