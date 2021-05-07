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
Various parts of `PooledProof` use `frunk_core::coproduct::Coproduct` to create subtypes of the union of `{PremKey, JustKey, SubKey}` to get more precise typing guarantees. This manifests as occurrences of `{Inl,Inr}`.
### Vec vs ZipperVec
While the `[]`s in the `Justification`s are actually `Vec`s and will be that simple in the `Debug` rendering, `premise_list` and `line_list` are `ZipperVec`s for performance, and so the `Debug` rendering reveals where the cursor (roughly, last insertion point) is.
*/

use crate::expr::Expr;
use crate::proofs::js_to_pjs;
use crate::proofs::DisplayIndented;
use crate::proofs::JsRef;
use crate::proofs::Justification;
use crate::proofs::PjRef;
use crate::proofs::PjsRef;
use crate::proofs::Proof;
use crate::rules::ProofCheckError;
use crate::rules::RuleT;
use crate::zipper_vec::ZipperVec;

use std::collections::BTreeMap;
use std::collections::HashSet;

use frunk_core::coproduct::Coproduct;
use frunk_core::hlist::HCons;
use frunk_core::Coprod;

#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub struct PremKey(usize);

#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub struct JustKey(usize);

#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub struct SubKey(usize);

type PooledRef = Coprod!(PremKey, JustKey);
type PjsKey = Coprod!(PremKey, JustKey, SubKey);

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Pools<T> {
    prem_map: BTreeMap<PremKey, T>,
    just_map: BTreeMap<JustKey, Justification<T, PooledRef, SubKey>>,
    sub_map: BTreeMap<SubKey, PooledSubproof<T>>,
    containing_subproof: BTreeMap<PjsKey, SubKey>,
}

impl<T> Pools<T> {
    fn new() -> Self {
        Pools { prem_map: BTreeMap::new(), just_map: BTreeMap::new(), sub_map: BTreeMap::new(), containing_subproof: BTreeMap::new() }
    }
    fn subproof_to_subkey(&self, sub: &PooledSubproof<T>) -> Option<SubKey> {
        for (k, v) in self.sub_map.iter() {
            if std::ptr::eq(v as *const _, sub as *const _) {
                return Some(*k);
            }
        }
        None
    }
    fn set_parent(&mut self, idx: Coprod!(PremKey, JustKey, SubKey), sub: &PooledSubproof<T>) {
        if let Some(sk) = self.subproof_to_subkey(sub) {
            self.containing_subproof.insert(idx, sk);
        }
    }
    fn parent_of(&self, idx: &Coprod!(PremKey, JustKey, SubKey)) -> Option<SubKey> {
        self.containing_subproof.get(idx).copied()
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
        use frunk_core::coproduct::Coproduct::{Inl, Inr};
        match idx {
            Inl(prem) => self.remove_premise(prem),
            Inr(Inl(just)) => self.remove_step(just),
            Inr(Inr(void)) => match *void {},
        }
    }
    fn remove_line_helper(&mut self, idx: &Coprod!(PremKey, JustKey, SubKey)) {
        use frunk_core::coproduct::Coproduct::{Inl, Inr};
        let just_map = std::mem::take(&mut self.just_map);
        self.just_map = just_map
            .into_iter()
            .map(|(k, Justification(expr, rule, deps, sdeps))| {
                let (new_deps, new_sdeps) = match idx {
                    Inl(pr) => (deps.into_iter().filter(|d| d != &Coproduct::inject(*pr)).collect(), sdeps),
                    Inr(Inl(jr)) => (deps.into_iter().filter(|d| d != &Coproduct::inject(*jr)).collect(), sdeps),
                    Inr(Inr(Inl(sr))) => (deps, sdeps.into_iter().filter(|d| d != sr).collect()),
                    Inr(Inr(Inr(void))) => match *void {},
                };
                (k, Justification(expr, rule, new_deps, new_sdeps))
            })
            .collect();
        self.containing_subproof.remove(idx);
    }
    fn remove_premise(&mut self, idx: &PremKey) {
        self.prem_map.remove(idx);
        for (_, v) in self.sub_map.iter_mut() {
            let premise_list = std::mem::replace(&mut v.premise_list, ZipperVec::new());
            v.premise_list = ZipperVec::from_vec(premise_list.iter().filter(|x| x != &idx).cloned().collect());
        }
        self.remove_line_helper(&Coproduct::inject(*idx));
    }
    fn remove_step(&mut self, idx: &JustKey) {
        self.just_map.remove(idx);
        for (_, v) in self.sub_map.iter_mut() {
            let line_list = std::mem::replace(&mut v.line_list, ZipperVec::new());
            v.line_list = ZipperVec::from_vec(line_list.iter().filter(|x| x.get() != Some(idx)).cloned().collect());
        }
        self.remove_line_helper(&Coproduct::inject(*idx));
    }
    fn remove_subproof(&mut self, idx: &SubKey) {
        use frunk_core::coproduct::Coproduct::{Inl, Inr};
        if let Some(sub) = self.sub_map.remove(idx) {
            for prem in sub.premise_list.iter() {
                self.remove_premise(prem);
            }
            for line in sub.line_list.iter() {
                match line {
                    Inl(jr) => {
                        self.remove_step(jr);
                    }
                    Inr(Inl(sr)) => {
                        self.remove_subproof(sr);
                    }
                    Inr(Inr(void)) => match *void {},
                }
            }
        }
        for (_, v) in self.sub_map.iter_mut() {
            let line_list = std::mem::replace(&mut v.line_list, ZipperVec::new());
            v.line_list = ZipperVec::from_vec(line_list.iter().filter(|x| x.get() != Some(idx)).cloned().collect());
        }
        self.remove_line_helper(&Coproduct::inject(*idx));
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
    line_list: ZipperVec<Coproduct<JustKey, Coproduct<SubKey, frunk_core::coproduct::CNil>>>,
}

impl<T> PooledSubproof<T> {
    /// PooledSubproof::new requires for safety that p points to something that won't move (e.g. a heap-allocated Box)
    fn new(p: &mut Pools<T>) -> Self {
        PooledSubproof { pools: p as _, premise_list: ZipperVec::new(), line_list: ZipperVec::new() }
    }
}

impl<T: Clone> Pools<T> {
    /// Get next unused key in pool's premises map
    pub fn next_premkey(&self) -> PremKey {
        // Increment highest index
        PremKey(self.prem_map.keys().next_back().map(|key| key.0 + 1).unwrap_or(0))
    }
    /// Get next unused key in pool's justifications map
    pub fn next_justkey(&self) -> JustKey {
        // Increment highest index
        JustKey(self.just_map.keys().next_back().map(|key| key.0 + 1).unwrap_or(0))
    }
    /// Get next unused key in pool's subproofs map
    pub fn next_subkey(&self) -> SubKey {
        // Increment highest index
        SubKey(self.sub_map.keys().next_back().map(|key| key.0 + 1).unwrap_or(0))
    }
}

impl<Tail: Default + Clone> Proof for PooledSubproof<HCons<Expr, Tail>> {
    type PremiseReference = PremKey;
    type JustificationReference = JustKey;
    type SubproofReference = SubKey;
    type Subproof = Self;
    fn new() -> Self {
        panic!("new is invalid for PooledSubproof, use add_subproof")
    }
    fn top_level_proof(&self) -> &Self {
        self
    }
    fn lookup_premise(&self, r: &Self::PremiseReference) -> Option<Expr> {
        unsafe { &*self.pools }.prem_map.get(r).map(|x| x.head.clone())
    }
    fn lookup_step(&self, r: &Self::JustificationReference) -> Option<Justification<Expr, PjRef<Self>, Self::SubproofReference>> {
        unsafe { &*self.pools }.just_map.get(r).map(|x| x.clone().map0(|y| y.head))
    }
    fn lookup_subproof(&self, r: &Self::SubproofReference) -> Option<Self::Subproof> {
        unsafe { &mut *self.pools }.sub_map.get(r).cloned()
    }
    fn with_mut_premise<A, F: FnOnce(&mut Expr) -> A>(&mut self, r: &Self::PremiseReference, f: F) -> Option<A> {
        let pools = unsafe { &mut *self.pools };
        pools.prem_map.get_mut(r).map(|p: &mut HCons<Expr, Tail>| f(p.get_mut()))
    }
    fn with_mut_step<A, F: FnOnce(&mut Justification<Expr, PjRef<Self>, Self::SubproofReference>) -> A>(&mut self, r: &Self::JustificationReference, f: F) -> Option<A> {
        let pools = unsafe { &mut *self.pools };
        pools.just_map.get_mut(r).map(|j_hcons: &mut Justification<HCons<Expr, Tail>, _, _>| {
            let mut j_expr: Justification<Expr, _, _> = Justification(j_hcons.0.get().clone(), j_hcons.1, j_hcons.2.clone(), j_hcons.3.clone());
            let ret = f(&mut j_expr);
            *j_hcons.0.get_mut() = j_expr.0;
            j_hcons.1 = j_expr.1;
            j_hcons.2 = j_expr.2;
            j_hcons.3 = j_expr.3;
            ret
        })
    }
    fn with_mut_subproof<A, F: FnOnce(&mut Self::Subproof) -> A>(&mut self, r: &Self::SubproofReference, f: F) -> Option<A> {
        unsafe { &mut *self.pools }.sub_map.get_mut(r).map(f)
    }
    fn add_premise(&mut self, e: Expr) -> Self::PremiseReference {
        let pools = unsafe { &mut *self.pools };
        let idx = pools.next_premkey();
        pools.prem_map.insert(idx, HCons { head: e, tail: Tail::default() });
        pools.set_parent(Coproduct::inject(idx), self);
        self.premise_list.push(idx);
        idx
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
    fn add_step(&mut self, just: Justification<Expr, PjRef<Self>, Self::SubproofReference>) -> Self::JustificationReference {
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

        idx
    }
    fn add_premise_relative(&mut self, e: Expr, r: &Self::PremiseReference, after: bool) -> Self::PremiseReference {
        let pools = unsafe { &mut *self.pools };
        let idx = pools.next_premkey();
        pools.prem_map.insert(idx, HCons { head: e, tail: Tail::default() });
        if let Some(s) = pools.parent_of(&Coproduct::inject(*r)) {
            self.with_mut_subproof(&s, |sub| {
                pools.set_parent(Coproduct::inject(idx), sub);
                sub.premise_list.insert_relative(idx, r, after);
            });
        } else {
            self.premise_list.insert_relative(idx, r, after);
        }
        idx
    }
    fn add_subproof_relative(&mut self, r: &JsRef<Self>, after: bool) -> Self::SubproofReference {
        let pools = unsafe { &mut *self.pools };
        let idx = pools.next_subkey();
        let sub = PooledSubproof::new(pools);
        pools.sub_map.insert(idx, sub);
        if let Some(s) = pools.parent_of(&js_to_pjs::<Self>(*r)) {
            self.with_mut_subproof(&s, |sub| {
                pools.set_parent(Coproduct::inject(idx), sub);
                sub.line_list.insert_relative(Coproduct::inject(idx), r, after);
            });
        } else {
            self.line_list.insert_relative(Coproduct::inject(idx), r, after);
        }
        idx
    }
    fn add_step_relative(&mut self, just: Justification<Expr, PjRef<Self>, Self::SubproofReference>, r: &JsRef<Self>, after: bool) -> Self::JustificationReference {
        let pools = unsafe { &mut *self.pools };
        let idx = pools.next_justkey();
        // TODO: occurs-before check
        pools.just_map.insert(idx, Justification(HCons { head: just.0, tail: Tail::default() }, just.1, just.2, just.3));
        if let Some(s) = pools.parent_of(&js_to_pjs::<Self>(*r)) {
            self.with_mut_subproof(&s, |sub| {
                pools.set_parent(Coproduct::inject(idx), sub);
                sub.line_list.insert_relative(Coproduct::inject(idx), r, after);
            });
        } else {
            self.line_list.insert_relative(Coproduct::inject(idx), r, after);
        }
        idx
    }
    fn remove_line(&mut self, r: &PjRef<Self>) {
        let pools = unsafe { &mut *self.pools };
        pools.remove_line(r);
    }
    fn remove_subproof(&mut self, r: &Self::SubproofReference) {
        let pools = unsafe { &mut *self.pools };
        pools.remove_subproof(r);
    }
    fn premises(&self) -> Vec<Self::PremiseReference> {
        self.premise_list.iter().cloned().collect()
    }
    fn lines(&self) -> Vec<JsRef<Self>> {
        self.line_list.iter().cloned().collect()
    }
    fn parent_of_line(&self, r: &PjsRef<Self>) -> Option<Self::SubproofReference> {
        let pools = unsafe { &mut *self.pools };
        pools.parent_of(r)
    }
    fn verify_line(&self, r: &PjRef<Self>) -> Result<(), ProofCheckError<PjRef<Self>, Self::SubproofReference>> {
        use self::Coproduct::{Inl, Inr};
        match self.lookup_pj(r) {
            None => Err(ProofCheckError::LineDoesNotExist(*r)),
            Some(Inl(_)) => Ok(()), // premises are always valid
            Some(Inr(Inl(Justification(conclusion, rule, deps, sdeps)))) => {
                // TODO: efficient caching for ReferencesLaterLine check, so this isn't potentially O(n)
                let mut valid_deps = HashSet::new();
                let mut valid_sdeps = HashSet::new();
                self.possible_deps_for_line(r, &mut valid_deps, &mut valid_sdeps);
                println!("possible_deps_for_line: {:?} {:?} {:?}", r, valid_deps, valid_sdeps);

                for dep in deps.iter() {
                    let dep_co = Coproduct::inject(*dep);
                    if !self.can_reference_dep(r, &dep_co) {
                        return Err(ProofCheckError::ReferencesLaterLine(*r, dep_co));
                    }
                }
                for sdep in sdeps.iter() {
                    let sdep_co = Coproduct::inject(*sdep);
                    if !self.can_reference_dep(r, &sdep_co) {
                        return Err(ProofCheckError::ReferencesLaterLine(*r, sdep_co));
                    }
                }
                rule.check(self, conclusion, deps, sdeps)
            }
            Some(Inr(Inr(void))) => match void {},
        }
    }
}

impl<Tail: Default + Clone> Proof for PooledProof<HCons<Expr, Tail>> {
    type PremiseReference = PremKey;
    type JustificationReference = JustKey;
    type SubproofReference = SubKey;
    type Subproof = PooledSubproof<HCons<Expr, Tail>>;
    fn new() -> Self {
        let mut pools = Box::new(Pools::new());
        let proof = PooledSubproof::new(&mut *pools);
        PooledProof { pools, proof }
    }
    fn top_level_proof(&self) -> &Self::Subproof {
        &self.proof
    }
    fn lookup_premise(&self, r: &Self::PremiseReference) -> Option<Expr> {
        self.proof.lookup_premise(r)
    }
    fn lookup_step(&self, r: &Self::JustificationReference) -> Option<Justification<Expr, PjRef<Self>, Self::SubproofReference>> {
        self.proof.lookup_step(r)
    }
    fn lookup_subproof(&self, r: &Self::SubproofReference) -> Option<Self::Subproof> {
        self.proof.lookup_subproof(r)
    }
    fn with_mut_premise<A, F: FnOnce(&mut Expr) -> A>(&mut self, r: &Self::PremiseReference, f: F) -> Option<A> {
        self.proof.with_mut_premise(r, f)
    }
    fn with_mut_step<A, F: FnOnce(&mut Justification<Expr, PjRef<Self>, Self::SubproofReference>) -> A>(&mut self, r: &Self::JustificationReference, f: F) -> Option<A> {
        self.proof.with_mut_step(r, f)
    }
    fn with_mut_subproof<A, F: FnOnce(&mut Self::Subproof) -> A>(&mut self, r: &Self::SubproofReference, f: F) -> Option<A> {
        self.proof.with_mut_subproof(r, f)
    }
    fn add_premise(&mut self, e: Expr) -> Self::PremiseReference {
        self.proof.add_premise(e)
    }
    fn add_subproof(&mut self) -> Self::SubproofReference {
        self.proof.add_subproof()
    }
    fn add_premise_relative(&mut self, e: Expr, r: &Self::PremiseReference, after: bool) -> Self::PremiseReference {
        self.proof.add_premise_relative(e, r, after)
    }
    fn add_subproof_relative(&mut self, r: &JsRef<Self>, after: bool) -> Self::SubproofReference {
        self.proof.add_subproof_relative(r, after)
    }
    fn add_step_relative(&mut self, just: Justification<Expr, PjRef<Self>, Self::SubproofReference>, r: &JsRef<Self>, after: bool) -> Self::JustificationReference {
        self.proof.add_step_relative(just, r, after)
    }
    fn add_step(&mut self, just: Justification<Expr, PjRef<Self>, Self::SubproofReference>) -> Self::JustificationReference {
        self.proof.add_step(just)
    }
    fn remove_line(&mut self, r: &PjRef<Self>) {
        let premise_list = std::mem::replace(&mut self.proof.premise_list, ZipperVec::new());
        self.proof.premise_list = ZipperVec::from_vec(premise_list.iter().filter(|x| Some(x) != r.get().as_ref()).cloned().collect());
        let line_list = std::mem::replace(&mut self.proof.line_list, ZipperVec::new());
        self.proof.line_list = ZipperVec::from_vec(line_list.iter().filter(|x| (x.get::<JustKey, _>() != r.get()) || x.get::<SubKey, _>().is_some()).cloned().collect());
        self.proof.remove_line(r);
    }
    fn remove_subproof(&mut self, r: &Self::SubproofReference) {
        let line_list = std::mem::replace(&mut self.proof.line_list, ZipperVec::new());
        self.proof.line_list = ZipperVec::from_vec(line_list.iter().filter(|x| x.get() != Some(r)).cloned().collect());
        self.proof.remove_subproof(r);
    }
    fn premises(&self) -> Vec<Self::PremiseReference> {
        self.proof.premises()
    }
    fn lines(&self) -> Vec<JsRef<Self>> {
        self.proof.lines()
    }
    fn parent_of_line(&self, r: &PjsRef<Self>) -> Option<Self::SubproofReference> {
        self.proof.parent_of_line(r)
    }
    fn verify_line(&self, r: &PjRef<Self>) -> Result<(), ProofCheckError<PjRef<Self>, Self::SubproofReference>> {
        self.proof.verify_line(r)
    }
}

impl<Tail> DisplayIndented for PooledProof<HCons<Expr, Tail>> {
    fn display_indented(&self, fmt: &mut std::fmt::Formatter, indent: usize, linecount: &mut usize) -> std::result::Result<(), std::fmt::Error> {
        fn aux<Tail>(p: &PooledProof<HCons<Expr, Tail>>, fmt: &mut std::fmt::Formatter, indent: usize, linecount: &mut usize, sub: &PooledSubproof<HCons<Expr, Tail>>) -> std::result::Result<(), std::fmt::Error> {
            for idx in sub.premise_list.iter() {
                let premise = p.pools.prem_map.get(idx).unwrap();
                write!(fmt, "{}:\t", linecount)?;
                for _ in 0..indent {
                    write!(fmt, "| ")?;
                }
                writeln!(fmt, "{}", premise.head)?;
                *linecount += 1;
            }
            write!(fmt, "\t")?;
            for _ in 0..indent {
                write!(fmt, "| ")?;
            }
            for _ in 0..10 {
                write!(fmt, "-")?;
            }
            writeln!(fmt)?;
            for line in sub.line_list.iter() {
                match line.uninject() {
                    Ok(justkey) => p.pools.just_map.get(&justkey).unwrap().display_indented(fmt, indent, linecount)?,
                    Err(line) => aux(p, fmt, indent + 1, linecount, p.pools.sub_map.get(&line.uninject::<SubKey, _>().unwrap()).unwrap())?,
                }
            }
            Ok(())
        }
        aux(&self, fmt, indent, linecount, &self.proof)
    }
}

impl<Tail> std::fmt::Display for PooledProof<HCons<Expr, Tail>> {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        self.display_indented(f, 1, &mut 1)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    use crate::rules::RuleM;

    use frunk_core::Hlist;

    #[test]
    fn test_pooledproof_mutating_lines() {
        use crate::parser::parse_unwrap as p;
        let mut prf = PooledProof::<Hlist![Expr]>::new();
        let r1 = prf.add_premise(p("A"));
        let r2 = prf.add_step(Justification(p("A & A"), RuleM::AndIntro, vec![Coproduct::inject(r1.clone())], vec![]));
        println!("{}", prf);
        prf.with_mut_premise(&r1, |e| {
            *e = p("B");
        })
        .unwrap();
        prf.with_mut_step(&r2, |j| {
            j.0 = p("A | B");
            j.1 = RuleM::OrIntro;
        })
        .unwrap();
        println!("{}", prf);
    }

    #[test]
    fn prettyprint_pool() {
        let prf: PooledProof<Hlist![Expr]> = crate::proofs::proof_tests::demo_proof_1();
        println!("{:?}\n{}\n", prf, prf);
        println!("{:?}\n{:?}\n", prf.premises(), prf.lines());
    }
}
