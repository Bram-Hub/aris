use super::*;
use frunk::coproduct::*;
use std::collections::HashSet;
use std::fmt::{Display, Formatter};
use std::hash::Hash;
use std::ops::Range;

#[cfg(test)]
mod proof_tests;
/// This module houses different proof representations with different performance/simplicity tradeoffs
/// The intent is that once experimentation is done data-structure-wise, these can be packaged up as java objects

/// treeproof represents a proof as a vec of premises and a vec of non-premise lines with either rule applications (with explicit line numbers for dependencies) or nested subproofs, with hooks for annotations
/// Pros:
/// - rules out invalid nesting
/// - close to the presentation of the system in the textbook
/// Cons:
/// - many operations of interest {adding and deleting lines, recomputing line numbers for dependencies} are O(n)
pub mod treeproof;
#[cfg(test)] mod treeproof_tests;

/// pooledproof represents proofs as ZipperVec's of indices into three seperate pools of {premises, justifications, subproofs}
/// Pros:
/// - allows approximate O(1) inserts/removals of nearby lines (gracefully degrading to O(n) if edits are made at opposite ends of the proof)
/// - references to pool entries are never invalidated, which means that moving lines around can be efficiently supported
/// - looking up steps by line number should be O(1), which enables efficiently checking individual rules, elimintating the need for a check-cache
///     (if I understand correctly, the current java version caches rule checks, which might lead to soundness bugs if the cache isn't invalidated correctly)
/// Cons:
/// - potentially more implementation effort than treeproof
/// - nontrivial mapping between references and line numbers might require some additional design
/// Mixed:
/// - splicing in a subproof is O(|subproof|), while in treeproof that's O(1), but forces an O(|wholeproof|) line recalculation
pub mod pooledproof;

/// java_shallow_proof only represents things from edu.rpi.aris.rules.Premise, for the purpose of shimming into RuleT::check
pub mod java_shallow_proof;

/// A LinedProof is a wrapper around another proof type that adds lines and strings, for interfacing with the GUI
pub mod lined_proof;

/// DisplayIndented gives a convention for passing around state to pretty printers
/// it is intended that objects that implement this implement display as:
/// `fn fmt(&self, fmt: &mut Formatter) -> std::result::Result<(), std::fmt::Error> { self.display_indented(fmt, 1, &mut 1) }`
/// but this cannot be given as a blanket implementation due to coherence rules
pub trait DisplayIndented {
    fn display_indented(&self, fmt: &mut Formatter, indent: usize, linecount: &mut usize) -> Result<(), std::fmt::Error>;
}

pub trait Proof: Sized {
    type Reference: Clone + Eq + Hash;
    type SubproofReference: Clone + Eq + Hash;
    type Subproof: Proof<Reference=Self::Reference, SubproofReference=Self::SubproofReference, Subproof=Self::Subproof>;
    fn new() -> Self;
    fn top_level_proof(&self) -> &Self::Subproof;
    fn lookup(&self, r: Self::Reference) -> Option<Coprod!(Expr, Justification<Expr, Self::Reference, Self::SubproofReference>)>;
    fn lookup_subproof(&self, r: Self::SubproofReference) -> Option<Self::Subproof>;
    fn with_mut_subproof<A, F: FnOnce(&mut Self::Subproof) -> A>(&mut self, r: &Self::SubproofReference, f: F) -> Option<A>;
    fn add_premise(&mut self, e: Expr) -> Self::Reference;
    fn add_subproof(&mut self) -> Self::SubproofReference;
    fn add_step(&mut self, just: Justification<Expr, Self::Reference, Self::SubproofReference>) -> Self::Reference;
    fn add_premise_relative(&mut self, e: Expr, r: Self::Reference, after: bool) -> Self::Reference;
    fn add_subproof_relative(&mut self, r: Self::Reference, after: bool) -> Self::SubproofReference;
    fn add_step_relative(&mut self, just: Justification<Expr, Self::Reference, Self::SubproofReference>, r: Self::Reference, after: bool) -> Self::Reference;
    fn remove_line(&mut self, r: Self::Reference);
    fn remove_subproof(&mut self, r: Self::SubproofReference);
    fn premises(&self) -> Vec<Self::Reference>;
    fn lines(&self) -> Vec<Coprod!(Self::Reference, Self::SubproofReference)>;
    fn parent_of_line(&self, r: &Coprod!(Self::Reference, Self::SubproofReference)) -> Option<Self::SubproofReference>;
    fn verify_line(&self, r: &Self::Reference) -> Result<(), ProofCheckError<Self::Reference, Self::SubproofReference>>;

    fn lookup_expr(&self, r: Self::Reference) -> Option<Expr> {
        self.lookup(r).and_then(|x: Coprod!(Expr, Justification<Expr, Self::Reference, Self::SubproofReference>)| x.fold(hlist![|x| Some(x), |x: Justification<_, _, _>| Some(x.0)]))
    }
    fn lookup_expr_or_die(&self, r: Self::Reference) -> Result<Expr, ProofCheckError<Self::Reference, Self::SubproofReference>> {
        self.lookup_expr(r.clone()).ok_or(ProofCheckError::LineDoesNotExist(r))
    }
    fn lookup_subproof_or_die(&self, r: Self::SubproofReference) -> Result<Self::Subproof, ProofCheckError<Self::Reference, Self::SubproofReference>> {
        self.lookup_subproof(r.clone()).ok_or(ProofCheckError::SubproofDoesNotExist(r))
    }
    fn exprs(&self) -> Vec<<Self as Proof>::Reference> {
        self.premises().iter().cloned().chain(self.lines().iter().filter_map(|x| Coproduct::uninject::<<Self as Proof>::Reference, _>(x.clone()).ok())).collect()
    }
    fn contained_justifications(&self) -> HashSet<Self::Reference> {
        self.lines().into_iter().filter_map(|x| x.fold(hlist![
            |r: Self::Reference| Some(vec![r].into_iter().collect()),
            |r: Self::SubproofReference| self.lookup_subproof(r).map(|sub| sub.contained_justifications()),
        ])).fold(HashSet::new(), |mut x, y| { x.extend(y.into_iter()); x })
    }
    fn transitive_dependencies(&self, line: Self::Reference) -> HashSet<Self::Reference> {
        // TODO: cycle detection
        let mut stack: Vec<Coprod!(Self::Reference, Self::SubproofReference)> = vec![Coproduct::inject(line)];
        let mut result = HashSet::new();
        while let Some(r) = stack.pop() {
            use frunk::Coproduct::{Inl, Inr};
            match r {
                Inl(r) => {
                    result.insert(r.clone());
                    if let Some(Justification(_, _, deps, sdeps)) = self.lookup(r).and_then(|x| Coproduct::uninject(x).ok()) {
                        stack.extend(deps.into_iter().map(|x| Coproduct::inject(x)));
                        stack.extend(sdeps.into_iter().map(|x| Coproduct::inject(x)));
                    }
                },
                Inr(Inl(r)) => {
                    if let Some(sub) = self.lookup_subproof(r) {
                        stack.extend(sub.lines());
                    }
                },
                Inr(Inr(void)) => match void {},
            }
        }
        result
    }
    fn depth_of_line(&self, r: &Coprod!(Self::Reference, Self::SubproofReference)) -> usize {
        let mut result = 0;
        let mut current = r.clone();
        while let Some(parent) = self.parent_of_line(&current) {
            result += 1;
            current = Coproduct::inject(parent);
        }
        result
    }
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Justification<T, R, S>(T, Rule, Vec<R>, Vec<S>);

impl<T, R, S> Justification<T, R, S> {
    fn map0<U, F: FnOnce(T) -> U>(self, f: F) -> Justification<U, R, S> {
        Justification(f(self.0), self.1, self.2, self.3)
    }
}

pub trait JustificationExprDisplay { fn fmt_expr(&self, fmt: &mut Formatter) -> std::fmt::Result; }
impl JustificationExprDisplay for Expr { fn fmt_expr(&self, fmt: &mut Formatter) -> std::fmt::Result { write!(fmt, "{}", self) } }
impl<Tail> JustificationExprDisplay for frunk::HCons<Expr, Tail> { fn fmt_expr(&self, fmt: &mut Formatter) -> std::fmt::Result { write!(fmt, "{}", self.head) } }

impl<T: JustificationExprDisplay, R: std::fmt::Debug, S: std::fmt::Debug> DisplayIndented for Justification<T, R, S> {
    fn display_indented(&self, fmt: &mut Formatter, indent: usize, linecount: &mut usize) -> std::result::Result<(), std::fmt::Error> {
        let &Justification(expr, rule, deps, sdeps) = &self;
        write!(fmt, "{}:\t", linecount)?;
        *linecount += 1;
        for _ in 0..indent { write!(fmt, "| ")?; }
        expr.fmt_expr(fmt)?;
        write!(fmt, "; {:?}; ", rule)?;
        for (i, dep) in deps.iter().enumerate() {
            write!(fmt, "{:?}", dep)?;
            if i != deps.len()-1 { write!(fmt, ", ")?; }
        }
        for (i, dep) in sdeps.iter().enumerate() {
            write!(fmt, "{:?}", dep)?;
            if i != sdeps.len()-1 { write!(fmt, ", ")?; }
        }
        write!(fmt, "\n")
    }
}


#[derive(Clone, Debug, PartialEq, Eq)]
pub struct LineAndIndent { pub line: usize, pub indent: usize }

#[test]
fn test_xml() {
    use xml::reader::EventReader;
    let data = &include_bytes!("../propositional_logic_arguments_for_proofs_ii_problem_10.bram")[..];
    let mut er = EventReader::new(data);

    let mut author = None;
    let mut hash = None;

    let mut element_stack = vec![];
    let mut attribute_stack = vec![];
    let mut contents = String::new();

    type P = super::proofs::pooledproof::PooledProof<Hlist![Expr]>;
    let parse = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    use std::collections::HashMap;
    let mut subproofs: HashMap<_, <P as Proof>::SubproofReference> = HashMap::new();
    let mut lines_to_subs = HashMap::new();
    let mut line_refs: HashMap<_, <P as Proof>::Reference> = HashMap::new();
    let mut last_linenum = "".into();
    let mut proof = P::new();
    let mut current_proof_id = "0".into();
    let mut last_raw = "".into();

    let mut last_rule = "".into();
    let mut seen_premises = vec![];

    loop {
        use xml::reader::XmlEvent::{self, *};
        match er.next() {
            Ok(StartElement { name, attributes, namespace: _ }) => {
                let element = name.local_name;
                element_stack.push(element.clone());
                attribute_stack.push(attributes.clone());
                contents = String::new();
                match &*element {
                    "proof" => {
                        let id = attributes.iter().find(|x| x.name.local_name == "id").expect("proof element has no id attribute");
                        current_proof_id = id.value.clone();
                    },
                    "assumption" => {
                        let linenum = attributes.iter().find(|x| x.name.local_name == "linenum").expect("assumption element has no linenum attribute");
                        last_linenum = linenum.value.clone();
                    },
                    "step" => {
                        let linenum = attributes.iter().find(|x| x.name.local_name == "linenum").expect("step element has no linenum attribute");
                        last_linenum = linenum.value.clone();
                        last_rule = "".into();
                        seen_premises = vec![];
                    }
                    _ => (),
                }
            },
            Ok(Characters(data)) => { contents += &data; },
            Ok(EndElement { name }) => {
                println!("end {:?} {:?}", element_stack, contents);
                let element = element_stack.pop().unwrap();
                assert_eq!(name.local_name, element);
                let attributes = attribute_stack.pop().unwrap();
                println!("{:?} {:?}", element, attributes);
                match &*element {
                    "author" => { author = Some(contents.clone()) },
                    "hash" => { hash = Some(contents.clone()) },
                    "raw" => { last_raw = contents.clone(); },
                    "assumption" => {
                        match &*current_proof_id {
                            "0" => { let p = proof.add_premise(parse(&last_raw)); line_refs.insert(last_linenum.clone(), p); },
                            r => { let key = subproofs[r].clone(); proof.with_mut_subproof(&key, |sub| { let p = sub.add_premise(parse(&last_raw)); line_refs.insert(last_linenum.clone(), p); }); },
                        }
                    },
                    "rule" => { last_rule = contents.clone(); },
                    "premise" => { seen_premises.push(contents.clone()); },
                    "step" => {
                        println!("step {:?} {:?}", last_rule, seen_premises);
                        match &*last_rule {
                            "SUBPROOF" => {
                                match &*current_proof_id {
                                    "0" => { let p = proof.add_subproof(); subproofs.insert(seen_premises[0].clone(), p); lines_to_subs.insert(last_linenum.clone(), p); },
                                    r => { let key = subproofs[r].clone(); proof.with_mut_subproof(&key, |sub| { let p = sub.add_subproof(); subproofs.insert(seen_premises[0].clone(), p); lines_to_subs.insert(last_linenum.clone(), p); }); },
                                }
                            }
                            rulename => {
                                let rule = RuleM::from_serialized_name(rulename).unwrap();
                                println!("{:?}", rule);
                                let deps = seen_premises.iter().filter_map(|x| line_refs.get(x)).cloned().collect::<Vec<_>>();
                                let sdeps = seen_premises.iter().filter_map(|x| lines_to_subs.get(x)).cloned().collect::<Vec<_>>();
                                println!("{:?} {:?}", line_refs, subproofs);
                                println!("{:?} {:?}", deps, sdeps);
                                let just = Justification(parse(&last_raw), rule, deps, sdeps);
                                println!("{:?}", just);
                                match &*current_proof_id {
                                    "0" => { let p = proof.add_step(just); line_refs.insert(last_linenum.clone(), p); },
                                    r => { let key = subproofs[r].clone(); proof.with_mut_subproof(&key, |sub| { let p = sub.add_step(just); line_refs.insert(last_linenum.clone(), p); }); },
                                }
                                
                            },
                        }
                    },
                    _ => (),
                }
            },
            Ok(Whitespace(_)) => (),
            Ok(EndDocument) => break,
            e => { println!("{:?}", e); },
        }
    }
    println!("{:?} {:?}", author, hash);
    println!("{}\n-----", proof);
    println!("{:?}", subproofs);
}
