use super::*;
use std::collections::BTreeMap;

/// a ZipperVec represents a list-with-edit-position [a,b,c, EDIT_CURSOR, d, e, f] as (vec![a, b, c], vec![f, e, d])
/// since Vec's have O(1) insert/remove at the end, ZipperVec's have O(1) insert/removal around the edit cursor, while being way more cache/memory efficient than a doubly-linked list
/// the cursor can be moved from position i to position j in O(|i-j|) time by shuffling elements between the prefix and the suffix
// TODO: should ZipperVec have a seperate module?
#[derive(Clone, Debug)]
pub struct ZipperVec<T> {
    prefix: Vec<T>,
    suffix_r: Vec<T>,
}
impl<T> ZipperVec<T> {
    pub fn new() -> Self { ZipperVec { prefix: Vec::new(), suffix_r: Vec::new() } }
    pub fn cursor_pos(&self) -> usize { self.prefix.len() }
    pub fn len(&self) -> usize { self.prefix.len() + self.suffix_r.len() }
    pub fn move_cursor(&mut self, mut to: usize) {
        while to > self.cursor_pos() { if let Some(x) = self.suffix_r.pop() { self.prefix.push(x); }; to -= 1 }
        while to < self.cursor_pos() { if let Some(x) = self.prefix.pop() { self.suffix_r.push(x); }; to += 1 }
    }
    pub fn push(&mut self, x: T) {
        let len = self.len();
        self.move_cursor(len);
        self.prefix.push(x);
    }
}

#[derive(Clone, Debug)]
pub enum LineTag { Justification(usize), Subproof(usize) }

#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord)] pub struct PremKey(usize);
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord)] pub struct JustKey(usize);
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord)] pub struct SubKey(usize);

#[derive(Clone, Debug)]
pub struct PooledProof {
    prem_map: BTreeMap<PremKey, Expr>,
    just_map: BTreeMap<JustKey, Justification<<PooledProof as Proof>::Reference>>,
    sub_map: BTreeMap<SubKey, Subproof>,
    proof: Subproof,
}

#[derive(Clone, Debug)]
pub struct Subproof {
    premise_list: ZipperVec<PremKey>,
    line_list: ZipperVec<Coprod!(JustKey, SubKey)>,
}

impl Subproof {
    pub fn new() -> Self { Subproof { premise_list: ZipperVec::new(), line_list: ZipperVec::new() } }
}

impl PooledProof {
    pub fn next_premkey(&self) -> PremKey { PremKey(self.prem_map.range(..).next_back().map(|(k, _)| k.0+1).unwrap_or(0)) }
    pub fn next_justkey(&self) -> JustKey { JustKey(self.just_map.range(..).next_back().map(|(k, _)| k.0+1).unwrap_or(0)) }
    pub fn next_subkey(&self) -> SubKey { SubKey(self.sub_map.range(..).next_back().map(|(k, _)| k.0+1).unwrap_or(0)) }
    fn add_pools(&mut self, other: &PooledProof) {
        // TODO: deduplicate values for efficiency on joining subproofs with copies of the same expression multiple times
        let premkey = self.next_premkey(); for (k, v) in other.prem_map.iter() { self.prem_map.insert(PremKey(premkey.0 + k.0), v.clone()); }
        let justkey = self.next_justkey(); for (k, v) in other.just_map.iter() { self.just_map.insert(JustKey(justkey.0 + k.0), v.clone()); }
        let subkey = self.next_subkey(); for (k, v) in other.sub_map.iter() { self.sub_map.insert(SubKey(subkey.0 + k.0), v.clone()); }
    }
}

impl Proof for PooledProof {
    type Reference = Coprod!(PremKey, JustKey, SubKey);
    fn new() -> Self {
        PooledProof { prem_map: BTreeMap::new(), just_map: BTreeMap::new(), sub_map: BTreeMap::new(), proof: Subproof::new(), }
    }
    fn lookup(&self, r: Self::Reference) -> Option<Coprod!(Expr, Justification<Self::Reference>, Self)> {
        r.fold(hlist![
            |ref k| self.prem_map.get(k).map(|x| Coproduct::inject(x.clone())),
            |ref k| self.just_map.get(k).map(|x| Coproduct::inject(x.clone())),
            |ref k| self.sub_map.get(k).map(|x| Coproduct::inject({ let mut p = self.clone(); p.proof = x.clone(); p}))])
    }
    fn add_premise(&mut self, e: Expr) -> Self::Reference {
        let idx = self.next_premkey();
        self.prem_map.insert(idx, e);
        self.proof.premise_list.push(idx);
        Self::Reference::inject(idx)
    }
    fn add_subproof(&mut self, sub: Self) -> Self::Reference {
        self.add_pools(&sub);
        let idx = self.next_subkey();
        self.sub_map.insert(idx, sub.proof);
        self.proof.line_list.push(Coproduct::inject(idx));
        Self::Reference::inject(idx)
    }
    fn add_step(&mut self, just: Justification<Self::Reference>) -> Self::Reference {
        let idx = self.next_justkey();
        self.just_map.insert(idx, just);
        self.proof.line_list.push(Coproduct::inject(idx));
        Self::Reference::inject(idx)
    }
}

#[test]
fn prettyprint_pool() {
    let prf: PooledProof = super::proof_tests::demo_proof_1();
    println!("{:?}", prf)
}
