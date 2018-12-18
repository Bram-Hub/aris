use super::*;
use std::collections::HashMap;

/// a ZipperVec represents a list-with-edit-position [a,b,c, EDIT_CURSOR, d, e, f] as (vec![a, b, c], vec![f, e, d])
/// since Vec's have O(1) insert/remove at the end, ZipperVec's have O(1) insert/removal around the edit cursor, while being way more cache/memory efficient than a doubly-linked list
/// the cursor can be moved from position i to position j in O(|i-j|) time by shuffling elements between the prefix and the suffix
// TODO: should ZipperVec have a seperate module?
pub struct ZipperVec<T> {
    prefix: Vec<T>,
    suffix_r: Vec<T>,
}

pub enum LineTag { Justification(usize), Subproof(usize) }

pub struct PooledProof {
    premise_map: HashMap<usize, Expr>,
    just_map: HashMap<usize, Justification<usize>>,
    subproof_map: HashMap<usize, PooledProof>,

    premise_list: ZipperVec<usize>,
    line_list: ZipperVec<LineTag>,
}
    
