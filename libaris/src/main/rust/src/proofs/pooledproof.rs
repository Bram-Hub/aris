use super::*;
use std::collections::BTreeMap;

/// a ZipperVec represents a list-with-edit-position [a,b,c, EDIT_CURSOR, d, e, f] as (vec![a, b, c], vec![f, e, d])
/// since Vec's have O(1) insert/remove at the end, ZipperVec's have O(1) insert/removal around the edit cursor, while being way more cache/memory efficient than a doubly-linked list
/// the cursor can be moved from position i to position j in O(|i-j|) time by shuffling elements between the prefix and the suffix
// TODO: should ZipperVec have a seperate module?
// TODO: should Eq for ZipperVec quotient out cursor position?
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ZipperVec<T> {
    prefix: Vec<T>,
    suffix_r: Vec<T>,
}
impl<T> ZipperVec<T> {
    pub fn new() -> Self { ZipperVec { prefix: Vec::new(), suffix_r: Vec::new() } }
    pub fn from_vec(v: Vec<T>) -> Self { ZipperVec { prefix: v, suffix_r: Vec::new() } }
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
    pub fn iter(&self) -> impl Iterator<Item=&T> {
        self.prefix.iter().chain(self.suffix_r.iter().rev())
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord)] pub struct PremKey(usize);
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord)] pub struct JustKey(usize);
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord)] pub struct SubKey(usize);

type PooledRef = Coprod!(PremKey, JustKey, SubKey);

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct PooledProof<T> {
    prem_map: BTreeMap<PremKey, T>,
    just_map: BTreeMap<JustKey, Justification<T, PooledRef>>,
    sub_map: BTreeMap<SubKey, Subproof>,
    proof: Subproof,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Subproof {
    premise_list: ZipperVec<PremKey>,
    line_list: ZipperVec<Coprod!(JustKey, SubKey)>,
}

impl Subproof {
    pub fn new() -> Self { Subproof { premise_list: ZipperVec::new(), line_list: ZipperVec::new() } }
    fn increment_indices(&mut self, i: PremKey, j: JustKey, k: SubKey) {
        let newprems = self.premise_list.iter().map(|x| PremKey(x.0+i.0)).collect();
        self.premise_list = ZipperVec::from_vec(newprems);
        let newlines = self.line_list.iter().map(|x| x.fold(hlist![|y: JustKey| Coproduct::inject(JustKey(y.0+j.0)), |y: SubKey| Coproduct::inject(SubKey(y.0+k.0))])).collect();
        self.line_list = ZipperVec::from_vec(newlines);
    }
}

impl<PREM: Clone> PooledProof<PREM> {
    pub fn next_premkey(&self) -> PremKey { PremKey(self.prem_map.range(..).next_back().map(|(k, _)| k.0+1).unwrap_or(0)) }
    pub fn next_justkey(&self) -> JustKey { JustKey(self.just_map.range(..).next_back().map(|(k, _)| k.0+1).unwrap_or(0)) }
    pub fn next_subkey(&self) -> SubKey { SubKey(self.sub_map.range(..).next_back().map(|(k, _)| k.0+1).unwrap_or(0)) }
    fn add_pools(&mut self, other: &Self) -> (PremKey, JustKey, SubKey) {
        // TODO: deduplicate values for efficiency on joining subproofs with copies of the same expression multiple times
        let premkey = self.next_premkey(); let justkey = self.next_justkey(); let subkey = self.next_subkey();
        for (k, v) in other.prem_map.iter() { self.prem_map.insert(PremKey(premkey.0 + k.0), v.clone()); }
        for (k, v) in other.just_map.iter() {
            self.just_map.insert(JustKey(justkey.0 + k.0),
                Justification(v.0.clone(), v.1, v.2.iter().map(|x| x.fold(hlist![
                    |x: PremKey| Coproduct::inject(PremKey(x.0+premkey.0)),
                    |x: JustKey| Coproduct::inject(JustKey(x.0+justkey.0)),
                    |x: SubKey| Coproduct::inject(SubKey(x.0+subkey.0)),
                ])).collect())
            );
        }
        for (k, v) in other.sub_map.iter() {
            let mut w = v.clone();
            w.increment_indices(premkey, justkey, subkey);
            self.sub_map.insert(SubKey(subkey.0 + k.0), w);
        }
        (premkey, justkey, subkey)
    }
}

impl Proof for PooledProof<Expr> {
    type Reference = PooledRef;
    fn new() -> Self {
        PooledProof { prem_map: BTreeMap::new(), just_map: BTreeMap::new(), sub_map: BTreeMap::new(), proof: Subproof::new(), }
    }
    fn lookup(&self, r: Self::Reference) -> Option<Coprod!(Expr, Justification<Expr, Self::Reference>, Self)> {
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
    fn add_subproof(&mut self, mut sub: Self) -> Self::Reference {
        let (i, j, k) = self.add_pools(&sub);
        let idx = self.next_subkey();
        sub.proof.increment_indices(i, j, k);
        self.sub_map.insert(idx, sub.proof);
        self.proof.line_list.push(Coproduct::inject(idx));
        Self::Reference::inject(idx)
    }
    fn add_step(&mut self, just: Justification<Expr, Self::Reference>) -> Self::Reference {
        let idx = self.next_justkey();
        self.just_map.insert(idx, just);
        self.proof.line_list.push(Coproduct::inject(idx));
        Self::Reference::inject(idx)
    }
}

#[test]
fn prettyprint_pool() {
    let prf: PooledProof<Expr> = super::proof_tests::demo_proof_1();
    println!("{:?}\n{}\n", prf, prf)
}

impl DisplayIndented for PooledProof<Expr> {
    fn display_indented(&self, fmt: &mut Formatter, indent: usize, linecount: &mut usize) -> std::result::Result<(), std::fmt::Error> {
        fn aux(p: &PooledProof<Expr>, fmt: &mut Formatter, indent: usize, linecount: &mut usize, sub: &Subproof) -> std::result::Result<(), std::fmt::Error> {
            for idx in sub.premise_list.iter() {
                let premise = p.prem_map.get(idx).unwrap();
                write!(fmt, "{}:\t", linecount)?;
                for _ in 0..indent { write!(fmt, "| ")?; }
                write!(fmt, "{:?}\n", premise)?; // TODO Display for Expr
                *linecount += 1;
            }
            write!(fmt, "\t")?;
            for _ in 0..indent { write!(fmt, "| ")?; }
            for _ in 0..10 { write!(fmt, "-")?; }
            write!(fmt, "\n")?;
            for line in sub.line_list.iter() {
                match line.uninject() {
                    Ok(justkey) => p.just_map.get(&justkey).unwrap().display_indented(fmt, indent, linecount)?,
                    Err(line) => aux(p, fmt, indent+1, linecount, p.sub_map.get(&line.uninject::<SubKey, _>().unwrap()).unwrap())?,
                }
            }
            Ok(())
        }
        aux(&self, fmt, indent, linecount, &self.proof)
    }
}
impl Display for PooledProof<Expr> {
    fn fmt(&self, fmt: &mut Formatter) -> std::result::Result<(), std::fmt::Error> { self.display_indented(fmt, 1, &mut 1) }
}
