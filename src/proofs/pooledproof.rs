/*!
# Structure examples
## Flat proof (excerpted from `test_impelim`)
### ASCII art version
```text
1 | P
2 | P -> Q
  | ---
3 | Q ; "-> Elim", [1, 2]
```
### Pseudo-JSON PooledProof

```text
// all the expression trees are owned by pools, so copying line references for dependencies are cheap
pools = Pools {
    prem_map: {
        PremKey(0): AST("P"),
        PremKey(1): AST("P -> Q")
    },
    just_map; {
        JustKey(0): Justification(AST("Q"), RuleM::ImpElim, [PremKey(0), PremKey(1)], [])
    },
    sub_map: {}
    containing_subproof: {}
}

proof_obj = PooledProof {
    pools: Box(pools),
    proof: root_node,
}

root_node = PooledSubproof {
    pools: &mut pools as *mut Pools<_>,
    premise_list: [PremKey(0), PremKey(1)],
    line_list: [JustKey(0)],
}
```
### Efficient insertion between lines
Note that if another premise "R" is inserted between lines 1 and 2, `PremKey(2): AST("R")` would be added to `pools.prem_map` and `root_node.premise_list` would change to `[PremKey(0), PremKey(2), PremKey(1)]`.
This does not require any renumbering of other lines, and is essential to PooledProof's efficiency.

This is also why premise_list and line_list are `ZipperVec`s instead of `Vec`s (reduces the case of bulk insertion at the beginning/middle of a subproof from quadratic to linear).

## Nested proof (excerpted from `test_forallintro`)
### ASCII art version
```text
1 | forall x, p(x)
2 | forall x, q(x)
  | ----------
  | | ----------
3 | | p(a); ForallElim, [1]
4 | | q(a); ForallElim, [2]
5 | | p(a) & q(a); AndIntro, [3, 4]
6 | forall y, p(y) & q(y); ForallIntro, [3..5]
```
### Pseudo-JSON PooledProof
```text
pools = Pools {
    prem_map: {
        PremKey(0): AST("forall x, p(x)"),
        PremKey(1): AST("forall x, q(x)"),
    },
    just_map; {
        JustKey(0): Justification(AST("p(a)"), RuleM::ForallElim, [PremKey(0)], []),
        JustKey(1): Justification(AST("q(a)"), RuleM::ForallElim, [PremKey(1)], []),
        JustKey(2): Justification(AST("p(a) & q(a)"), RuleM::AndIntro, [JustKey(0), JustKey(1)], []),
        // note that subproof dependencies are in the 4th field of Justification, not the 3rd; this is less ambiguous than the xml representation, and is more type-safe
        JustKey(3): Justification(AST("forall y, p(y) & q(y)"), RuleM::ForallIntro, [], [SubKey(0)]),
    },
    sub_map: {
        SubKey(0): subproof_one,
    }
    containing_subproof: {
        JustKey(0): SubKey(0),
        JustKey(1): SubKey(0),
        JustKey(2): SubKey(0),
        // note that JustKey(3) is absent from containing_subproof, since it's in the root proof node, which has no SubKey
    }
}

proof_obj = PooledProof {
    pools: Box(pools),
    proof: root_node,
}

root_node = PooledSubproof {
    pools: &mut pools as *mut Pools<_>,
    premise_list: [PremKey(0), PremKey(1)],
    line_list: [SubKey(0), JustKey(3)],
}

subproof_one = PooledSubproof {
    pools: &mut pools as *mut Pools<_>,
    premise_list: [],
    line_list: [JustKey(0), JustKey(1), JustKey(2)],
}
```

## Simplifications made in the above illustrations
These illustrations are less verbose than the `Debug` rendering of an actual `PooledProof` for several reasons
### Parse trees
`AST("forall x, p(x)")` will be rendered as the actual AST `Quantifier { symbol: Forall, name: "x", body: Apply { func: Var { name: "p" }, args: [Var { name: "x" }] } }`.
### Coproducts
Various parts of `PooledProof` use `frunk::Coproduct` to create subtypes of the union of `{PremKey, JustKey, SubKey}` to get more precise typing guarantees. This manifests as occurrences of `{Inl,Inr}`.
### Vec vs ZipperVec
While the `[]`s in the `Justification`s are actually `Vec`s and will be that simple in the `Debug` rendering, `premise_list` and `line_list` are `ZipperVec`s for performance, and so the `Debug` rendering reveals where the cursor (roughly, last insertion point) is.
*/

use super::*;
use frunk::hlist::HCons;
use std::collections::BTreeMap;

#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)] pub struct PremKey(usize);
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)] pub struct JustKey(usize);
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)] pub struct SubKey(usize);

type PooledRef = Coprod!(PremKey, JustKey);

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Pools<T> {
    prem_map: BTreeMap<PremKey, T>,
    just_map: BTreeMap<JustKey, Justification<T, PooledRef, SubKey>>,
    sub_map: BTreeMap<SubKey, PooledSubproof<T>>,
    containing_subproof: BTreeMap<Coproduct<PremKey, Coproduct<JustKey, Coproduct<SubKey, frunk::coproduct::CNil>>>, SubKey>,
}

impl<T> Pools<T> {
    fn new() -> Self {
        Pools { prem_map: BTreeMap::new(), just_map: BTreeMap::new(), sub_map: BTreeMap::new(), containing_subproof: BTreeMap::new() }
    }
    fn set_parent(&mut self, idx: Coprod!(PremKey, JustKey, SubKey), sub: &PooledSubproof<T>) {
        for (k, v) in self.sub_map.iter() {
            if v as *const _ as usize == sub as *const _ as usize {
                self.containing_subproof.insert(idx, *k);
                break
            }
        }
    }
    fn parent_of(&self, idx: &Coprod!(PremKey, JustKey, SubKey)) -> Option<SubKey> {
        self.containing_subproof.get(idx).map(|x| x.clone())
    }
    fn transitive_parents(&self, mut idx: Coprod!(PremKey, JustKey, SubKey)) -> HashSet<SubKey> {
        let mut result = HashSet::new();
        while let Some(s) = self.parent_of(&idx) {
            result.insert(s);
            idx = Coproduct::inject(s);
        }
        result
    }
    fn remove_line(&mut self, idx: &Coprod!(PremKey, JustKey)) {
        use frunk::Coproduct::{Inl, Inr};
        match idx {
            Inl(prem) => self.remove_premise(prem),
            Inr(Inl(just)) => self.remove_step(just),
            Inr(Inr(void)) => match *void {},
        }
        let just_map = std::mem::replace(&mut self.just_map, BTreeMap::new());
        self.just_map = just_map.into_iter().map(|(k, Justification(expr, rule, deps, sdeps))| (k, Justification(expr, rule, deps.into_iter().filter(|d| d != idx).collect(), sdeps))).collect();
    }
    fn remove_premise(&mut self, idx: &PremKey) {
        self.prem_map.remove(idx);
        for (_, v) in self.sub_map.iter_mut() {
            let premise_list = std::mem::replace(&mut v.premise_list, ZipperVec::new());
            v.premise_list = ZipperVec::from_vec(premise_list.iter().filter(|x| x != &idx).cloned().collect());
        }
    }
    fn remove_step(&mut self, idx: &JustKey) {
        self.just_map.remove(idx);
        for (_, v) in self.sub_map.iter_mut() {
            let line_list = std::mem::replace(&mut v.line_list, ZipperVec::new());
            v.line_list = ZipperVec::from_vec(line_list.iter().filter(|x| x.get() != Some(idx)).cloned().collect());
        }
    }
    fn remove_subproof(&mut self, idx: &SubKey) {
        self.sub_map.remove(idx);
        for (_, v) in self.sub_map.iter_mut() {
            let line_list = std::mem::replace(&mut v.line_list, ZipperVec::new());
            v.line_list = ZipperVec::from_vec(line_list.iter().filter(|x| x.get() != Some(idx)).cloned().collect());
        }
        let just_map = std::mem::replace(&mut self.just_map, BTreeMap::new());
        self.just_map = just_map.into_iter().map(|(k, Justification(expr, rule, deps, sdeps))| (k, Justification(expr, rule, deps, sdeps.into_iter().filter(|d| d != idx).collect()))).collect();
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
    fn top_level_proof(&self) -> &Self { self }
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
        pools.set_parent(Coproduct::inject(idx), self);
        self.premise_list.push(idx);
        Self::Reference::inject(idx)
    }
    fn add_subproof(&mut self) -> Self::SubproofReference {
        let pools = unsafe { &mut *self.pools };
        let idx = pools.next_subkey();
        pools.set_parent(Coproduct::inject(idx), self);
        let sub = PooledSubproof::new(pools);
        pools.sub_map.insert(idx, sub);
        self.line_list.push(Coproduct::inject(idx));
        idx
    }
    fn add_step(&mut self, just: Justification<Expr, Self::Reference, Self::SubproofReference>) -> Self::Reference {
        let pools = unsafe { &mut *self.pools };
        let idx = pools.next_justkey();
        pools.set_parent(Coproduct::inject(idx), self);

        // Prevent a justification from depending on the whole subproof that it resides in, as that leads to unsoundness
        // 1 | | Top
        //   | | -----
        // 2 | | Top -> P ; -> Intro subproof_beginning_at(1) // problematic line, depends on conclusion
        // 3 | | P ; -> Elim statement_at(1), statement_at(2)
        // 4 | Top -> P ; -> Intro subproof_beginning_at(1)
        let tps = pools.transitive_parents(Coproduct::inject(idx));
        let Justification(e, r, deps, mut sdeps) = just;
        // silently remove sdeps that are invalid due to this pattern, as they show up naturally when parsing Aris's xml due to an ambiguity between deps and sdeps
        sdeps = sdeps.into_iter().filter(|x| !tps.contains(x)).collect();

        self.line_list.push(Coproduct::inject(idx));
        pools.just_map.insert(idx, Justification(HCons { head: e, tail: Tail::default() }, r, deps, sdeps));

        Self::Reference::inject(idx)
    }
    fn add_premise_relative(&mut self, e: Expr, r: Self::Reference, after: bool) -> Self::Reference {
        let pools = unsafe { &mut *self.pools };
        let idx = pools.next_premkey();
        pools.prem_map.insert(idx, HCons { head: e, tail: Tail::default() });
        pools.set_parent(Coproduct::inject(idx), self);
        use self::Coproduct::{Inl, Inr};
        if let Some(s) = pools.parent_of(&Coproduct::embed(r)) {
            self.with_mut_subproof(&s, |sub| {
                match r {
                    Inl(pr) => sub.premise_list.insert_relative(idx, &pr, after),
                    Inr(Inl(_)) => sub.premise_list.push(idx), // if inserting before a step, insert at the end of premises
                    Inr(Inr(void)) => match void {},
                }
            });
        } else {
            match r {
                Inl(pr) => self.premise_list.insert_relative(idx, &pr, after),
                Inr(Inl(_)) => self.premise_list.push(idx), // if inserting before a step, insert at the end of premises
                Inr(Inr(void)) => match void {},
            }
        }
        Coproduct::inject(idx)
    }
    fn add_subproof_relative(&mut self, r: Self::Reference, after: bool) -> Self::SubproofReference {
        let pools = unsafe { &mut *self.pools };
        let idx = pools.next_subkey();
        let sub = PooledSubproof::new(pools);
        pools.sub_map.insert(idx, sub);
        if let Some(s) = pools.parent_of(&Coproduct::embed(r)) {
            self.with_mut_subproof(&s, |sub| {
                let jr: &JustKey = r.get().unwrap(); // TODO: allow r to be a PremKey if after is true
                sub.line_list.insert_relative(Coproduct::inject(idx), &Coproduct::inject(*jr), after);
            });
        } else {
            let jr: &JustKey = r.get().unwrap(); // TODO: allow r to be a PremKey if after is true
            self.line_list.insert_relative(Coproduct::inject(idx), &Coproduct::inject(*jr), after);
        }
        idx
    }
    fn add_step_relative(&mut self, just: Justification<Expr, Self::Reference, Self::SubproofReference>, r: Self::Reference, after: bool) -> Self::Reference {
        let pools = unsafe { &mut *self.pools };
        let idx = pools.next_justkey();
        // TODO: occurs-before check
        pools.just_map.insert(idx, Justification(HCons { head: just.0, tail: Tail::default() }, just.1, just.2, just.3));
        pools.set_parent(Coproduct::inject(idx), self);
        use self::Coproduct::{Inl, Inr};
        if let Some(s) = pools.parent_of(&Coproduct::embed(r)) {
            self.with_mut_subproof(&s, |sub| {
                match r {
                    Inl(_) => sub.line_list.push_front(Coproduct::inject(idx)), // if inserting after a premise, insert at the beginning of lines
                    Inr(Inl(jr)) => sub.line_list.insert_relative(Coproduct::inject(idx), &Coproduct::inject(jr.clone()), after),
                    Inr(Inr(void)) => match void {},
                }
            });
        } else {
            match r {
                Inl(_) => self.line_list.push_front(Coproduct::inject(idx)), // if inserting after a premise, insert at the beginning of lines
                Inr(Inl(jr)) => self.line_list.insert_relative(Coproduct::inject(idx), &Coproduct::inject(jr.clone()), after),
                Inr(Inr(void)) => match void {},
            }
        }
        Coproduct::inject(idx)
    }
    fn remove_line(&mut self, r: Self::Reference) {
        let pools = unsafe { &mut *self.pools };
        pools.remove_line(&r);
    }
    fn remove_subproof(&mut self, r: Self::SubproofReference) {
        let pools = unsafe { &mut *self.pools };
        pools.remove_subproof(&r);
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
    fn parent_of_line(&self, r: &Coprod!(Self::Reference, Self::SubproofReference)) -> Option<Self::SubproofReference> {
        let pools = unsafe { &mut *self.pools };
        use self::Coproduct::{Inl, Inr};
        match r {
            Inl(r) => pools.parent_of(&Coproduct::embed(*r)),
            Inr(Inl(r)) => pools.parent_of(&Coproduct::inject(*r)),
            Inr(Inr(void)) => match *void {},
        }
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
    fn top_level_proof(&self) -> &Self::Subproof { &self.proof }
    fn lookup(&self, r: Self::Reference) -> Option<Coprod!(Expr, Justification<Expr, Self::Reference, Self::SubproofReference>)> { self.proof.lookup(r) }
    fn lookup_subproof(&self, r: Self::SubproofReference) -> Option<Self::Subproof> { self.proof.lookup_subproof(r) }
    fn with_mut_subproof<A, F: FnOnce(&mut Self::Subproof) -> A>(&mut self, r: &Self::SubproofReference, f: F) -> Option<A> { self.proof.with_mut_subproof(r, f) }
    fn add_premise(&mut self, e: Expr) -> Self::Reference { self.proof.add_premise(e) }
    fn add_subproof(&mut self) -> Self::SubproofReference { self.proof.add_subproof() }
    fn add_premise_relative(&mut self, e: Expr, r: Self::Reference, after: bool) -> Self::Reference { self.proof.add_premise_relative(e, r, after) }
    fn add_subproof_relative(&mut self, r: Self::Reference, after: bool) -> Self::SubproofReference { self.proof.add_subproof_relative(r, after) }
    fn add_step_relative(&mut self, just: Justification<Expr, Self::Reference, Self::SubproofReference>, r: Self::Reference, after: bool) -> Self::Reference { self.proof.add_step_relative(just, r, after) }
    fn add_step(&mut self, just: Justification<Expr, Self::Reference, Self::SubproofReference>) -> Self::Reference { self.proof.add_step(just) }
    fn remove_line(&mut self, r: Self::Reference) {
        let premise_list = std::mem::replace(&mut self.proof.premise_list, ZipperVec::new());
        self.proof.premise_list = ZipperVec::from_vec(premise_list.iter().filter(|x| Some(x) != r.get().as_ref()).cloned().collect());
        let line_list = std::mem::replace(&mut self.proof.line_list, ZipperVec::new());
        self.proof.line_list = ZipperVec::from_vec(line_list.iter().filter(|x| (x.get::<JustKey, _>() != r.get()) || x.get::<SubKey, _>().is_some()).cloned().collect());
        self.proof.remove_line(r);
    }
    fn remove_subproof(&mut self, r: Self::SubproofReference) {
        let line_list = std::mem::replace(&mut self.proof.line_list, ZipperVec::new());
        self.proof.line_list = ZipperVec::from_vec(line_list.iter().filter(|x| x.get() != Some(&r)).cloned().collect());
        self.proof.remove_subproof(r);
    }
    fn premises(&self) -> Vec<Self::Reference> { self.proof.premises() }
    fn lines(&self) -> Vec<Coprod!(Self::Reference, Self::SubproofReference)> { self.proof.lines() }
    fn parent_of_line(&self, r: &Coprod!(Self::Reference, Self::SubproofReference)) -> Option<Self::SubproofReference> { self.proof.parent_of_line(r) }
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
