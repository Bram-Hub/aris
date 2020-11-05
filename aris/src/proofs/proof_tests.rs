#![deny(unused_variables, dead_code)]

use crate::expr::Expr;
use crate::proofs::pooledproof::PooledProof;
use crate::proofs::Justification;
use crate::proofs::PJRef;
use crate::proofs::Proof;
use crate::rules::RuleM;

use std::collections::HashSet;
use std::fmt::Debug;
use std::fmt::Display;

use frunk_core::coproduct::CoprodInjector;
use frunk_core::coproduct::Coproduct;
use frunk_core::Hlist;

fn coproduct_inject<T, Index, Head, Tail>(to_insert: T) -> Coproduct<Head, Tail>
where
    Coproduct<Head, Tail>: CoprodInjector<T, Index>,
{
    Coproduct::inject(to_insert)
}

fn run_test<P: Proof + Display + Debug, F: FnOnce() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>)>(f: F)
where
    PJRef<P>: Debug,
    P::SubproofReference: Debug,
{
    let (prf, oks, errs) = f();
    println!("{}", prf);
    println!("{:?}", prf);
    for (i, ok) in oks.iter().enumerate() {
        if let Err(e) = prf.verify_line(&ok) {
            panic!("run_test: unexpected error on line {}: {:?}", i, e);
        }
    }
    for (i, err) in errs.iter().enumerate() {
        if let Err(e) = prf.verify_line(&err) {
            println!("Error message for {} was {:?}", i, e);
        } else {
            panic!(
                "{} ({:?}, {:?}) should have failed, but didn't",
                i,
                err,
                prf.lookup_pj(&err.clone())
            );
        }
    }
}

macro_rules! generate_tests {
    ($proofrepr:ty, $modprefix:ident; $( $generic_test:ident ),+,) => {
        #[cfg(test)]
        mod $modprefix {
            use super::*;
            $(
                #[test]
                fn $generic_test() {
                    super::run_test::<$proofrepr, _>(super::$generic_test);
                }
            )+
        }
    }
}

macro_rules! enumerate_subproofless_tests {
    ($x:ty, $y:ident) => {
        generate_tests! { $x, $y;
            test_andelim, test_contelim, test_orintro, test_reit, test_andintro,
            test_contradictionintro, test_notelim, test_impelim, test_commutation,
            test_association, test_demorgan, test_idempotence, test_doublenegation,
            test_distribution, test_complement, test_identity, test_annihilation,
            test_inverse, test_absorption, test_reduction, test_adjacency, test_resolution,
            test_tautcon, test_empty_rule, test_modus_tollens, test_hypothetical_syllogism,
            test_disjunctive_syllogism, test_constructive_dilemma, test_excluded_middle,
        }
    };
}

macro_rules! enumerate_subproofful_tests {
    ($x:ty, $y:ident) => {
        generate_tests! { $x, $y;
            test_forallintro,
            test_forallelim,
            test_biconelim,
            test_biconintro,
            test_impintro,
            test_notintro,
            test_orelim,
            test_equivelim,
            test_equivintro,
            test_existsintro,
            test_existselim,
        }
    };
}

enumerate_subproofless_tests! { PooledProof<Hlist![Expr]>, test_subproofless_rules_on_pooledproof }
enumerate_subproofful_tests! { PooledProof<Hlist![Expr]>, test_subproofful_rules_on_pooledproof }

pub fn demo_proof_1<P: Proof>() -> P
where
    P: PartialEq + std::fmt::Debug,
    PJRef<P>: PartialEq + std::fmt::Debug,
    P::SubproofReference: PartialEq + std::fmt::Debug,
{
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A"));
    let r2 = prf.add_premise(p("B"));
    let r3 = prf.add_step(Justification(
        p("A & B"),
        RuleM::AndIntro,
        vec![i(r1.clone()), i(r2.clone())],
        vec![],
    ));
    let r4 = prf.add_subproof();
    prf.with_mut_subproof(&r4, |sub| {
        sub.add_premise(p("C"));
        sub.add_step(Justification(
            p("A & B"),
            RuleM::Reit,
            vec![i(r3.clone())],
            vec![],
        ));
    });
    let r5 = prf.add_step(Justification(
        p("C -> (A & B)"),
        RuleM::ImpIntro,
        vec![],
        vec![r4.clone()],
    ));
    assert_eq!(prf.lookup_premise(&r1), Some(p("A")));
    assert_eq!(prf.lookup_premise(&r2), Some(p("B")));
    assert_eq!(
        prf.lookup_step(&r3),
        Some(Justification(
            p("A&B"),
            RuleM::AndIntro,
            vec![i(r1.clone()), i(r2.clone())],
            vec![]
        ))
    );
    if let Some(sub) = prf.lookup_subproof(&r4) {
        let _: P::Subproof = sub;
        println!("lookup4 good");
    } else {
        println!("lookup4 bad");
    }
    assert_eq!(
        prf.lookup_step(&r5),
        Some(Justification(
            p("C->(A&B)"),
            RuleM::ImpIntro,
            vec![],
            vec![r4.clone()]
        ))
    );
    prf
}

pub fn test_andelim<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A & B & C & D"));
    let r2 = prf.add_premise(p("E | F"));
    let r3 = prf.add_step(Justification(
        p("A"),
        RuleM::AndElim,
        vec![i(r1.clone())],
        vec![],
    ));
    let r4 = prf.add_step(Justification(
        p("E"),
        RuleM::AndElim,
        vec![i(r1.clone())],
        vec![],
    ));
    let r5 = prf.add_step(Justification(
        p("A"),
        RuleM::AndElim,
        vec![i(r1.clone()), i(r1.clone())],
        vec![],
    ));
    let r6 = prf.add_step(Justification(
        p("A"),
        RuleM::AndElim,
        vec![i(r2.clone())],
        vec![],
    ));
    (prf, vec![i(r3)], vec![i(r4), i(r5), i(r6)])
}

pub fn test_contelim<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let r1 = prf.add_premise(p("_|_"));
    let r2 = prf.add_premise(p("A & B"));
    let r3 = prf.add_step(Justification(
        p("forall x, x & ~ x"),
        RuleM::ContradictionElim,
        vec![i(r1.clone())],
        vec![],
    ));
    let r4 = prf.add_step(Justification(
        p("Q"),
        RuleM::ContradictionElim,
        vec![i(r2.clone())],
        vec![],
    ));
    (prf, vec![i(r3)], vec![i(r4)])
}

pub fn test_orintro<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A"));
    let r2 = prf.add_step(Justification(
        p("A | B | C"),
        RuleM::OrIntro,
        vec![i(r1.clone())],
        vec![],
    ));
    let r3 = prf.add_step(Justification(
        p("P | Q"),
        RuleM::OrIntro,
        vec![i(r1.clone())],
        vec![],
    ));
    let r4 = prf.add_step(Justification(
        p("P & Q"),
        RuleM::OrIntro,
        vec![i(r1.clone())],
        vec![],
    ));
    (prf, vec![i(r2)], vec![i(r3), i(r4)])
}

pub fn test_reit<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A"));
    let r2 = prf.add_step(Justification(
        p("A"),
        RuleM::Reit,
        vec![i(r1.clone())],
        vec![],
    ));
    let r3 = prf.add_step(Justification(
        p("B"),
        RuleM::Reit,
        vec![i(r1.clone())],
        vec![],
    ));
    (prf, vec![i(r2)], vec![i(r3)])
}

pub fn test_andintro<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A"));
    let r2 = prf.add_premise(p("B"));
    let r3 = prf.add_premise(p("C"));
    let r4 = prf.add_step(Justification(
        p("A & B"),
        RuleM::AndIntro,
        vec![i(r1.clone()), i(r2.clone())],
        vec![],
    ));
    let r5 = prf.add_step(Justification(
        p("A & B"),
        RuleM::AndIntro,
        vec![i(r1.clone()), i(r2.clone()), i(r3.clone())],
        vec![],
    ));
    let r6 = prf.add_step(Justification(
        p("A & B"),
        RuleM::AndIntro,
        vec![i(r1.clone())],
        vec![],
    ));
    let r7 = prf.add_step(Justification(
        p("A & A"),
        RuleM::AndIntro,
        vec![i(r1.clone())],
        vec![],
    ));
    (prf, vec![i(r4), i(r7)], vec![i(r5), i(r6)])
}

pub fn test_contradictionintro<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A"));
    let r2 = prf.add_premise(p("~A"));
    let r3 = prf.add_premise(p("~~A"));
    let r4 = prf.add_premise(p("B"));
    let r5 = prf.add_step(Justification(
        p("_|_"),
        RuleM::ContradictionIntro,
        vec![i(r1.clone()), i(r2.clone())],
        vec![],
    ));
    let r6 = prf.add_step(Justification(
        p("_|_"),
        RuleM::ContradictionIntro,
        vec![i(r2.clone()), i(r3.clone())],
        vec![],
    ));
    let r7 = prf.add_step(Justification(
        p("_|_"),
        RuleM::ContradictionIntro,
        vec![i(r1.clone()), i(r3.clone())],
        vec![],
    ));
    let r8 = prf.add_step(Justification(
        p("_|_"),
        RuleM::ContradictionIntro,
        vec![i(r1.clone()), i(r4.clone())],
        vec![],
    ));
    let r9 = prf.add_step(Justification(
        p("Q(E,D)"),
        RuleM::ContradictionIntro,
        vec![i(r1.clone()), i(r2.clone())],
        vec![],
    ));
    let r10 = prf.add_step(Justification(
        p("_|_"),
        RuleM::ContradictionIntro,
        vec![i(r2.clone()), i(r1.clone())],
        vec![],
    ));
    (prf, vec![i(r5), i(r6), i(r10)], vec![i(r7), i(r8), i(r9)])
}

pub fn test_notelim<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let r1 = prf.add_premise(p("~~A"));
    let r2 = prf.add_premise(p("~~(A & B)"));
    let r3 = prf.add_premise(p("~A"));
    let r4 = prf.add_premise(p("A"));
    let r5 = prf.add_step(Justification(
        p("A"),
        RuleM::NotElim,
        vec![i(r1.clone())],
        vec![],
    )); // 5
    let r6 = prf.add_step(Justification(
        p("A & B"),
        RuleM::NotElim,
        vec![i(r2.clone())],
        vec![],
    ));
    let r7 = prf.add_step(Justification(
        p("A"),
        RuleM::NotElim,
        vec![i(r3.clone())],
        vec![],
    ));
    let r8 = prf.add_step(Justification(
        p("A"),
        RuleM::NotElim,
        vec![i(r4.clone())],
        vec![],
    ));
    let r9 = prf.add_step(Justification(
        p("B"),
        RuleM::NotElim,
        vec![i(r2.clone())],
        vec![],
    ));
    (prf, vec![i(r5), i(r6)], vec![i(r7), i(r8), i(r9)])
}

pub fn test_impelim<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let r1 = prf.add_premise(p("P"));
    let r2 = prf.add_premise(p("P -> Q"));
    let r3 = prf.add_premise(p("Q"));
    let r4 = prf.add_premise(p("A"));
    let r5 = prf.add_premise(p("A -> A"));
    let r6 = prf.add_step(Justification(
        p("Q"),
        RuleM::ImpElim,
        vec![i(r1.clone()), i(r2.clone())],
        vec![],
    ));
    let r7 = prf.add_step(Justification(
        p("Q"),
        RuleM::ImpElim,
        vec![i(r2.clone()), i(r1.clone())],
        vec![],
    ));
    let r8 = prf.add_step(Justification(
        p("B"),
        RuleM::ImpElim,
        vec![i(r1.clone()), i(r2.clone())],
        vec![],
    ));
    let r9 = prf.add_step(Justification(
        p("P"),
        RuleM::ImpElim,
        vec![i(r3.clone()), i(r2.clone())],
        vec![],
    ));
    let r10 = prf.add_step(Justification(
        p("P"),
        RuleM::ImpElim,
        vec![i(r2.clone()), i(r3.clone())],
        vec![],
    ));
    let r11 = prf.add_step(Justification(
        p("B"),
        RuleM::ImpElim,
        vec![i(r3.clone()), i(r4.clone())],
        vec![],
    ));
    let r12 = prf.add_step(Justification(
        p("Q"),
        RuleM::ImpElim,
        vec![i(r2.clone()), i(r4.clone())],
        vec![],
    ));
    let r13 = prf.add_step(Justification(
        p("A"),
        RuleM::ImpElim,
        vec![i(r4.clone()), i(r5.clone())],
        vec![],
    ));
    let r14 = prf.add_step(Justification(
        p("Q"),
        RuleM::ImpElim,
        vec![i(r2.clone()), i(r1.clone())],
        vec![],
    ));
    (
        prf,
        vec![i(r6), i(r7), i(r13), i(r14)],
        vec![i(r8), i(r9), i(r10), i(r11), i(r12)],
    )
}

pub fn test_biconelim<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A <-> B <-> C"));
    let r2 = prf.add_premise(p("A"));
    let r3 = prf.add_step(Justification(
        p("B <-> C"),
        RuleM::BiconditionalElim,
        vec![i(r1.clone()), i(r2.clone())],
        vec![],
    ));
    let r4 = prf.add_step(Justification(
        p("C <-> B"),
        RuleM::BiconditionalElim,
        vec![i(r1.clone()), i(r2.clone())],
        vec![],
    ));
    let r5 = prf.add_step(Justification(
        p("D <-> B"),
        RuleM::BiconditionalElim,
        vec![i(r1.clone()), i(r2.clone())],
        vec![],
    ));
    let r8 = prf.add_subproof();
    let (_r6, r7) = prf
        .with_mut_subproof(&r8, |sub| {
            let r6 = sub.add_premise(p("D"));
            let r7 = sub.add_step(Justification(
                p("A <-> B"),
                RuleM::BiconditionalElim,
                vec![i(r1.clone()), i(r6.clone())],
                vec![],
            ));
            (r6, r7)
        })
        .unwrap();
    let r9 = prf.add_premise(p("A <-> B"));
    let r10 = prf.add_step(Justification(
        p("B"),
        RuleM::BiconditionalElim,
        vec![i(r1.clone()), i(r2.clone())],
        vec![],
    ));
    let r11 = prf.add_step(Justification(
        p("B"),
        RuleM::BiconditionalElim,
        vec![i(r9.clone()), i(r2.clone())],
        vec![],
    ));
    let r12 = prf.add_premise(p("A <-> B <-> C <-> D"));
    let r13 = prf.add_step(Justification(
        p("A <-> C <-> D"),
        RuleM::BiconditionalElim,
        vec![i(r10.clone()), i(r12.clone())],
        vec![],
    ));
    let r14 = prf.add_step(Justification(
        p("C"),
        RuleM::BiconditionalElim,
        vec![i(r1.clone()), i(r9.clone())],
        vec![],
    ));
    let r15 = prf.add_step(Justification(
        p("B <-> C"),
        RuleM::BiconditionalElim,
        vec![i(r2.clone()), i(r1.clone())],
        vec![],
    ));
    static BICON_COMMUTATIVITY: bool = false;
    if BICON_COMMUTATIVITY {
        (
            prf,
            vec![i(r3), i(r4), i(r11), i(r13), i(r15)],
            vec![i(r5), i(r7), i(r10)],
        )
    } else {
        (
            prf,
            vec![i(r3), i(r11), i(r13), i(r14), i(r15)],
            vec![i(r4), i(r5), i(r7), i(r10)],
        )
    }
}

pub fn test_impintro<P: Proof + Debug>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>)
where
    P::Subproof: Debug,
{
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A"));
    let r2 = prf.add_premise(p("B"));
    let r5 = prf.add_subproof();
    let r4 = prf
        .with_mut_subproof(&r5, |sub1| {
            let _r3 = sub1.add_premise(p("A"));
            let r4 = sub1.add_step(Justification(
                p("B"),
                RuleM::Reit,
                vec![i(r2.clone())],
                vec![],
            ));
            println!("{:?}", sub1);
            r4
        })
        .unwrap();
    let r8 = prf.add_subproof();
    let r7 = prf
        .with_mut_subproof(&r8, |sub2| {
            let _r6 = sub2.add_premise(p("A"));
            let r7 = sub2.add_step(Justification(
                p("A"),
                RuleM::Reit,
                vec![i(r1.clone())],
                vec![],
            ));
            println!("{:?}", sub2);
            r7
        })
        .unwrap();
    let r9 = prf.add_step(Justification(
        p("A -> B"),
        RuleM::ImpIntro,
        vec![],
        vec![r5.clone()],
    ));
    let r10 = prf.add_step(Justification(
        p("A -> A"),
        RuleM::ImpIntro,
        vec![],
        vec![r8.clone()],
    ));
    let r11 = prf.add_step(Justification(
        p("B -> A"),
        RuleM::ImpIntro,
        vec![],
        vec![r5.clone()],
    ));
    (prf, vec![i(r4), i(r7), i(r9), i(r10)], vec![i(r11)])
}

pub fn test_notintro<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A -> _|_"));
    let r4 = prf.add_subproof();
    prf.with_mut_subproof(&r4, |sub1| {
        let r2 = sub1.add_premise(p("A"));
        let _r3 = sub1.add_step(Justification(
            p("_|_"),
            RuleM::ImpElim,
            vec![i(r1.clone()), i(r2.clone())],
            vec![],
        ));
    });
    let r5 = prf.add_step(Justification(
        p("~A"),
        RuleM::NotIntro,
        vec![],
        vec![r4.clone()],
    ));
    let r6 = prf.add_step(Justification(
        p("~B"),
        RuleM::NotIntro,
        vec![],
        vec![r4.clone()],
    ));
    (prf, vec![i(r5)], vec![i(r6)])
}

pub fn test_orelim<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A | B"));
    let r4 = prf.add_subproof();
    prf.with_mut_subproof(&r4, |sub1| {
        let _r2 = sub1.add_premise(p("A"));
        let _r3 = sub1.add_step(Justification(p("C"), RuleM::Reit, vec![], vec![]));
    });
    let r7 = prf.add_subproof();
    prf.with_mut_subproof(&r7, |sub2| {
        let _r5 = sub2.add_premise(p("B"));
        let _r6 = sub2.add_step(Justification(p("C"), RuleM::Reit, vec![], vec![]));
    });
    let r10 = prf.add_subproof();
    prf.with_mut_subproof(&r10, |sub3| {
        let _r8 = sub3.add_premise(p("B"));
        let _r9 = sub3.add_step(Justification(p("D"), RuleM::Reit, vec![], vec![]));
    });
    let r11 = prf.add_step(Justification(
        p("C"),
        RuleM::OrElim,
        vec![i(r1.clone())],
        vec![r4.clone(), r7.clone()],
    ));
    let r12 = prf.add_step(Justification(
        p("D"),
        RuleM::OrElim,
        vec![i(r1.clone())],
        vec![r4.clone(), r7.clone()],
    ));
    let r13 = prf.add_step(Justification(
        p("C"),
        RuleM::OrElim,
        vec![i(r1.clone())],
        vec![r4.clone(), r10.clone()],
    ));
    (prf, vec![i(r11)], vec![i(r12), i(r13)])
}

pub fn test_biconintro<P: Proof + Debug>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>)
where
    P::Subproof: Debug,
{
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let r1 = prf.add_premise(p("P -> Q"));
    let r2 = prf.add_premise(p("Q -> R"));
    let r3 = prf.add_premise(p("Q -> P"));
    let r4 = prf.add_premise(p("R -> Q"));
    let r5 = prf.add_premise(p("R -> P"));
    let r6 = prf.add_premise(p("A -> A"));
    let r7 = prf.add_step(Justification(
        p("A <-> A"),
        RuleM::BiconditionalIntro,
        vec![i(r6.clone())],
        vec![],
    ));
    let r8 = prf.add_step(Justification(
        p("P <-> Q <-> R"),
        RuleM::BiconditionalIntro,
        vec![i(r1.clone()), i(r2.clone()), i(r3.clone()), i(r4.clone())],
        vec![],
    ));
    let r9 = prf.add_step(Justification(
        p("P <-> Q <-> R"),
        RuleM::BiconditionalIntro,
        vec![i(r1.clone()), i(r2.clone()), i(r5.clone())],
        vec![],
    ));
    let r10 = prf.add_subproof();
    prf.with_mut_subproof(&r10, |sub1| {
        sub1.add_premise(p("B"));
    });
    let r11 = prf.add_step(Justification(
        p("B <-> B"),
        RuleM::BiconditionalIntro,
        vec![],
        vec![r10.clone()],
    ));
    let r12 = prf.add_step(Justification(
        p("P <-> Q <-> R <-> S"),
        RuleM::BiconditionalIntro,
        vec![i(r1.clone()), i(r2.clone()), i(r3.clone()), i(r4.clone())],
        vec![],
    ));
    let r13 = prf.add_step(Justification(
        p("P <-> Q <-> R <-> S"),
        RuleM::BiconditionalIntro,
        vec![i(r1.clone()), i(r2.clone()), i(r5.clone())],
        vec![],
    ));
    let r14 = prf.add_subproof();
    prf.with_mut_subproof(&r14, |sub2| {
        sub2.add_premise(p("A"));
        sub2.add_step(Justification(p("B"), RuleM::Reit, vec![], vec![]));
        sub2.add_step(Justification(p("C"), RuleM::Reit, vec![], vec![]));
    });
    let r15 = prf.add_subproof();
    prf.with_mut_subproof(&r15, |sub2| {
        sub2.add_premise(p("B"));
        sub2.add_step(Justification(p("A"), RuleM::Reit, vec![], vec![]));
        sub2.add_step(Justification(p("C"), RuleM::Reit, vec![], vec![]));
    });
    let r16 = prf.add_step(Justification(
        p("A <-> B"),
        RuleM::BiconditionalIntro,
        vec![],
        vec![r14.clone(), r15.clone()],
    ));
    let r17 = prf.add_step(Justification(
        p("A <-> C"),
        RuleM::BiconditionalIntro,
        vec![],
        vec![r14.clone(), r15.clone()],
    ));
    let r18 = prf.add_subproof();
    prf.with_mut_subproof(&r18, |sub2| {
        sub2.add_premise(p("P"));
        sub2.add_step(Justification(p("Q"), RuleM::Reit, vec![], vec![]));
    });
    let r19 = prf.add_step(Justification(
        p("P <-> Q"),
        RuleM::BiconditionalIntro,
        vec![i(r3.clone())],
        vec![r18.clone()],
    ));
    (
        prf,
        vec![i(r7), i(r11), i(r16), i(r19)],
        vec![i(r8), i(r9), i(r12), i(r13), i(r17)],
    )
}

pub fn test_equivintro<P: Proof + Debug>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>)
where
    P::Subproof: Debug,
{
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let r1 = prf.add_premise(p("P -> Q"));
    let r2 = prf.add_premise(p("Q -> R"));
    let r3 = prf.add_premise(p("Q -> P"));
    let r4 = prf.add_premise(p("R -> Q"));
    let r5 = prf.add_premise(p("R -> P"));
    let r6 = prf.add_premise(p("A -> A"));
    let r7 = prf.add_step(Justification(
        p("A === A === A === A === A"),
        RuleM::EquivalenceIntro,
        vec![i(r6.clone())],
        vec![],
    ));
    let r8 = prf.add_step(Justification(
        p("P === Q === R"),
        RuleM::EquivalenceIntro,
        vec![i(r1.clone()), i(r2.clone()), i(r3.clone()), i(r4.clone())],
        vec![],
    ));
    let r9 = prf.add_step(Justification(
        p("P === Q === R"),
        RuleM::EquivalenceIntro,
        vec![i(r1.clone()), i(r2.clone()), i(r5.clone())],
        vec![],
    ));
    let r10 = prf.add_subproof();
    prf.with_mut_subproof(&r10, |sub1| {
        sub1.add_premise(p("B"));
    });
    let r11 = prf.add_step(Justification(
        p("B === B === B"),
        RuleM::EquivalenceIntro,
        vec![],
        vec![r10.clone()],
    ));
    let r12 = prf.add_step(Justification(
        p("P === Q === R === S"),
        RuleM::EquivalenceIntro,
        vec![i(r1.clone()), i(r2.clone()), i(r3.clone()), i(r4.clone())],
        vec![],
    ));
    let r13 = prf.add_step(Justification(
        p("P === Q === R === S"),
        RuleM::EquivalenceIntro,
        vec![i(r1.clone()), i(r2.clone()), i(r5.clone())],
        vec![],
    ));
    let r14 = prf.add_subproof();
    prf.with_mut_subproof(&r14, |sub2| {
        sub2.add_premise(p("A"));
        sub2.add_step(Justification(p("B"), RuleM::Reit, vec![], vec![]));
        sub2.add_step(Justification(p("C"), RuleM::Reit, vec![], vec![]));
    });
    let r15 = prf.add_subproof();
    prf.with_mut_subproof(&r15, |sub2| {
        sub2.add_premise(p("B"));
        sub2.add_step(Justification(p("A"), RuleM::Reit, vec![], vec![]));
        sub2.add_step(Justification(p("C"), RuleM::Reit, vec![], vec![]));
    });
    let r16 = prf.add_step(Justification(
        p("A === B"),
        RuleM::EquivalenceIntro,
        vec![],
        vec![r14.clone(), r15.clone()],
    ));
    let r17 = prf.add_step(Justification(
        p("A === C"),
        RuleM::EquivalenceIntro,
        vec![],
        vec![r14.clone(), r15.clone()],
    ));
    let r18 = prf.add_subproof();
    prf.with_mut_subproof(&r18, |sub2| {
        sub2.add_premise(p("P"));
        sub2.add_step(Justification(p("Q"), RuleM::Reit, vec![], vec![]));
    });
    let r19 = prf.add_step(Justification(
        p("P === Q"),
        RuleM::EquivalenceIntro,
        vec![i(r3.clone())],
        vec![r18.clone()],
    ));
    (
        prf,
        vec![i(r7), i(r8), i(r9), i(r11), i(r16), i(r19)],
        vec![i(r12), i(r13), i(r17)],
    )
}

pub fn test_equivelim<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A === B === C"));
    let r2 = prf.add_premise(p("A"));
    let r3 = prf.add_step(Justification(
        p("B"),
        RuleM::EquivalenceElim,
        vec![i(r1.clone()), i(r2.clone())],
        vec![],
    ));
    let r4 = prf.add_step(Justification(
        p("C"),
        RuleM::EquivalenceElim,
        vec![i(r1.clone()), i(r2.clone())],
        vec![],
    ));
    let r5 = prf.add_step(Justification(
        p("A"),
        RuleM::EquivalenceElim,
        vec![i(r1.clone()), i(r4.clone())],
        vec![],
    ));
    let r6 = prf.add_step(Justification(
        p("D"),
        RuleM::EquivalenceElim,
        vec![i(r1.clone()), i(r4.clone())],
        vec![],
    ));
    let r7 = prf.add_step(Justification(
        p("A"),
        RuleM::EquivalenceElim,
        vec![i(r1.clone()), i(r6.clone())],
        vec![],
    ));
    let r8 = prf.add_step(Justification(
        p("B"),
        RuleM::EquivalenceElim,
        vec![i(r2.clone()), i(r1.clone())],
        vec![],
    ));
    (prf, vec![i(r3), i(r4), i(r5), i(r8)], vec![i(r6), i(r7)])
}

pub fn test_forallelim<P: Proof + Debug>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>)
where
    P::Subproof: Debug,
{
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let r1 = prf.add_premise(p("forall x, p(x)"));
    let r2 = prf.add_step(Justification(
        p("p(a)"),
        RuleM::ForallElim,
        vec![i(r1.clone())],
        vec![],
    ));
    let r3 = prf.add_step(Justification(
        p("q(x)"),
        RuleM::ForallElim,
        vec![i(r1.clone())],
        vec![],
    ));
    let r4 = prf.add_step(Justification(
        p("p(A & B & C & D)"),
        RuleM::ForallElim,
        vec![i(r1.clone())],
        vec![],
    ));
    (prf, vec![i(r2), i(r4)], vec![i(r3)])
}

pub fn test_forallintro<P: Proof + Debug>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>)
where
    P::Subproof: Debug,
    PJRef<P>: Debug,
    P::SubproofReference: Debug,
{
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let r1 = prf.add_premise(p("forall x, p(x)"));
    let r2 = prf.add_premise(p("forall x, q(x)"));
    let r3 = prf.add_premise(p("r(c)"));
    let r4 = prf.add_subproof();
    let (r5, r6, r7) = prf
        .with_mut_subproof(&r4, |sub| {
            let r5 = sub.add_step(Justification(
                p("p(a)"),
                RuleM::ForallElim,
                vec![i(r1.clone())],
                vec![],
            ));
            let r6 = sub.add_step(Justification(
                p("q(a)"),
                RuleM::ForallElim,
                vec![i(r2.clone())],
                vec![],
            ));
            let r7 = sub.add_step(Justification(
                p("p(a) & q(a)"),
                RuleM::AndIntro,
                vec![i(r5.clone()), i(r6.clone())],
                vec![],
            ));
            (r5, r6, r7)
        })
        .unwrap();
    let r8 = prf.add_step(Justification(
        p("forall y, p(y) & q(y)"),
        RuleM::ForallIntro,
        vec![],
        vec![r4.clone()],
    ));
    let r9 = prf.add_step(Justification(
        p("forall y, p(a) & q(y)"),
        RuleM::ForallIntro,
        vec![],
        vec![r4.clone()],
    ));
    let r10 = prf.add_subproof();
    let r11 = prf
        .with_mut_subproof(&r10, |sub| {
            let r11 = sub.add_step(Justification(
                p("r(c)"),
                RuleM::Reit,
                vec![i(r3.clone())],
                vec![],
            ));
            println!("contained {:?}", sub.contained_justifications(true));
            println!(
                "reachable {:?}",
                sub.transitive_dependencies(i(r11.clone()))
            );
            println!(
                "reachable-contained {:?}",
                sub.transitive_dependencies(i(r11.clone()))
                    .difference(&sub.contained_justifications(true))
                    .collect::<HashSet<_>>()
            );
            r11
        })
        .unwrap();
    let r12 = prf.add_step(Justification(
        p("forall y, r(y)"),
        RuleM::ForallIntro,
        vec![],
        vec![r10.clone()],
    ));
    let r13 = prf.add_subproof();
    let (r17, r18, r19) = prf
        .with_mut_subproof(&r13, |sub1| {
            let r14 = sub1.add_subproof();
            let (r17, r18) = sub1
                .with_mut_subproof(&r14, |sub2| {
                    let r15 = sub2.add_subproof();
                    let r17 = sub2
                        .with_mut_subproof(&r15, |sub3| {
                            let r17 = sub3.add_step(Justification(
                                p("s(a, b)"),
                                RuleM::Reit,
                                vec![],
                                vec![],
                            ));
                            r17
                        })
                        .unwrap();
                    let r18 = sub2.add_step(Justification(
                        p("forall y, s(a, y)"),
                        RuleM::ForallIntro,
                        vec![],
                        vec![r15],
                    ));
                    (r17, r18)
                })
                .unwrap();
            let r19 = sub1.add_step(Justification(
                p("forall x, forall y, s(x, y)"),
                RuleM::ForallIntro,
                vec![],
                vec![r14],
            ));
            (r17, r18, r19)
        })
        .unwrap();

    let r20 = prf.add_subproof();
    let r22 = prf
        .with_mut_subproof(&r20, |sub| {
            let r21 = sub.add_premise(p("a"));
            let r22 = sub.add_step(Justification(
                p("a"),
                RuleM::Reit,
                vec![i(r21.clone())],
                vec![],
            ));
            r22
        })
        .unwrap();
    let r23 = prf.add_step(Justification(
        p("forall x, x"),
        RuleM::ForallIntro,
        vec![],
        vec![r20],
    ));
    (
        prf,
        vec![i(r5), i(r6), i(r7), i(r8), i(r11), i(r18), i(r19), i(r22)],
        vec![i(r9), i(r12), i(r17), i(r23)],
    )
}

pub fn test_existsintro<P: Proof + Debug>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>)
where
    P::Subproof: Debug,
    PJRef<P>: Debug,
    P::SubproofReference: Debug,
{
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();

    let r1 = prf.add_premise(p("p(a)"));
    let r2 = prf.add_step(Justification(p("p(b) & p(b)"), RuleM::Reit, vec![], vec![]));
    let r3 = prf.add_step(Justification(
        p("exists x, p(x)"),
        RuleM::ExistsIntro,
        vec![i(r1.clone())],
        vec![],
    ));
    let r4 = prf.add_step(Justification(
        p("exists x, p(a)"),
        RuleM::ExistsIntro,
        vec![i(r1.clone())],
        vec![],
    ));
    let r5 = prf.add_step(Justification(
        p("exists x, p(b)"),
        RuleM::ExistsIntro,
        vec![i(r1.clone())],
        vec![],
    ));
    let r6 = prf.add_step(Justification(
        p("exists x, p(x) & p(x)"),
        RuleM::ExistsIntro,
        vec![i(r2.clone())],
        vec![],
    ));
    let r7 = prf.add_step(Justification(
        p("exists x, p(b) & p(x)"),
        RuleM::ExistsIntro,
        vec![i(r2.clone())],
        vec![],
    ));
    let r8 = prf.add_step(Justification(
        p("exists x, p(x) & p(b)"),
        RuleM::ExistsIntro,
        vec![i(r2.clone())],
        vec![],
    ));
    let r9 = prf.add_step(Justification(
        p("exists x, p(b) & p(b)"),
        RuleM::ExistsIntro,
        vec![i(r2.clone())],
        vec![],
    ));
    let r10 = prf.add_step(Justification(
        p("exists x, p(y) & p(b)"),
        RuleM::ExistsIntro,
        vec![i(r2.clone())],
        vec![],
    ));
    let r11 = prf.add_step(Justification(
        p("exists x, p(a) & p(b)"),
        RuleM::ExistsIntro,
        vec![i(r2.clone())],
        vec![],
    ));
    let r12 = prf.add_step(Justification(
        p("exists x, p(y) & p(x)"),
        RuleM::ExistsIntro,
        vec![i(r2.clone())],
        vec![],
    ));

    (
        prf,
        vec![i(r3), i(r4), i(r6), i(r7), i(r8), i(r9)],
        vec![i(r5), i(r10), i(r11), i(r12)],
    )
}
pub fn test_existselim<P: Proof + Debug>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>)
where
    P::Subproof: Debug,
    PJRef<P>: Debug,
    P::SubproofReference: Debug,
{
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let r1 = prf.add_premise(p("exists x, p(x)"));
    let r2 = prf.add_premise(p("p(a) -> q(a)"));
    let r3 = prf.add_premise(p("forall b, (p(b) -> r(b))"));
    let r4 = prf.add_subproof();
    let (r6, r7, r8, r9, r10) = prf
        .with_mut_subproof(&r4, |sub| {
            let r5 = sub.add_premise(p("p(a)"));
            let r6 = sub.add_step(Justification(
                p("q(a)"),
                RuleM::ImpElim,
                vec![i(r2.clone()), i(r5.clone())],
                vec![],
            ));
            let r7 = sub.add_step(Justification(
                p("p(a) -> r(a)"),
                RuleM::ForallElim,
                vec![i(r3.clone())],
                vec![],
            ));
            let r8 = sub.add_step(Justification(
                p("r(a)"),
                RuleM::ImpElim,
                vec![i(r7.clone()), i(r5.clone())],
                vec![],
            ));
            let r9 = sub.add_step(Justification(
                p("exists x, q(x)"),
                RuleM::ExistsIntro,
                vec![i(r6.clone())],
                vec![],
            ));
            let r10 = sub.add_step(Justification(
                p("exists x, r(x)"),
                RuleM::ExistsIntro,
                vec![i(r8.clone())],
                vec![],
            ));
            (r6, r7, r8, r9, r10)
        })
        .unwrap();
    let r11 = prf.add_step(Justification(
        p("exists x, q(x)"),
        RuleM::ExistsElim,
        vec![i(r1.clone())],
        vec![r4.clone()],
    ));
    let r12 = prf.add_step(Justification(
        p("exists x, r(x)"),
        RuleM::ExistsElim,
        vec![i(r1.clone())],
        vec![r4.clone()],
    ));
    let r13 = prf.add_step(Justification(
        p("r(a)"),
        RuleM::ExistsElim,
        vec![i(r1.clone())],
        vec![r4.clone()],
    ));

    let s1 = prf.add_premise(p("forall y, man(y) → mortal(y)"));
    let s2 = prf.add_premise(p("exists x, man(x)"));
    let s3 = prf.add_subproof();
    let (s5, s6, s7) = prf
        .with_mut_subproof(&s3, |sub| {
            let s4 = sub.add_premise(p("man(socrates)"));
            let s5 = sub.add_step(Justification(
                p("man(socrates) → mortal(socrates)"),
                RuleM::ForallElim,
                vec![i(s1.clone())],
                vec![],
            ));
            let s6 = sub.add_step(Justification(
                p("mortal(socrates)"),
                RuleM::ImpElim,
                vec![i(s4.clone()), i(s5.clone())],
                vec![],
            ));
            let s7 = sub.add_step(Justification(
                p("exists foo, mortal(foo)"),
                RuleM::ExistsIntro,
                vec![i(s6.clone())],
                vec![],
            ));
            (s5, s6, s7)
        })
        .unwrap();
    let s8 = prf.add_step(Justification(
        p("exists foo, mortal(foo)"),
        RuleM::ExistsElim,
        vec![i(s2.clone())],
        vec![s3.clone()],
    ));

    let t1 = prf.add_step(Justification(
        p("p(a) -> r(a)"),
        RuleM::ForallElim,
        vec![i(r3.clone())],
        vec![],
    ));
    let t2 = prf.add_subproof();
    let (t4, t5) = prf
        .with_mut_subproof(&t2, |sub| {
            let t3 = sub.add_premise(p("p(a)"));
            let t4 = sub.add_step(Justification(
                p("r(a)"),
                RuleM::ImpElim,
                vec![i(t1.clone()), i(t3.clone())],
                vec![],
            ));
            let t5 = sub.add_step(Justification(
                p("exists x, r(x)"),
                RuleM::ExistsIntro,
                vec![i(t4.clone())],
                vec![],
            ));
            (t4, t5)
        })
        .unwrap();
    let t6 = prf.add_step(Justification(
        p("exists x, r(x)"),
        RuleM::ExistsElim,
        vec![i(r1.clone())],
        vec![t2.clone()],
    ));

    let u1 = prf.add_subproof();
    let u2 = prf.add_premise(p("forall c, forall d, p(c) -> s(d)"));
    let (u4, u5, u6) = prf
        .with_mut_subproof(&u1, |sub| {
            let u3 = sub.add_premise(p("p(a)"));
            let u4 = sub.add_step(Justification(
                p("forall d, p(a) -> s(d)"),
                RuleM::ForallElim,
                vec![i(u2.clone())],
                vec![],
            ));
            let u5 = sub.add_step(Justification(
                p("p(a) -> s(foo)"),
                RuleM::ForallElim,
                vec![i(u4.clone())],
                vec![],
            )); // TODO: generalized forall?
            let u6 = sub.add_step(Justification(
                p("s(foo)"),
                RuleM::ImpElim,
                vec![i(u3.clone()), i(u5.clone())],
                vec![],
            ));
            (u4, u5, u6)
        })
        .unwrap();
    let u7 = prf.add_step(Justification(
        p("s(foo)"),
        RuleM::ExistsElim,
        vec![i(r1.clone())],
        vec![u1.clone()],
    ));

    (
        prf,
        vec![
            i(r6),
            i(r7),
            i(r8),
            i(r9),
            i(r10),
            i(r12),
            i(s5),
            i(s6),
            i(s7),
            i(s8),
            i(t1),
            i(t4),
            i(t5),
            i(u4),
            i(u5),
            i(u6),
            i(u7),
        ],
        vec![i(r11), i(r13), i(t6)],
    )
}

pub fn test_commutation<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let r1 = prf.add_premise(p("(A & B & C) | (P & Q & R & S)"));
    let r2 = prf.add_premise(p("(a <-> b <-> c <-> d) === (bar -> quux)"));
    let r3 = prf.add_step(Justification(
        p("(Q & R & S & P) | (C & A & B)"),
        RuleM::Commutation,
        vec![i(r1.clone())],
        vec![],
    ));
    let r4 = prf.add_step(Justification(
        p("(A & B & C) | (P & Q & R & S)"),
        RuleM::Commutation,
        vec![i(r1.clone())],
        vec![],
    ));
    let r5 = prf.add_step(Justification(
        p("(A & B & C) & (P & Q & R & S)"),
        RuleM::Commutation,
        vec![i(r1.clone())],
        vec![],
    ));
    let r6 = prf.add_step(Justification(
        p("(a <-> b <-> c <-> d) === (bar -> quux)"),
        RuleM::Commutation,
        vec![i(r2.clone())],
        vec![],
    ));
    let r7 = prf.add_step(Justification(
        p("(d <-> a <-> b <-> c) === (bar -> quux)"),
        RuleM::Commutation,
        vec![i(r2.clone())],
        vec![],
    ));
    let r8 = prf.add_step(Justification(
        p("(bar -> quux) === (d <-> a <-> b <-> c)"),
        RuleM::Commutation,
        vec![i(r2.clone())],
        vec![],
    ));
    let r9 = prf.add_step(Justification(
        p("(a <-> b <-> c <-> d) === (quux -> bar)"),
        RuleM::Commutation,
        vec![i(r2.clone())],
        vec![],
    ));
    (
        prf,
        vec![i(r3), i(r4), i(r6), i(r7), i(r8)],
        vec![i(r5), i(r9)],
    )
}

pub fn test_association<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let r1 = prf.add_premise(p("(A & B & C) | (P & Q & R & S) | (U <-> V <-> W)"));
    let r2 = prf.add_step(Justification(
        p("(A & (B & C)) | ((((P & Q) & (R & S)) | ((U <-> V) <-> W)))"),
        RuleM::Association,
        vec![i(r1.clone())],
        vec![],
    ));
    let r3 = prf.add_step(Justification(
        p("(A & B & C) | (P & Q & R & S) | (U | V | W)"),
        RuleM::Association,
        vec![i(r1.clone())],
        vec![],
    ));
    (prf, vec![i(r2)], vec![i(r3)])
}

pub fn test_demorgan<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();

    let r1 = prf.add_premise(p("~(A & B)"));
    let r2 = prf.add_premise(p("~(A | B)"));
    let r3 = prf.add_premise(p("~(A | B | C)"));
    let r4 = prf.add_premise(p("~~(A | B)"));
    let r5 = prf.add_premise(p("~(~(A & B) | ~(C | D))"));
    let r6 = prf.add_premise(p("~~~~~~~~~~~~~~~~(A & B)"));
    let r7 = prf.add_step(Justification(
        p("~A | ~B"),
        RuleM::DeMorgan,
        vec![i(r1.clone())],
        vec![],
    ));
    let r8 = prf.add_step(Justification(
        p("~(A | B)"),
        RuleM::DeMorgan,
        vec![i(r1.clone())],
        vec![],
    ));
    let r9 = prf.add_step(Justification(
        p("~(~A & ~B)"),
        RuleM::DeMorgan,
        vec![i(r1.clone())],
        vec![],
    ));
    let r10 = prf.add_step(Justification(
        p("~(~A | ~B)"),
        RuleM::DeMorgan,
        vec![i(r1.clone())],
        vec![],
    ));
    let r11 = prf.add_step(Justification(
        p("~A & ~B"),
        RuleM::DeMorgan,
        vec![i(r2.clone())],
        vec![],
    ));
    let r12 = prf.add_step(Justification(
        p("~(A & B)"),
        RuleM::DeMorgan,
        vec![i(r2.clone())],
        vec![],
    ));
    let r13 = prf.add_step(Justification(
        p("~(~A | ~B)"),
        RuleM::DeMorgan,
        vec![i(r2.clone())],
        vec![],
    ));
    let r14 = prf.add_step(Justification(
        p("~(~A & ~B)"),
        RuleM::DeMorgan,
        vec![i(r2.clone())],
        vec![],
    ));
    let r15 = prf.add_step(Justification(
        p("~A & ~B & ~C"),
        RuleM::DeMorgan,
        vec![i(r3.clone())],
        vec![],
    ));
    let r16 = prf.add_step(Justification(
        p("~(A & B & C)"),
        RuleM::DeMorgan,
        vec![i(r3.clone())],
        vec![],
    ));
    let r17 = prf.add_step(Justification(
        p("~A | ~B | ~C"),
        RuleM::DeMorgan,
        vec![i(r3.clone())],
        vec![],
    ));
    let r18 = prf.add_step(Justification(
        p("~~A | ~~B"),
        RuleM::DeMorgan,
        vec![i(r4.clone())],
        vec![],
    ));
    let r19 = prf.add_step(Justification(
        p("~(~A & ~B)"),
        RuleM::DeMorgan,
        vec![i(r4.clone())],
        vec![],
    ));
    let r20 = prf.add_step(Justification(
        p("~((~A | ~B) | ~(C | D))"),
        RuleM::DeMorgan,
        vec![i(r5.clone())],
        vec![],
    ));
    let r21 = prf.add_step(Justification(
        p("~(~(A & B) | (~C & ~D))"),
        RuleM::DeMorgan,
        vec![i(r5.clone())],
        vec![],
    ));
    let r22 = prf.add_step(Justification(
        p("~((~A | ~B) | (~C & ~D))"),
        RuleM::DeMorgan,
        vec![i(r5.clone())],
        vec![],
    ));
    let r23 = prf.add_step(Justification(
        p("~(~A | ~B) & ~(~C & ~D)"),
        RuleM::DeMorgan,
        vec![i(r5.clone())],
        vec![],
    ));
    let r24 = prf.add_step(Justification(
        p("(~~A & ~~B) & (~~C | ~~D)"),
        RuleM::DeMorgan,
        vec![i(r5.clone())],
        vec![],
    ));
    let r25 = prf.add_step(Justification(
        p("(~~(A & B) & ~~(C | D))"),
        RuleM::DeMorgan,
        vec![i(r5.clone())],
        vec![],
    ));
    let r26 = prf.add_step(Justification(
        p("~~((A & B) & (C | D))"),
        RuleM::DeMorgan,
        vec![i(r5.clone())],
        vec![],
    ));
    let r27 = prf.add_step(Justification(
        p("~((A | B) | (C & D))"),
        RuleM::DeMorgan,
        vec![i(r5.clone())],
        vec![],
    ));
    let r28 = prf.add_step(Justification(
        p("~~((A & B) | (C | D))"),
        RuleM::DeMorgan,
        vec![i(r5.clone())],
        vec![],
    ));
    let r29 = prf.add_step(Justification(
        p("~~~~~~~~~~~~~~~~A & ~~~~~~~~~~~~~~~~B"),
        RuleM::DeMorgan,
        vec![i(r6.clone())],
        vec![],
    ));
    let r30 = prf.add_step(Justification(
        p("~~~~~~~~~~~~~~~~A | ~~~~~~~~~~~~~~~~B"),
        RuleM::DeMorgan,
        vec![i(r6.clone())],
        vec![],
    ));

    (
        prf,
        vec![
            i(r7),
            i(r11),
            i(r15),
            i(r18),
            i(r19),
            i(r20),
            i(r21),
            i(r22),
            i(r23),
            i(r24),
            i(r25),
            i(r26),
            i(r29),
        ],
        vec![
            i(r8),
            i(r9),
            i(r10),
            i(r12),
            i(r13),
            i(r14),
            i(r16),
            i(r17),
            i(r27),
            i(r28),
            i(r30),
        ],
    )
}

pub fn test_idempotence<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();

    let r1 = prf.add_premise(p("A & A"));
    let r2 = prf.add_premise(p("A | A"));
    let r3 = prf.add_premise(p("A & A & A & A & A"));
    let r4 = prf.add_premise(p("(A | A) & (A | A)"));
    let r5 = prf.add_premise(p("(A | A) & (B | B)"));
    let r6 = prf.add_premise(p("(A | (A | A)) & ((B & B) | B)"));
    let r7 = prf.add_premise(p("A & A & B"));

    let r8 = prf.add_step(Justification(
        p("A"),
        RuleM::Idempotence,
        vec![i(r1.clone())],
        vec![],
    ));
    let r9 = prf.add_step(Justification(
        p("A"),
        RuleM::Idempotence,
        vec![i(r2.clone())],
        vec![],
    ));
    let r10 = prf.add_step(Justification(
        p("A"),
        RuleM::Idempotence,
        vec![i(r3.clone())],
        vec![],
    ));
    let r11 = prf.add_step(Justification(
        p("A"),
        RuleM::Idempotence,
        vec![i(r4.clone())],
        vec![],
    ));
    let r12 = prf.add_step(Justification(
        p("A & B"),
        RuleM::Idempotence,
        vec![i(r5.clone())],
        vec![],
    ));
    let r13 = prf.add_step(Justification(
        p("A"),
        RuleM::Idempotence,
        vec![i(r5.clone())],
        vec![],
    ));
    let r14 = prf.add_step(Justification(
        p("B"),
        RuleM::Idempotence,
        vec![i(r5.clone())],
        vec![],
    ));
    let r15 = prf.add_step(Justification(
        p("A | B"),
        RuleM::Idempotence,
        vec![i(r5.clone())],
        vec![],
    ));
    let r16 = prf.add_step(Justification(
        p("A & B"),
        RuleM::Idempotence,
        vec![i(r6.clone())],
        vec![],
    ));
    let r17 = prf.add_step(Justification(
        p("(A | A) & B"),
        RuleM::Idempotence,
        vec![i(r6.clone())],
        vec![],
    ));
    let r18 = prf.add_step(Justification(
        p("A & (B | B)"),
        RuleM::Idempotence,
        vec![i(r6.clone())],
        vec![],
    ));
    let r19 = prf.add_step(Justification(
        p("A"),
        RuleM::Idempotence,
        vec![i(r6.clone())],
        vec![],
    ));
    let r20 = prf.add_step(Justification(
        p("A"),
        RuleM::Idempotence,
        vec![i(r7.clone())],
        vec![],
    ));
    let r21 = prf.add_step(Justification(
        p("B"),
        RuleM::Idempotence,
        vec![i(r7.clone())],
        vec![],
    ));
    //TODO: Should we make this valid? Currently it is invalid as all args must be equal
    let r22 = prf.add_step(Justification(
        p("A & B"),
        RuleM::Idempotence,
        vec![i(r7.clone())],
        vec![],
    ));

    (
        prf,
        vec![i(r8), i(r9), i(r10), i(r11), i(r12), i(r16), i(r17), i(r18)],
        vec![i(r13), i(r14), i(r15), i(r19), i(r20), i(r21), i(r22)],
    )
}

pub fn test_doublenegation<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();

    let r1 = prf.add_premise(p("~~A & A"));
    let r2 = prf.add_premise(p("P & Q & ~~~~(~~R | S)"));
    let r3 = prf.add_premise(p("~P -> Q"));

    let r4 = prf.add_step(Justification(
        p("A & A"),
        RuleM::DoubleNegation,
        vec![i(r1.clone())],
        vec![],
    ));
    let r5 = prf.add_step(Justification(
        p("A & ~~~~A"),
        RuleM::DoubleNegation,
        vec![i(r1.clone())],
        vec![],
    ));
    let r6 = prf.add_step(Justification(
        p("~~P & Q & ~~~~(R | ~~~~S)"),
        RuleM::DoubleNegation,
        vec![i(r2.clone())],
        vec![],
    ));
    let r7 = prf.add_step(Justification(
        p("~~P & Q & (R | ~~~~S)"),
        RuleM::DoubleNegation,
        vec![i(r2.clone())],
        vec![],
    ));
    let r8 = prf.add_step(Justification(
        p("P & Q & (R | S)"),
        RuleM::DoubleNegation,
        vec![i(r2.clone())],
        vec![],
    ));
    let r9 = prf.add_step(Justification(
        p("~~~P -> ~~~~Q"),
        RuleM::DoubleNegation,
        vec![i(r3.clone())],
        vec![],
    ));

    let r10 = prf.add_step(Justification(
        p("~A & A"),
        RuleM::DoubleNegation,
        vec![i(r1.clone())],
        vec![],
    ));
    let r11 = prf.add_step(Justification(
        p("~~~~P -> ~~~Q"),
        RuleM::DoubleNegation,
        vec![i(r3.clone())],
        vec![],
    ));

    (
        prf,
        vec![i(r4), i(r5), i(r6), i(r7), i(r8), i(r9)],
        vec![i(r10), i(r11)],
    )
}

pub fn test_distribution<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();

    let p1 = prf.add_premise(p("A & (B | C)"));
    let p2 = prf.add_premise(p("(B & A) | (C & A)"));

    let r1 = prf.add_step(Justification(
        p("(A & B) | (A & C)"),
        RuleM::Distribution,
        vec![i(p1.clone())],
        vec![],
    ));
    let r2 = prf.add_step(Justification(
        p("A & (B | C)"),
        RuleM::Distribution,
        vec![i(p2.clone())],
        vec![],
    ));
    let r3 = prf.add_step(Justification(
        p("(B | C) & A"),
        RuleM::Distribution,
        vec![i(p2.clone())],
        vec![],
    ));

    let r4 = prf.add_step(Justification(
        p("A | (B & C)"),
        RuleM::Distribution,
        vec![i(p2.clone())],
        vec![],
    ));

    (prf, vec![i(r1), i(r2), i(r3)], vec![i(r4)])
}

pub fn test_complement<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();

    let r1 = prf.add_premise(p("A & ~A"));
    let r2 = prf.add_premise(p("~A & A"));
    let r3 = prf.add_premise(p("A | ~A"));
    let r4 = prf.add_premise(p("~A | A"));
    let r5 = prf.add_premise(p("~(forall A, A) | (forall B, B)"));
    let r6 = prf.add_premise(p("~(forall A, A) & (forall B, B)"));
    let r19 = prf.add_premise(p("(A -> A) & (B <-> B) & (C <-> ~C) & (~D <-> D)"));
    let r7 = prf.add_step(Justification(
        p("_|_"),
        RuleM::Complement,
        vec![i(r1.clone())],
        vec![],
    ));
    let r8 = prf.add_step(Justification(
        p("^|^"),
        RuleM::Complement,
        vec![i(r1.clone())],
        vec![],
    ));
    let r9 = prf.add_step(Justification(
        p("_|_"),
        RuleM::Complement,
        vec![i(r2.clone())],
        vec![],
    ));
    let r10 = prf.add_step(Justification(
        p("^|^"),
        RuleM::Complement,
        vec![i(r2.clone())],
        vec![],
    ));
    let r11 = prf.add_step(Justification(
        p("_|_"),
        RuleM::Complement,
        vec![i(r3.clone())],
        vec![],
    ));
    let r12 = prf.add_step(Justification(
        p("^|^"),
        RuleM::Complement,
        vec![i(r3.clone())],
        vec![],
    ));
    let r13 = prf.add_step(Justification(
        p("_|_"),
        RuleM::Complement,
        vec![i(r4.clone())],
        vec![],
    ));
    let r14 = prf.add_step(Justification(
        p("^|^"),
        RuleM::Complement,
        vec![i(r4.clone())],
        vec![],
    ));
    let r15 = prf.add_step(Justification(
        p("_|_"),
        RuleM::Complement,
        vec![i(r5.clone())],
        vec![],
    ));
    let r16 = prf.add_step(Justification(
        p("^|^"),
        RuleM::Complement,
        vec![i(r5.clone())],
        vec![],
    ));
    let r17 = prf.add_step(Justification(
        p("_|_"),
        RuleM::Complement,
        vec![i(r6.clone())],
        vec![],
    ));
    let r18 = prf.add_step(Justification(
        p("^|^"),
        RuleM::Complement,
        vec![i(r6.clone())],
        vec![],
    ));

    let r20 = prf.add_step(Justification(
        p("^|^ & ^|^ & _|_ & _|_"),
        RuleM::CondComplement,
        vec![i(r19.clone())],
        vec![],
    ));
    let r21 = prf.add_step(Justification(
        p("^|^ & (B <-> B) & (C <-> ~C) & (~D <-> D)"),
        RuleM::CondComplement,
        vec![i(r19.clone())],
        vec![],
    ));
    let r22 = prf.add_step(Justification(
        p("(A -> A) & ^|^ & (C <-> ~C) & (~D <-> D)"),
        RuleM::CondComplement,
        vec![i(r19.clone())],
        vec![],
    ));
    let r23 = prf.add_step(Justification(
        p("(A -> A) & (B <-> B) & _|_ & (~D <-> D)"),
        RuleM::CondComplement,
        vec![i(r19.clone())],
        vec![],
    ));
    let r24 = prf.add_step(Justification(
        p("(A -> A) & (B <-> B) & (C <-> ~C) & _|_"),
        RuleM::CondComplement,
        vec![i(r19.clone())],
        vec![],
    ));
    let r25 = prf.add_step(Justification(
        p("_|_ & _|_ & ^|^ & ^|^"),
        RuleM::CondComplement,
        vec![i(r19.clone())],
        vec![],
    ));

    (
        prf,
        vec![
            i(r7),
            i(r9),
            i(r12),
            i(r14),
            i(r16),
            i(r17),
            i(r20),
            i(r21),
            i(r22),
            i(r23),
            i(r24),
        ],
        vec![i(r8), i(r10), i(r11), i(r13), i(r15), i(r18), i(r25)],
    )
}

pub fn test_identity<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();

    let r1 = prf.add_premise(p("A & ^|^"));
    let r2 = prf.add_premise(p("^|^ & A"));
    let r3 = prf.add_premise(p("A | _|_"));
    let r4 = prf.add_premise(p("_|_ | A"));
    let r13 = prf.add_premise(p("(A -> _|_) & (^|^ -> B)"));
    let r17 = prf.add_premise(p("(A <-> _|_) & (^|^ <-> B)"));

    let r5 = prf.add_step(Justification(
        p("A"),
        RuleM::Identity,
        vec![i(r1.clone())],
        vec![],
    ));
    let r6 = prf.add_step(Justification(
        p("^|^"),
        RuleM::Identity,
        vec![i(r1.clone())],
        vec![],
    ));
    let r7 = prf.add_step(Justification(
        p("A"),
        RuleM::Identity,
        vec![i(r2.clone())],
        vec![],
    ));
    let r8 = prf.add_step(Justification(
        p("^|^"),
        RuleM::Identity,
        vec![i(r2.clone())],
        vec![],
    ));
    let r9 = prf.add_step(Justification(
        p("A"),
        RuleM::Identity,
        vec![i(r3.clone())],
        vec![],
    ));
    let r10 = prf.add_step(Justification(
        p("_|_"),
        RuleM::Identity,
        vec![i(r3.clone())],
        vec![],
    ));
    let r11 = prf.add_step(Justification(
        p("A"),
        RuleM::Identity,
        vec![i(r4.clone())],
        vec![],
    ));
    let r12 = prf.add_step(Justification(
        p("_|_"),
        RuleM::Identity,
        vec![i(r4.clone())],
        vec![],
    ));

    let r14 = prf.add_step(Justification(
        p("~A & B"),
        RuleM::CondIdentity,
        vec![i(r13.clone())],
        vec![],
    ));
    let r15 = prf.add_step(Justification(
        p("A & B"),
        RuleM::CondIdentity,
        vec![i(r13.clone())],
        vec![],
    ));
    let r16 = prf.add_step(Justification(
        p("~A & ~B"),
        RuleM::CondIdentity,
        vec![i(r13.clone())],
        vec![],
    ));
    let r18 = prf.add_step(Justification(
        p("~A & B"),
        RuleM::CondIdentity,
        vec![i(r17.clone())],
        vec![],
    ));
    let r19 = prf.add_step(Justification(
        p("A & B"),
        RuleM::CondIdentity,
        vec![i(r17.clone())],
        vec![],
    ));

    (
        prf,
        vec![i(r5), i(r7), i(r9), i(r11), i(r14), i(r18)],
        vec![i(r6), i(r8), i(r10), i(r12), i(r15), i(r16), i(r19)],
    )
}

pub fn test_annihilation<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();

    let r1 = prf.add_premise(p("A & _|_"));
    let r2 = prf.add_premise(p("_|_ & A"));
    let r3 = prf.add_premise(p("A | ^|^"));
    let r4 = prf.add_premise(p("^|^ | A"));
    let r13 = prf.add_premise(p("(A -> ^|^) & (_|_ -> B)"));
    let r15 = prf.add_premise(p("(A -> _|_)"));
    let r5 = prf.add_step(Justification(
        p("_|_"),
        RuleM::Annihilation,
        vec![i(r1.clone())],
        vec![],
    ));
    let r6 = prf.add_step(Justification(
        p("A"),
        RuleM::Annihilation,
        vec![i(r1.clone())],
        vec![],
    ));
    let r7 = prf.add_step(Justification(
        p("_|_"),
        RuleM::Annihilation,
        vec![i(r2.clone())],
        vec![],
    ));
    let r8 = prf.add_step(Justification(
        p("A"),
        RuleM::Annihilation,
        vec![i(r2.clone())],
        vec![],
    ));
    let r9 = prf.add_step(Justification(
        p("^|^"),
        RuleM::Annihilation,
        vec![i(r3.clone())],
        vec![],
    ));
    let r10 = prf.add_step(Justification(
        p("A"),
        RuleM::Annihilation,
        vec![i(r3.clone())],
        vec![],
    ));
    let r11 = prf.add_step(Justification(
        p("^|^"),
        RuleM::Annihilation,
        vec![i(r4.clone())],
        vec![],
    ));
    let r12 = prf.add_step(Justification(
        p("A"),
        RuleM::Annihilation,
        vec![i(r4.clone())],
        vec![],
    ));

    let r14 = prf.add_step(Justification(
        p("^|^ & ^|^"),
        RuleM::CondAnnihilation,
        vec![i(r13.clone())],
        vec![],
    ));
    let r16 = prf.add_step(Justification(
        p("^|^"),
        RuleM::CondAnnihilation,
        vec![i(r15.clone())],
        vec![],
    ));

    (
        prf,
        vec![i(r5), i(r7), i(r9), i(r11), i(r14)],
        vec![i(r6), i(r8), i(r10), i(r12), i(r16)],
    )
}

pub fn test_inverse<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();

    let r1 = prf.add_premise(p("~_|_"));
    let r2 = prf.add_premise(p("~^|^"));
    let r3 = prf.add_premise(p("~~_|_"));
    let r4 = prf.add_premise(p("~~^|^"));
    let r5 = prf.add_step(Justification(
        p("_|_"),
        RuleM::Inverse,
        vec![i(r1.clone())],
        vec![],
    ));
    let r6 = prf.add_step(Justification(
        p("^|^"),
        RuleM::Inverse,
        vec![i(r1.clone())],
        vec![],
    ));
    let r7 = prf.add_step(Justification(
        p("^|^"),
        RuleM::Inverse,
        vec![i(r2.clone())],
        vec![],
    ));
    let r8 = prf.add_step(Justification(
        p("_|_"),
        RuleM::Inverse,
        vec![i(r2.clone())],
        vec![],
    ));
    let r9 = prf.add_step(Justification(
        p("_|_"),
        RuleM::Inverse,
        vec![i(r3.clone())],
        vec![],
    ));
    let r10 = prf.add_step(Justification(
        p("^|^"),
        RuleM::Inverse,
        vec![i(r3.clone())],
        vec![],
    ));
    let r11 = prf.add_step(Justification(
        p("^|^"),
        RuleM::Inverse,
        vec![i(r4.clone())],
        vec![],
    ));
    let r12 = prf.add_step(Justification(
        p("_|_"),
        RuleM::Inverse,
        vec![i(r4.clone())],
        vec![],
    ));

    (
        prf,
        vec![i(r6), i(r8), i(r9), i(r11)],
        vec![i(r5), i(r7), i(r10), i(r12)],
    )
}

pub fn test_absorption<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();

    let r1 = prf.add_premise(p("A & (A | B)"));
    let r2 = prf.add_premise(p("A & (B | A)"));

    let r3 = prf.add_premise(p("A | (A & B)"));
    let r4 = prf.add_premise(p("A | (B & A)"));

    let r5 = prf.add_premise(p("(A & B) | A"));
    let r6 = prf.add_premise(p("(B & A) | A"));

    let r7 = prf.add_premise(p("(A | B) & A"));
    let r8 = prf.add_premise(p("(B | A) & A"));

    let r9 = prf.add_premise(p("((A | B) & A) & (((A | B) & A) | C)"));

    let r10 = prf.add_step(Justification(
        p("A"),
        RuleM::Absorption,
        vec![i(r1.clone())],
        vec![],
    ));
    let r11 = prf.add_step(Justification(
        p("A"),
        RuleM::Absorption,
        vec![i(r2.clone())],
        vec![],
    ));
    let r12 = prf.add_step(Justification(
        p("A"),
        RuleM::Absorption,
        vec![i(r3.clone())],
        vec![],
    ));
    let r13 = prf.add_step(Justification(
        p("A"),
        RuleM::Absorption,
        vec![i(r4.clone())],
        vec![],
    ));
    let r14 = prf.add_step(Justification(
        p("A"),
        RuleM::Absorption,
        vec![i(r5.clone())],
        vec![],
    ));
    let r15 = prf.add_step(Justification(
        p("A"),
        RuleM::Absorption,
        vec![i(r6.clone())],
        vec![],
    ));
    let r16 = prf.add_step(Justification(
        p("A"),
        RuleM::Absorption,
        vec![i(r7.clone())],
        vec![],
    ));
    let r17 = prf.add_step(Justification(
        p("A"),
        RuleM::Absorption,
        vec![i(r8.clone())],
        vec![],
    ));
    let r18 = prf.add_step(Justification(
        p("A"),
        RuleM::Absorption,
        vec![i(r9.clone())],
        vec![],
    ));

    (
        prf,
        vec![
            i(r10),
            i(r11),
            i(r12),
            i(r13),
            i(r14),
            i(r15),
            i(r16),
            i(r17),
            i(r18),
        ],
        vec![],
    )
}

pub fn test_reduction<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();

    let p1 = prf.add_premise(p("A & (~A | B)"));
    let p2 = prf.add_premise(p("(~~A | B) & ~A"));
    let p3 = prf.add_premise(p("(B & ~A) | A"));
    let p4 = prf.add_premise(p("~B | (A & ~~B)"));
    let p5 = prf.add_premise(p(
        "(forall A, (A & (~A | B))) | (~(forall A, (A & (~A | B))) & C)",
    ));
    let p6 = prf.add_premise(p("B & (C | (~C & ~A))"));
    let p7 = prf.add_premise(p("A | (~A & (~~A | B))"));
    let p8 = prf.add_premise(p("D | (~A & (~~A | B))"));

    let r1 = prf.add_step(Justification(
        p("A & B"),
        RuleM::Reduction,
        vec![i(p1.clone())],
        vec![],
    ));
    let r2 = prf.add_step(Justification(
        p("~A & B"),
        RuleM::Reduction,
        vec![i(p2.clone())],
        vec![],
    ));
    let r3 = prf.add_step(Justification(
        p("A | B"),
        RuleM::Reduction,
        vec![i(p3.clone())],
        vec![],
    ));
    let r4 = prf.add_step(Justification(
        p("~B | A"),
        RuleM::Reduction,
        vec![i(p4.clone())],
        vec![],
    ));
    let r5 = prf.add_step(Justification(
        p("(forall A, (A & B)) | C"),
        RuleM::Reduction,
        vec![i(p5.clone())],
        vec![],
    ));

    let r6 = prf.add_step(Justification(
        p("A"),
        RuleM::Reduction,
        vec![i(p1.clone())],
        vec![],
    ));
    let r7 = prf.add_step(Justification(
        p("A | B"),
        RuleM::Reduction,
        vec![i(p2.clone())],
        vec![],
    ));
    let r8 = prf.add_step(Justification(
        p("B"),
        RuleM::Reduction,
        vec![i(p3.clone())],
        vec![],
    ));
    let r9 = prf.add_step(Justification(
        p("B & A"),
        RuleM::Reduction,
        vec![i(p4.clone())],
        vec![],
    ));

    let r10 = prf.add_step(Justification(
        p("B & (C | ~A)"),
        RuleM::Reduction,
        vec![i(p6.clone())],
        vec![],
    ));
    let r11 = prf.add_step(Justification(
        p("B & (C & ~A)"),
        RuleM::Reduction,
        vec![i(p6.clone())],
        vec![],
    ));
    let r12 = prf.add_step(Justification(
        p("A | (~A & B)"),
        RuleM::Reduction,
        vec![i(p7.clone())],
        vec![],
    ));
    let r13 = prf.add_step(Justification(
        p("D | (~A & B)"),
        RuleM::Reduction,
        vec![i(p8.clone())],
        vec![],
    ));

    (
        prf,
        vec![i(r1), i(r2), i(r3), i(r4), i(r5), i(r10), i(r12), i(r13)],
        vec![i(r6), i(r7), i(r8), i(r9), i(r11)],
    )
}

pub fn test_adjacency<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();

    let p1 = prf.add_premise(p("(A & B) | (A & ~B)"));
    let p2 = prf.add_premise(p("(A | B) & (A | ~B)"));

    let r1 = prf.add_step(Justification(
        p("A"),
        RuleM::Adjacency,
        vec![i(p1.clone())],
        vec![],
    ));
    let r2 = prf.add_step(Justification(
        p("A"),
        RuleM::Adjacency,
        vec![i(p2.clone())],
        vec![],
    ));
    let r3 = prf.add_step(Justification(
        p("(B | A) & (A | ~B)"),
        RuleM::Adjacency,
        vec![i(p1.clone())],
        vec![],
    ));
    let r4 = prf.add_step(Justification(
        p("(~B & A) | (A & B)"),
        RuleM::Adjacency,
        vec![i(p2.clone())],
        vec![],
    ));

    let r5 = prf.add_step(Justification(
        p("B"),
        RuleM::Adjacency,
        vec![i(p1.clone())],
        vec![],
    ));
    let r6 = prf.add_step(Justification(
        p("B"),
        RuleM::Adjacency,
        vec![i(p2.clone())],
        vec![],
    ));

    (prf, vec![i(r1), i(r2), i(r3), i(r4)], vec![i(r5), i(r6)])
}

pub fn test_resolution<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let p1 = prf.add_premise(p("a1 | a2 | c"));
    let p2 = prf.add_premise(p("b1 | b2 | ~c"));
    let p3 = prf.add_premise(p("~c"));
    let p4 = prf.add_premise(p("c"));
    let p5 = prf.add_premise(p("a1 & a2 & c"));
    let p6 = prf.add_premise(p("(a1 & a2) | c"));

    let r1 = prf.add_step(Justification(
        p("a1 | a2 | b1 | b2"),
        RuleM::Resolution,
        vec![i(p1.clone()), i(p2.clone())],
        vec![],
    ));
    let r2 = prf.add_step(Justification(
        p("a1 | a2"),
        RuleM::Resolution,
        vec![i(p1.clone()), i(p3.clone())],
        vec![],
    ));
    let r3 = prf.add_step(Justification(
        p("_|_"),
        RuleM::Resolution,
        vec![i(p3.clone()), i(p4.clone())],
        vec![],
    ));
    let r4 = prf.add_step(Justification(
        p("a1 & a2"),
        RuleM::Resolution,
        vec![i(p3.clone()), i(p6.clone())],
        vec![],
    ));

    let r5 = prf.add_step(Justification(
        p("a1 | a2 | c | b1 | b2"),
        RuleM::Resolution,
        vec![i(p1.clone()), i(p2.clone())],
        vec![],
    ));
    let r6 = prf.add_step(Justification(
        p("a1 | a2 | b1"),
        RuleM::Resolution,
        vec![i(p1.clone()), i(p2.clone())],
        vec![],
    ));
    let r7 = prf.add_step(Justification(
        p("a1 | a2"),
        RuleM::Resolution,
        vec![i(p5.clone()), i(p3.clone())],
        vec![],
    ));
    let r8 = prf.add_step(Justification(
        p("a1 | a2"),
        RuleM::Resolution,
        vec![i(p3.clone()), i(p5.clone())],
        vec![],
    ));
    let r9 = prf.add_step(Justification(
        p("a1 | a2"),
        RuleM::Resolution,
        vec![i(p5.clone()), i(p5.clone())],
        vec![],
    ));

    (
        prf,
        vec![i(r1), i(r2), i(r3), i(r4)],
        vec![i(r5), i(r6), i(r7), i(r8), i(r9)],
    )
}

pub fn test_tautcon<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let p1 = prf.add_premise(p("_|_"));
    let p2 = prf.add_premise(p("^|^"));
    let p3 = prf.add_premise(p("A"));
    let p4 = prf.add_premise(p("~~A"));

    let r1 = prf.add_step(Justification(
        p("_|_"),
        RuleM::TautologicalConsequence,
        vec![i(p1.clone())],
        vec![],
    ));
    let r2 = prf.add_step(Justification(
        p("^|^"),
        RuleM::TautologicalConsequence,
        vec![i(p1.clone())],
        vec![],
    ));
    let r3 = prf.add_step(Justification(
        p("A"),
        RuleM::TautologicalConsequence,
        vec![i(p1.clone())],
        vec![],
    ));
    let r4 = prf.add_step(Justification(
        p("~~~((A & ~B) | ~C)"),
        RuleM::TautologicalConsequence,
        vec![i(p1.clone())],
        vec![],
    ));

    let r5 = prf.add_step(Justification(
        p("_|_"),
        RuleM::TautologicalConsequence,
        vec![i(p2.clone())],
        vec![],
    ));
    let r6 = prf.add_step(Justification(
        p("^|^"),
        RuleM::TautologicalConsequence,
        vec![i(p2.clone())],
        vec![],
    ));
    let r7 = prf.add_step(Justification(
        p("A"),
        RuleM::TautologicalConsequence,
        vec![i(p2.clone())],
        vec![],
    ));
    let r8 = prf.add_step(Justification(
        p("~~~((A & ~B) | ~C)"),
        RuleM::TautologicalConsequence,
        vec![i(p2.clone())],
        vec![],
    ));

    let r9 = prf.add_step(Justification(
        p("A"),
        RuleM::TautologicalConsequence,
        vec![i(p3.clone())],
        vec![],
    ));
    let r10 = prf.add_step(Justification(
        p("B"),
        RuleM::TautologicalConsequence,
        vec![i(p3.clone())],
        vec![],
    ));
    let r11 = prf.add_step(Justification(
        p("A | B"),
        RuleM::TautologicalConsequence,
        vec![i(p3.clone())],
        vec![],
    ));
    let r12 = prf.add_step(Justification(
        p("A & B"),
        RuleM::TautologicalConsequence,
        vec![i(p3.clone())],
        vec![],
    ));

    let r13 = prf.add_step(Justification(
        p("A"),
        RuleM::TautologicalConsequence,
        vec![i(p4.clone())],
        vec![],
    ));
    let r14 = prf.add_step(Justification(
        p("B"),
        RuleM::TautologicalConsequence,
        vec![i(p4.clone())],
        vec![],
    ));
    let r15 = prf.add_step(Justification(
        p("A | B"),
        RuleM::TautologicalConsequence,
        vec![i(p4.clone())],
        vec![],
    ));
    let r16 = prf.add_step(Justification(
        p("A & B"),
        RuleM::TautologicalConsequence,
        vec![i(p4.clone())],
        vec![],
    ));

    let r17 = prf.add_step(Justification(
        p("B"),
        RuleM::TautologicalConsequence,
        vec![i(p1.clone()), i(p4.clone())],
        vec![],
    ));

    (
        prf,
        vec![
            i(r1),
            i(r2),
            i(r3),
            i(r4),
            i(r6),
            i(r9),
            i(r11),
            i(r13),
            i(r15),
            i(r17),
        ],
        vec![i(r5), i(r7), i(r8), i(r10), i(r12), i(r14), i(r16)],
    )
}

pub fn test_empty_rule<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let p1 = prf.add_premise(p("A"));
    let r1 = prf.add_step(Justification(
        p("A"),
        RuleM::EmptyRule,
        vec![i(p1.clone())],
        vec![],
    ));

    (prf, vec![], vec![i(r1)])
}

pub fn test_modus_tollens<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let r1 = prf.add_premise(p("~Q"));
    let r2 = prf.add_premise(p("P -> Q"));
    let r3 = prf.add_premise(p("P"));
    let r4 = prf.add_premise(p("~A"));
    let r5 = prf.add_premise(p("A -> A"));
    let r6 = prf.add_step(Justification(
        p("~P"),
        RuleM::ModusTollens,
        vec![i(r1.clone()), i(r2.clone())],
        vec![],
    ));
    let r7 = prf.add_step(Justification(
        p("~P"),
        RuleM::ModusTollens,
        vec![i(r2.clone()), i(r1.clone())],
        vec![],
    ));
    let r8 = prf.add_step(Justification(
        p("~B"),
        RuleM::ModusTollens,
        vec![i(r1.clone()), i(r2.clone())],
        vec![],
    ));
    let r9 = prf.add_step(Justification(
        p("P"),
        RuleM::ModusTollens,
        vec![i(r3.clone()), i(r2.clone())],
        vec![],
    ));
    let r10 = prf.add_step(Justification(
        p("P"),
        RuleM::ModusTollens,
        vec![i(r2.clone()), i(r3.clone())],
        vec![],
    ));
    let r11 = prf.add_step(Justification(
        p("B"),
        RuleM::ModusTollens,
        vec![i(r3.clone()), i(r4.clone())],
        vec![],
    ));
    let r12 = prf.add_step(Justification(
        p("Q"),
        RuleM::ModusTollens,
        vec![i(r2.clone()), i(r4.clone())],
        vec![],
    ));
    let r13 = prf.add_step(Justification(
        p("~A"),
        RuleM::ModusTollens,
        vec![i(r4.clone()), i(r5.clone())],
        vec![],
    ));
    let r14 = prf.add_step(Justification(
        p("~P"),
        RuleM::ModusTollens,
        vec![i(r2.clone()), i(r1.clone())],
        vec![],
    ));
    (
        prf,
        vec![i(r6), i(r7), i(r13), i(r14)],
        vec![i(r8), i(r9), i(r10), i(r11), i(r12)],
    )
}

pub fn test_hypothetical_syllogism<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let r1 = prf.add_premise(p("P -> Q"));
    let r2 = prf.add_premise(p("Q -> R"));
    let r3 = prf.add_premise(p("R -> S"));
    let r4 = prf.add_premise(p("S -> T"));
    let r5 = prf.add_premise(p("P"));
    let r6 = prf.add_step(Justification(
        p("P -> R"),
        RuleM::HypotheticalSyllogism,
        vec![i(r1.clone()), i(r2.clone())],
        vec![],
    ));
    let r7 = prf.add_step(Justification(
        p("P -> R"),
        RuleM::HypotheticalSyllogism,
        vec![i(r2.clone()), i(r1.clone())],
        vec![],
    ));
    let r8 = prf.add_step(Justification(
        p("P"),
        RuleM::HypotheticalSyllogism,
        vec![i(r1.clone()), i(r2.clone())],
        vec![],
    ));
    let r9 = prf.add_step(Justification(
        p("P"),
        RuleM::HypotheticalSyllogism,
        vec![i(r3.clone()), i(r2.clone())],
        vec![],
    ));
    let r10 = prf.add_step(Justification(
        p("P"),
        RuleM::HypotheticalSyllogism,
        vec![i(r2.clone()), i(r5.clone())],
        vec![],
    ));
    let r11 = prf.add_step(Justification(
        p("B"),
        RuleM::HypotheticalSyllogism,
        vec![i(r3.clone()), i(r4.clone())],
        vec![],
    ));
    let r12 = prf.add_step(Justification(
        p("R -> P"),
        RuleM::HypotheticalSyllogism,
        vec![i(r2.clone()), i(r4.clone())],
        vec![],
    ));
    let r13 = prf.add_step(Justification(
        p("R -> T"),
        RuleM::HypotheticalSyllogism,
        vec![i(r3.clone()), i(r4.clone())],
        vec![],
    ));
    let r14 = prf.add_step(Justification(
        p("P -> T"),
        RuleM::HypotheticalSyllogism,
        vec![i(r6.clone()), i(r13.clone())],
        vec![],
    ));
    (
        prf,
        vec![i(r6), i(r7), i(r13), i(r14)],
        vec![i(r8), i(r9), i(r10), i(r11), i(r12)],
    )
}

pub fn test_disjunctive_syllogism<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let r1 = prf.add_premise(p("~Q"));
    let r2 = prf.add_premise(p("P ∨ Q"));
    let r3 = prf.add_premise(p("P"));
    let r4 = prf.add_step(Justification(
        p("P"),
        RuleM::DisjunctiveSyllogism,
        vec![i(r1.clone()), i(r2.clone())],
        vec![],
    ));
    let r5 = prf.add_step(Justification(
        p("P"),
        RuleM::DisjunctiveSyllogism,
        vec![i(r2.clone()), i(r1.clone())],
        vec![],
    ));
    let r6 = prf.add_step(Justification(
        p("~Q"),
        RuleM::DisjunctiveSyllogism,
        vec![i(r3.clone()), i(r2.clone())],
        vec![],
    ));
    let r7 = prf.add_step(Justification(
        p("~Q"),
        RuleM::DisjunctiveSyllogism,
        vec![i(r2.clone()), i(r3.clone())],
        vec![],
    ));
    let r8 = prf.add_step(Justification(
        p("Q"),
        RuleM::DisjunctiveSyllogism,
        vec![i(r1.clone()), i(r2.clone())],
        vec![],
    ));
    let r9 = prf.add_step(Justification(
        p("Q"),
        RuleM::DisjunctiveSyllogism,
        vec![i(r2.clone()), i(r1.clone())],
        vec![],
    ));
    (
        prf,
        vec![i(r4), i(r5)],
        vec![i(r6), i(r7), i(r8), i(r9)],
    )
}

pub fn test_constructive_dilemma<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let r1 = prf.add_premise(p("P -> Q"));
    let r2 = prf.add_premise(p("R -> S"));
    let r3 = prf.add_premise(p("S -> T"));
    let r4 = prf.add_premise(p("P | R"));
    let r5 = prf.add_premise(p("R | S"));
    let r6 = prf.add_step(Justification(
        p("Q | S"),
        RuleM::ConstructiveDilemma,
        vec![i(r1.clone()), i(r2.clone()), i(r4.clone())],
        vec![],
    ));
    let r7 = prf.add_step(Justification(
        p("Q | S"),
        RuleM::ConstructiveDilemma,
        vec![i(r2.clone()), i(r4.clone()), i(r1.clone())],
        vec![],
    ));
    let r8 = prf.add_step(Justification(
        p("S | T"),
        RuleM::ConstructiveDilemma,
        vec![i(r3.clone()), i(r2.clone()), i(r4.clone())],
        vec![],
    ));
    let r9 = prf.add_step(Justification(
        p("P"),
        RuleM::ConstructiveDilemma,
        vec![i(r3.clone()), i(r2.clone())],
        vec![],
    ));
    let r10 = prf.add_step(Justification(
        p("P"),
        RuleM::ConstructiveDilemma,
        vec![i(r2.clone()), i(r5.clone())],
        vec![],
    ));
    let r11 = prf.add_step(Justification(
        p("B"),
        RuleM::ConstructiveDilemma,
        vec![i(r3.clone()), i(r4.clone())],
        vec![],
    ));
    let r12 = prf.add_step(Justification(
        p("R -> P"),
        RuleM::ConstructiveDilemma,
        vec![i(r2.clone()), i(r4.clone())],
        vec![],
    ));
    let r13 = prf.add_step(Justification(
        p("R -> T"),
        RuleM::ConstructiveDilemma,
        vec![i(r3.clone()), i(r4.clone())],
        vec![],
    ));
    (
        prf,
        vec![i(r6), i(r7)],
        vec![i(r8), i(r9), i(r10), i(r11), i(r12), i(r13)],
    )
}

pub fn test_excluded_middle<P: Proof>() -> (P, Vec<PJRef<P>>, Vec<PJRef<P>>) {
    use self::coproduct_inject as i;
    use crate::parser::parse_unwrap as p;
    let mut prf = P::new();
    let r1 = prf.add_step(Justification(
        p("A | ~A"),
        RuleM::ExcludedMiddle,
        vec![],
        vec![],
    ));
    let r2 = prf.add_step(Justification(
        p("A | ~A"),
        RuleM::ExcludedMiddle,
        vec![i(r1.clone())],
        vec![],
    ));
    let r3 = prf.add_step(Justification(
        p("A & ~A"),
        RuleM::ExcludedMiddle,
        vec![],
        vec![],
    ));
    let r4 = prf.add_step(Justification(
        p("_|_ | ~_|_"),
        RuleM::ExcludedMiddle,
        vec![],
        vec![],
    ));
    let r5 = prf.add_step(Justification(
        p("^|^ | ~^|^"),
        RuleM::ExcludedMiddle,
        vec![],
        vec![],
    ));
    let r6 = prf.add_step(Justification(
        p("^|^ | ~_|_"),
        RuleM::ExcludedMiddle,
        vec![],
        vec![],
    ));
    let r7 = prf.add_step(Justification(p("P"), RuleM::ExcludedMiddle, vec![], vec![]));
    let r8 = prf.add_step(Justification(p("B"), RuleM::ExcludedMiddle, vec![], vec![]));
    let r9 = prf.add_step(Justification(
        p("R -> P"),
        RuleM::ExcludedMiddle,
        vec![],
        vec![],
    ));
    let r10 = prf.add_step(Justification(
        p("R -> T"),
        RuleM::ExcludedMiddle,
        vec![],
        vec![],
    ));
    let r11 = prf.add_step(Justification(
        p("(A & B & C & forall P, P) | ~(A & B & C & forall P, P)"),
        RuleM::ExcludedMiddle,
        vec![],
        vec![],
    ));
    (
        prf,
        vec![i(r1), i(r4), i(r5), i(r11)],
        vec![i(r2), i(r3), i(r6), i(r7), i(r8), i(r9), i(r10)],
    )
}
