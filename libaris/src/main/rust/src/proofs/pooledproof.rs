use super::*;
use frunk::hlist::HCons;
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

#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)] pub struct PremKey(usize);
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)] pub struct JustKey(usize);
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)] pub struct SubKey(usize);

type PooledRef = Coprod!(PremKey, JustKey);

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Pools<T> {
    prem_map: BTreeMap<PremKey, T>,
    just_map: BTreeMap<JustKey, Justification<T, PooledRef, SubKey>>,
    sub_map: BTreeMap<SubKey, PooledSubproof<T>>,
}

impl<T> Pools<T> {
    fn new() -> Self {
        Pools { prem_map: BTreeMap::new(), just_map: BTreeMap::new(), sub_map: BTreeMap::new() }
    }
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct PooledProof<T> {
    pools: Box<Pools<T>>,
    proof: PooledSubproof<T>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct PooledSubproof<T> {
    pools: *mut Pools<T>,
    premise_list: ZipperVec<PremKey>,
    line_list: ZipperVec<Coproduct<JustKey, Coproduct<SubKey, frunk::coproduct::CNil>>>,
}

impl<T> PooledSubproof<T> {
    /// PooledSubproof::new requires for safety that p points to something that won't move (e.g. a heap-allocated Box)
    fn new(p: &mut Pools<T>) -> Self { PooledSubproof { pools: p as _, premise_list: ZipperVec::new(), line_list: ZipperVec::new() } }
    fn increment_indices(&mut self, i: PremKey, j: JustKey, k: SubKey) {
        let newprems = self.premise_list.iter().map(|x| PremKey(x.0+i.0)).collect();
        self.premise_list = ZipperVec::from_vec(newprems);
        let newlines = self.line_list.iter().map(|x| x.fold(hlist![|y: JustKey| Coproduct::inject(JustKey(y.0+j.0)), |y: SubKey| Coproduct::inject(SubKey(y.0+k.0))])).collect();
        self.line_list = ZipperVec::from_vec(newlines);
    }
}

impl<T: Clone> Pools<T> {
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
                ])).collect(),
                v.3.iter().map(|x: &SubKey| (SubKey(x.0+subkey.0))).collect())
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


impl<Tail: Default+Clone> Proof for PooledSubproof<HCons<Expr, Tail>> {
    type Reference = PooledRef;
    type SubproofReference = SubKey;
    type Subproof = Self;
    fn new() -> Self { panic!("new is invalid for PooledSubproof, use add_subproof") }
    fn lookup(&self, r: Self::Reference) -> Option<Coprod!(Expr, Justification<Expr, Self::Reference, Self::SubproofReference>)> {
        r.fold(hlist![
            |ref k| unsafe { &*self.pools }.prem_map.get(k).map(|x| Coproduct::inject(x.head.clone())),
            |ref k| unsafe { &*self.pools }.just_map.get(k).map(|x| Coproduct::inject(x.clone().map0(|y| y.head)))])
    }
    fn lookup_subproof(&self, r: Self::SubproofReference) -> Option<Self::Subproof> {
        unsafe { &mut *self.pools }.sub_map.get(&r).map(|x| x.clone() )
    }
    fn with_mut_subproof<A, F: FnOnce(&mut Self::Subproof) -> A>(&mut self, r: &Self::SubproofReference, f: F) -> Option<A> {
        // The subproof pointer, if returned directly, could be invalidated by calls to add_subproof on the same proof object.
        // This is prevented by the fact that the lifetime parameter of the subproof reference cannot occur in A:
        // #[test]
        // fn doesnt_compile() {
        //     let mut p = PooledProof::<Expr>::new();
        //     let r = p.add_subproof();
        //     let s = p.with_mut_subproof(&r, |x| x);
        // }
        unsafe { &mut *self.pools }.sub_map.get_mut(r).map(f)
    }
    fn add_premise(&mut self, e: Expr) -> Self::Reference {
        let pools = unsafe { &mut *self.pools };
        let idx = pools.next_premkey();
        pools.prem_map.insert(idx, HCons { head: e, tail: Tail::default() });
        self.premise_list.push(idx);
        Self::Reference::inject(idx)
    }
    fn add_subproof(&mut self) -> Self::SubproofReference {
        let pools = unsafe { &mut *self.pools };
        let idx = pools.next_subkey();
        let sub = PooledSubproof::new(pools);
        pools.sub_map.insert(idx, sub);
        self.line_list.push(Coproduct::inject(idx));
        idx
    }
    fn add_step(&mut self, just: Justification<Expr, Self::Reference, Self::SubproofReference>) -> Self::Reference {
        let pools = unsafe { &mut *self.pools };
        let idx = pools.next_justkey();
        pools.just_map.insert(idx, Justification(HCons { head: just.0, tail: Tail::default() }, just.1, just.2, just.3));
        self.line_list.push(Coproduct::inject(idx));
        Self::Reference::inject(idx)
    }
    fn premises(&self) -> Vec<Self::Reference> {
        self.premise_list.iter().cloned().map(|x| Coproduct::inject(x)).collect()
    }
    fn lines(&self) -> Vec<Coprod!(Self::Reference, Self::SubproofReference)> {
        use self::Coproduct::{Inl, Inr};
        self.line_list.iter().cloned().map(|x| match x {
            Inl(x) => Coproduct::inject(Coproduct::inject(x)),
            Inr(x) => Coproduct::embed(x),
        }).collect()
    }
    fn verify_line(&self, r: &Self::Reference) -> Result<(), ProofCheckError<Self::Reference, Self::SubproofReference>> {
        // TODO: ReferencesLaterLine check (or should that go in SharedChecks?)
        use self::Coproduct::{Inl, Inr};
        match self.lookup(r.clone()) {
            None => Err(ProofCheckError::LineDoesNotExist(r.clone())),
            Some(Inl(_)) => Ok(()), // premises are always valid
            Some(Inr(Inl(Justification(conclusion, rule, deps, sdeps)))) => rule.check(self, conclusion, deps, sdeps),
            Some(Inr(Inr(void))) => match void {},
        }
    }
}

impl<Tail: Default+Clone> Proof for PooledProof<HCons<Expr, Tail>> {
    type Reference = PooledRef;
    type SubproofReference = SubKey;
    type Subproof = PooledSubproof<HCons<Expr, Tail>>;
    fn new() -> Self {
        let mut pools = Box::new(Pools::new());
        let proof = PooledSubproof::new(&mut *pools);
        PooledProof { pools, proof, }
    }
    fn lookup(&self, r: Self::Reference) -> Option<Coprod!(Expr, Justification<Expr, Self::Reference, Self::SubproofReference>)> { self.proof.lookup(r) }
    fn lookup_subproof(&self, r: Self::SubproofReference) -> Option<Self::Subproof> { self.proof.lookup_subproof(r) }
    fn with_mut_subproof<A, F: FnOnce(&mut Self::Subproof) -> A>(&mut self, r: &Self::SubproofReference, f: F) -> Option<A> { self.proof.with_mut_subproof(r, f) }
    fn add_premise(&mut self, e: Expr) -> Self::Reference { self.proof.add_premise(e) }
    fn add_subproof(&mut self) -> Self::SubproofReference { self.proof.add_subproof() }
    fn add_step(&mut self, just: Justification<Expr, Self::Reference, Self::SubproofReference>) -> Self::Reference { self.proof.add_step(just) }
    fn premises(&self) -> Vec<Self::Reference> { self.proof.premises() }
    fn lines(&self) -> Vec<Coprod!(Self::Reference, Self::SubproofReference)> { self.proof.lines() }
    fn verify_line(&self, r: &Self::Reference) -> Result<(), ProofCheckError<Self::Reference, Self::SubproofReference>> { self.proof.verify_line(r) }
}

#[test]
fn prettyprint_pool() {
    let prf: PooledProof<Hlist![Expr]> = super::proof_tests::demo_proof_1();
    println!("{:?}\n{}\n", prf, prf);
    println!("{:?}\n{:?}\n", prf.premises(), prf.lines());
}

impl<Tail> DisplayIndented for PooledProof<HCons<Expr, Tail>> {
    fn display_indented(&self, fmt: &mut Formatter, indent: usize, linecount: &mut usize) -> std::result::Result<(), std::fmt::Error> {
        fn aux<Tail>(p: &PooledProof<HCons<Expr, Tail>>, fmt: &mut Formatter, indent: usize, linecount: &mut usize, sub: &PooledSubproof<HCons<Expr, Tail>>) -> std::result::Result<(), std::fmt::Error> {
            for idx in sub.premise_list.iter() {
                let premise = p.pools.prem_map.get(idx).unwrap();
                write!(fmt, "{}:\t", linecount)?;
                for _ in 0..indent { write!(fmt, "| ")?; }
                write!(fmt, "{}\n", premise.head)?;
                *linecount += 1;
            }
            write!(fmt, "\t")?;
            for _ in 0..indent { write!(fmt, "| ")?; }
            for _ in 0..10 { write!(fmt, "-")?; }
            write!(fmt, "\n")?;
            for line in sub.line_list.iter() {
                match line.uninject() {
                    Ok(justkey) => { p.pools.just_map.get(&justkey).unwrap().display_indented(fmt, indent, linecount)? },
                    Err(line) => aux(p, fmt, indent+1, linecount, p.pools.sub_map.get(&line.uninject::<SubKey, _>().unwrap() ).unwrap())?,
                }
            }
            Ok(())
        }
        aux(&self, fmt, indent, linecount, &self.proof)
    }
}

impl<Tail> Display for PooledProof<HCons<Expr, Tail>> {
    fn fmt(&self, fmt: &mut Formatter) -> std::result::Result<(), std::fmt::Error> { self.display_indented(fmt, 1, &mut 1) }
}
