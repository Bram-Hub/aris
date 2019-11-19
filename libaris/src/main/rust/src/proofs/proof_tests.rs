use super::*;
use std::fmt::Debug;

fn run_test<P: Proof+Display+Debug, F: FnOnce() -> (P, Vec<P::Reference>, Vec<P::Reference>)>(f: F) where P::Reference: Debug, P::SubproofReference: Debug {
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
            panic!("{} ({:?}) should have failed, but didn't", i, err);
        }
    } 
}

fn test_rules<P: Proof+Display+Debug>() where P::Reference: Debug+Eq, P::SubproofReference: Debug+Eq, P::Subproof: Debug {
    run_test::<P, _>(test_andelim);
    run_test::<P, _>(test_contelim);
    run_test::<P, _>(test_orintro);
    run_test::<P, _>(test_reit);
    run_test::<P, _>(test_andintro);
    run_test::<P, _>(test_contradictionintro);
    run_test::<P, _>(test_notelim);
    run_test::<P, _>(test_impelim);
    run_test::<P, _>(test_commutation);
    run_test::<P, _>(test_association);
    run_test::<P, _>(test_demorgan);
    run_test::<P, _>(test_idempotence);
    run_test::<P, _>(test_doublenegation);
    run_test::<P, _>(test_complement);
    run_test::<P, _>(test_identity);
    run_test::<P, _>(test_annihilation);
}

fn test_rules_with_subproofs<P: Proof+Display+Debug>() where P::Reference: Debug+Eq, P::SubproofReference: Debug+Eq, P::Subproof: Debug {
    run_test::<P, _>(test_forallintro);
    run_test::<P, _>(test_forallelim);
    run_test::<P, _>(test_biconelim);
    run_test::<P, _>(test_biconintro);
    run_test::<P, _>(test_impintro);
    run_test::<P, _>(test_notintro);
    run_test::<P, _>(test_orelim);
    run_test::<P, _>(test_equivelim);
    run_test::<P, _>(test_equivintro);
}

#[test] fn test_rules_on_treeproof() { test_rules::<treeproof::TreeProof<(), ()>>(); }
#[test] fn test_rules_on_pooledproof() { 
    test_rules::<pooledproof::PooledProof<Hlist![Expr]>>();
    test_rules_with_subproofs::<pooledproof::PooledProof<Hlist![Expr]>>();
}

pub fn demo_proof_1<P: Proof>() -> P where P: PartialEq+std::fmt::Debug, P::Reference: PartialEq+std::fmt::Debug, P::SubproofReference: PartialEq+std::fmt::Debug {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A"));
    let r2 = prf.add_premise(p("B"));
    let r3 = prf.add_step(Justification(p("A & B"), RuleM::AndIntro, vec![r1.clone(), r2.clone()], vec![]));
    let r4 = prf.add_subproof();
    prf.with_mut_subproof(&r4, |sub| {
        sub.add_premise(p("C"));
        sub.add_step(Justification(p("A & B"), RuleM::Reit, vec![r3.clone()], vec![]));
    });
    let r5 = prf.add_step(Justification(p("C -> (A & B)"), RuleM::ImpIntro, vec![], vec![r4.clone()]));
    assert_eq!(prf.lookup(r1.clone()), Some(Coproduct::inject(p("A"))));
    assert_eq!(prf.lookup(r2.clone()), Some(Coproduct::inject(p("B"))));
    assert_eq!(prf.lookup(r3.clone()), Some(Coproduct::inject(Justification(p("A&B"), RuleM::AndIntro, vec![r1.clone(), r2.clone()], vec![]))));
    if let Some(sub) = prf.lookup_subproof(r4.clone()) { let _: P::Subproof = sub; println!("lookup4 good"); } else { println!("lookup4 bad"); }
    assert_eq!(prf.lookup(r5), Some(Coproduct::inject(Justification(p("C->(A&B)"), RuleM::ImpIntro, vec![], vec![r4.clone()]))));
    prf
}

pub fn test_andelim<P: Proof>() -> (P, Vec<P::Reference>, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A & B & C & D"));
    let r2 = prf.add_premise(p("E | F"));
    let r3 = prf.add_step(Justification(p("A"), RuleM::AndElim, vec![r1.clone()], vec![]));
    let r4 = prf.add_step(Justification(p("E"), RuleM::AndElim, vec![r1.clone()], vec![]));
    let r5 = prf.add_step(Justification(p("A"), RuleM::AndElim, vec![r1.clone(), r1.clone()], vec![]));
    let r6 = prf.add_step(Justification(p("A"), RuleM::AndElim, vec![r2.clone()], vec![]));
    (prf, vec![r3], vec![r4, r5, r6])
}

pub fn test_contelim<P: Proof>() -> (P, Vec<P::Reference>, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("_|_"));
    let r2 = prf.add_premise(p("A & B"));
    let r3 = prf.add_step(Justification(p("forall x, x & ~ x"), RuleM::ContradictionElim, vec![r1.clone()], vec![]));
    let r4 = prf.add_step(Justification(p("Q"), RuleM::ContradictionElim, vec![r2.clone()], vec![]));
    (prf, vec![r3], vec![r4])
}

pub fn test_orintro<P: Proof>() -> (P, Vec<P::Reference>, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A"));
    let r2 = prf.add_step(Justification(p("A | B | C"), RuleM::OrIntro, vec![r1.clone()], vec![]));
    let r3 = prf.add_step(Justification(p("P | Q"), RuleM::OrIntro, vec![r1.clone()], vec![]));
    let r4 = prf.add_step(Justification(p("P & Q"), RuleM::OrIntro, vec![r1.clone()], vec![]));
    (prf, vec![r2], vec![r3, r4])
}

pub fn test_reit<P: Proof>() -> (P, Vec<P::Reference>, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A"));
    let r2 = prf.add_step(Justification(p("A"), RuleM::Reit, vec![r1.clone()], vec![]));
    let r3 = prf.add_step(Justification(p("B"), RuleM::Reit, vec![r1.clone()], vec![]));
    (prf, vec![r2], vec![r3])
}

pub fn test_andintro<P: Proof>() -> (P, Vec<P::Reference>, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A"));
    let r2 = prf.add_premise(p("B"));
    let r3 = prf.add_premise(p("C"));
    let r4 = prf.add_step(Justification(p("A & B"), RuleM::AndIntro, vec![r1.clone(), r2.clone()], vec![]));
    let r5 = prf.add_step(Justification(p("A & B"), RuleM::AndIntro, vec![r1.clone(), r2.clone(), r3.clone()], vec![]));
    let r6 = prf.add_step(Justification(p("A & B"), RuleM::AndIntro, vec![r1.clone()], vec![]));
    let r7 = prf.add_step(Justification(p("A & A"), RuleM::AndIntro, vec![r1.clone()], vec![]));
    (prf, vec![r4, r7], vec![r5, r6])
}

pub fn test_contradictionintro<P: Proof>() -> (P, Vec<P::Reference>, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A"));
    let r2 = prf.add_premise(p("~A"));
    let r3 = prf.add_premise(p("~~A"));
    let r4 = prf.add_premise(p("B"));
    let r5 = prf.add_step(Justification(p("_|_"), RuleM::ContradictionIntro, vec![r1.clone(), r2.clone()], vec![]));
    let r6 = prf.add_step(Justification(p("_|_"), RuleM::ContradictionIntro, vec![r2.clone(), r3.clone()], vec![]));
    let r7 = prf.add_step(Justification(p("_|_"), RuleM::ContradictionIntro, vec![r1.clone(), r3.clone()], vec![]));
    let r8 = prf.add_step(Justification(p("_|_"), RuleM::ContradictionIntro, vec![r1.clone(), r4.clone()], vec![]));
    let r9 = prf.add_step(Justification(p("Q(E,D)"), RuleM::ContradictionIntro, vec![r1.clone(), r2.clone()], vec![]));
    let r10 = prf.add_step(Justification(p("_|_"), RuleM::ContradictionIntro, vec![r2.clone(), r1.clone()], vec![]));
    (prf, vec![r5, r6, r10], vec![r7, r8, r9])
}

pub fn test_notelim<P: Proof>() -> (P, Vec<P::Reference>, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("~~A"));
    let r2 = prf.add_premise(p("~~(A & B)"));
    let r3 = prf.add_premise(p("~A"));
    let r4 = prf.add_premise(p("A"));
    let r5 = prf.add_step(Justification(p("A"), RuleM::NotElim, vec![r1.clone()], vec![]));  // 5
    let r6 = prf.add_step(Justification(p("A & B"), RuleM::NotElim, vec![r2.clone()], vec![]));
    let r7 = prf.add_step(Justification(p("A"), RuleM::NotElim, vec![r3.clone()], vec![]));
    let r8 = prf.add_step(Justification(p("A"), RuleM::NotElim, vec![r4.clone()], vec![]));
    let r9 = prf.add_step(Justification(p("B"), RuleM::NotElim, vec![r2.clone()], vec![]));
    (prf, vec![r5, r6], vec![r7, r8, r9])
}

pub fn test_impelim<P: Proof>() -> (P, Vec<P::Reference>, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("P"));
    let r2 = prf.add_premise(p("P -> Q"));
    let r3 = prf.add_premise(p("Q"));
    let r4 = prf.add_premise(p("A"));
    let r5 = prf.add_premise(p("A -> A"));
    let r6 = prf.add_step(Justification(p("Q"), RuleM::ImpElim, vec![r1.clone(), r2.clone()], vec![]));
    let r7 = prf.add_step(Justification(p("Q"), RuleM::ImpElim, vec![r2.clone(), r1.clone()], vec![]));
    let r8 = prf.add_step(Justification(p("B"), RuleM::ImpElim, vec![r1.clone(), r2.clone()],vec![]));
    let r9 = prf.add_step(Justification(p("P"), RuleM::ImpElim, vec![r3.clone(), r2.clone()], vec![]));
    let r10 = prf.add_step(Justification(p("P"), RuleM::ImpElim, vec![r2.clone(), r3.clone()], vec![]));
    let r11 = prf.add_step(Justification(p("B"), RuleM::ImpElim, vec![r3.clone(), r4.clone()], vec![]));
    let r12 = prf.add_step(Justification(p("Q"), RuleM::ImpElim, vec![r2.clone(), r4.clone()], vec![]));
    let r13 = prf.add_step(Justification(p("A"), RuleM::ImpElim, vec![r4.clone(), r5.clone()], vec![]));
    let r14 = prf.add_step(Justification(p("Q"), RuleM::ImpElim, vec![r2.clone(), r1.clone()], vec![]));
    (prf, vec![r6, r7, r13, r14], vec![r8, r9, r10, r11, r12])
}


pub fn test_biconelim<P: Proof>() -> (P, Vec<P::Reference>, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A <-> B <-> C"));
    let r2 = prf.add_premise(p("A"));
    let r3 = prf.add_step(Justification(p("B <-> C"), RuleM::BiconditionalElim, vec![r1.clone(), r2.clone()], vec![]));
    let r4 = prf.add_step(Justification(p("C <-> B"), RuleM::BiconditionalElim, vec![r1.clone(), r2.clone()], vec![]));
    let r5 = prf.add_step(Justification(p("D <-> B"), RuleM::BiconditionalElim, vec![r1.clone(), r2.clone()], vec![]));
    let r8 = prf.add_subproof();
    let (_r6, r7) = prf.with_mut_subproof(&r8, |sub| {
        let r6 = sub.add_premise(p("D"));
        let r7 = sub.add_step(Justification(p("A <-> B"), RuleM::BiconditionalElim, vec![r1.clone(), r6.clone()], vec![]));
        (r6, r7)
    }).unwrap();
    let r9 = prf.add_premise(p("A <-> B"));
    let r10 = prf.add_step(Justification(p("B"), RuleM::BiconditionalElim, vec![r1.clone(), r2.clone()], vec![]));
    let r11 = prf.add_step(Justification(p("B"), RuleM::BiconditionalElim, vec![r9.clone(), r2.clone()], vec![]));
    let r12 = prf.add_premise(p("A <-> B <-> C <-> D"));
    let r13 = prf.add_step(Justification(p("A <-> C <-> D"), RuleM::BiconditionalElim, vec![r10.clone(), r12.clone()], vec![]));
    let r14 = prf.add_step(Justification(p("C"), RuleM::BiconditionalElim, vec![r1.clone(), r9.clone()], vec![]));
    let r15 = prf.add_step(Justification(p("B <-> C"), RuleM::BiconditionalElim, vec![r2.clone(), r1.clone()], vec![]));
    static BICON_COMMUTATIVITY: bool = false;
    if BICON_COMMUTATIVITY {
        (prf, vec![r3, r4, r11, r13, r15], vec![r5, r7, r10])
    } else {
        (prf, vec![r3, r11, r13, r14, r15], vec![r4, r5, r7, r10])
    }
}

pub fn test_impintro<P: Proof+Debug>() -> (P, Vec<P::Reference>, Vec<P::Reference>) where P::Subproof: Debug {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A"));
    let r2 = prf.add_premise(p("B"));
    let r5 = prf.add_subproof();
    let r4 = prf.with_mut_subproof(&r5, |sub1| {
        let _r3 = sub1.add_premise(p("A"));
        let r4 = sub1.add_step(Justification(p("B"), RuleM::Reit, vec![r2.clone()], vec![]));
        println!("{:?}",sub1);
        r4
    }).unwrap();
    let r8 = prf.add_subproof();
    let r7 = prf.with_mut_subproof(&r8, |sub2| {
        let _r6 = sub2.add_premise(p("A"));
        let r7 = sub2.add_step(Justification(p("A"), RuleM::Reit, vec![r1.clone()], vec![]));
        println!("{:?}",sub2);
        r7
    }).unwrap();
    let r9 = prf.add_step(Justification(p("A -> B"), RuleM::ImpIntro, vec![], vec![r5.clone()]));
    let r10 = prf.add_step(Justification(p("A -> A"), RuleM::ImpIntro, vec![], vec![r8.clone()]));
    let r11 = prf.add_step(Justification(p("B -> A"), RuleM::ImpIntro, vec![], vec![r5.clone()]));
    (prf, vec![r4, r7, r9, r10], vec![r11])
}

pub fn test_notintro<P: Proof>() -> (P, Vec<P::Reference>, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A -> _|_"));
    let r4 = prf.add_subproof();
    prf.with_mut_subproof(&r4, |sub1| {
        let r2 = sub1.add_premise(p("A"));
        let _r3 = sub1.add_step(Justification(p("_|_"), RuleM::ImpElim, vec![r1.clone(), r2.clone()], vec![]));
    });
    let r5 = prf.add_step(Justification(p("~A"), RuleM::NotIntro, vec![], vec![r4.clone()]));
    let r6 = prf.add_step(Justification(p("~B"), RuleM::NotIntro, vec![], vec![r4.clone()]));
    (prf, vec![r5], vec![r6])
}

pub fn test_orelim<P: Proof>() -> (P, Vec<P::Reference>, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
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
    let r11 = prf.add_step(Justification(p("C"), RuleM::OrElim, vec![r1.clone()], vec![r4.clone(), r7.clone()]));
    let r12 = prf.add_step(Justification(p("D"), RuleM::OrElim, vec![r1.clone()], vec![r4.clone(), r7.clone()]));
    let r13 = prf.add_step(Justification(p("C"), RuleM::OrElim, vec![r1.clone()], vec![r4.clone(), r10.clone()]));
    (prf, vec![r11], vec![r12, r13])
}

pub fn test_biconintro<P: Proof+Debug>() -> (P, Vec<P::Reference>, Vec<P::Reference>) where P::Subproof: Debug {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("P -> Q"));
    let r2 = prf.add_premise(p("Q -> R"));
    let r3 = prf.add_premise(p("Q -> P"));
    let r4 = prf.add_premise(p("R -> Q"));
    let r5 = prf.add_premise(p("R -> P"));
    let r6 = prf.add_premise(p("A -> A"));
    let r7 = prf.add_step(Justification(p("A <-> A"), RuleM::BiconditionalIntro, vec![r6.clone()], vec![]));
    let r8 = prf.add_step(Justification(p("P <-> Q <-> R"), RuleM::BiconditionalIntro, vec![r1.clone(), r2.clone(), r3.clone(), r4.clone()], vec![]));
    let r9 = prf.add_step(Justification(p("P <-> Q <-> R"), RuleM::BiconditionalIntro, vec![r1.clone(), r2.clone(), r5.clone()], vec![]));
    let r10 = prf.add_subproof();
    prf.with_mut_subproof(&r10, |sub1| {
        sub1.add_premise(p("B"));
    });
    let r11 = prf.add_step(Justification(p("B <-> B"), RuleM::BiconditionalIntro, vec![], vec![r10.clone()]));
    let r12 = prf.add_step(Justification(p("P <-> Q <-> R <-> S"), RuleM::BiconditionalIntro, vec![r1.clone(), r2.clone(), r3.clone(), r4.clone()], vec![]));
    let r13 = prf.add_step(Justification(p("P <-> Q <-> R <-> S"), RuleM::BiconditionalIntro, vec![r1.clone(), r2.clone(), r5.clone()], vec![]));
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
    let r16 = prf.add_step(Justification(p("A <-> B"), RuleM::BiconditionalIntro, vec![], vec![r14.clone(), r15.clone()]));
    let r17 = prf.add_step(Justification(p("A <-> C"), RuleM::BiconditionalIntro, vec![], vec![r14.clone(), r15.clone()]));
    let r18 = prf.add_subproof();
    prf.with_mut_subproof(&r18, |sub2| {
        sub2.add_premise(p("P"));
        sub2.add_step(Justification(p("Q"), RuleM::Reit, vec![], vec![]));
    });
    let r19 = prf.add_step(Justification(p("P <-> Q"), RuleM::BiconditionalIntro, vec![r3.clone()], vec![r18.clone()]));
    (prf, vec![r7, r11, r16, r19], vec![r8, r9, r12, r13, r17])
}

pub fn test_equivintro<P: Proof+Debug>() -> (P, Vec<P::Reference>, Vec<P::Reference>) where P::Subproof: Debug {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("P -> Q"));
    let r2 = prf.add_premise(p("Q -> R"));
    let r3 = prf.add_premise(p("Q -> P"));
    let r4 = prf.add_premise(p("R -> Q"));
    let r5 = prf.add_premise(p("R -> P"));
    let r6 = prf.add_premise(p("A -> A"));
    let r7 = prf.add_step(Justification(p("A === A === A === A === A"), RuleM::EquivalenceIntro, vec![r6.clone()], vec![]));
    let r8 = prf.add_step(Justification(p("P === Q === R"), RuleM::EquivalenceIntro, vec![r1.clone(), r2.clone(), r3.clone(), r4.clone()], vec![]));
    let r9 = prf.add_step(Justification(p("P === Q === R"), RuleM::EquivalenceIntro, vec![r1.clone(), r2.clone(), r5.clone()], vec![]));
    let r10 = prf.add_subproof();
    prf.with_mut_subproof(&r10, |sub1| {
        sub1.add_premise(p("B"));
    });
    let r11 = prf.add_step(Justification(p("B === B === B"), RuleM::EquivalenceIntro, vec![], vec![r10.clone()]));
    let r12 = prf.add_step(Justification(p("P === Q === R === S"), RuleM::EquivalenceIntro, vec![r1.clone(), r2.clone(), r3.clone(), r4.clone()], vec![]));
    let r13 = prf.add_step(Justification(p("P === Q === R === S"), RuleM::EquivalenceIntro, vec![r1.clone(), r2.clone(), r5.clone()], vec![]));
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
    let r16 = prf.add_step(Justification(p("A === B"), RuleM::EquivalenceIntro, vec![], vec![r14.clone(), r15.clone()]));
    let r17 = prf.add_step(Justification(p("A === C"), RuleM::EquivalenceIntro, vec![], vec![r14.clone(), r15.clone()]));
    let r18 = prf.add_subproof();
    prf.with_mut_subproof(&r18, |sub2| {
        sub2.add_premise(p("P"));
        sub2.add_step(Justification(p("Q"), RuleM::Reit, vec![], vec![]));
    });
    let r19 = prf.add_step(Justification(p("P === Q"), RuleM::EquivalenceIntro, vec![r3.clone()], vec![r18.clone()]));
    (prf, vec![r7, r8, r9, r11, r16, r19], vec![r12, r13, r17])
}

pub fn test_equivelim<P: Proof>() -> (P, Vec<P::Reference>, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A === B === C"));
    let r2 = prf.add_premise(p("A"));
    let r3 = prf.add_step(Justification(p("B"), RuleM::EquivalenceElim, vec![r1.clone(), r2.clone()], vec![]));
    let r4 = prf.add_step(Justification(p("C"), RuleM::EquivalenceElim, vec![r1.clone(), r2.clone()], vec![]));
    let r5 = prf.add_step(Justification(p("A"), RuleM::EquivalenceElim, vec![r1.clone(), r4.clone()], vec![]));
    let r6 = prf.add_step(Justification(p("D"), RuleM::EquivalenceElim, vec![r1.clone(), r4.clone()], vec![]));
    let r7 = prf.add_step(Justification(p("A"), RuleM::EquivalenceElim, vec![r1.clone(), r6.clone()], vec![]));
    let r8 = prf.add_step(Justification(p("B"), RuleM::EquivalenceElim, vec![r2.clone(), r1.clone()], vec![]));
    (prf, vec![r3, r4, r5], vec![r6, r7])
}

pub fn test_forallelim<P: Proof+Debug>() -> (P, Vec<P::Reference>, Vec<P::Reference>) where P::Subproof: Debug {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("forall x, p(x)"));
    let r2 = prf.add_step(Justification(p("p(a)"), RuleM::ForallElim, vec![r1.clone()], vec![]));
    let r3 = prf.add_step(Justification(p("q(x)"), RuleM::ForallElim, vec![r1.clone()], vec![]));
    let r4 = prf.add_step(Justification(p("p(A & B & C & D)"), RuleM::ForallElim, vec![r1.clone()], vec![]));
    (prf, vec![r2, r4], vec![r3])
}

pub fn test_forallintro<P: Proof+Debug>() -> (P, Vec<P::Reference>, Vec<P::Reference>) where P::Subproof: Debug, P::Reference: Debug, P::SubproofReference: Debug {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("forall x, p(x)"));
    let r2 = prf.add_premise(p("forall x, q(x)"));
    let r3 = prf.add_premise(p("r(c)"));
    let r4 = prf.add_subproof();
    let (r5, r6, r7) = prf.with_mut_subproof(&r4, |sub| {
        let r5 = sub.add_step(Justification(p("p(a)"), RuleM::ForallElim, vec![r1.clone()], vec![]));
        let r6 = sub.add_step(Justification(p("q(a)"), RuleM::ForallElim, vec![r2.clone()], vec![]));
        let r7 = sub.add_step(Justification(p("p(a) & q(a)"), RuleM::AndIntro, vec![r5.clone(), r6.clone()], vec![]));
        (r5, r6, r7)
    }).unwrap();
    let r8 = prf.add_step(Justification(p("forall y, p(y) & q(y)"), RuleM::ForallIntro, vec![], vec![r4.clone()]));
    let r9 = prf.add_step(Justification(p("forall y, p(a) & q(y)"), RuleM::ForallIntro, vec![], vec![r4.clone()]));
    let r10 = prf.add_subproof();
    let r11 = prf.with_mut_subproof(&r10, |sub| {
        let r11 = sub.add_step(Justification(p("r(c)"), RuleM::Reit, vec![r3.clone()], vec![]));
        println!("contained {:?}", sub.contained_justifications());
        println!("reachable {:?}", sub.transitive_dependencies(r11.clone()));
        println!("reachable-contained {:?}", sub.transitive_dependencies(r11.clone()).difference(&sub.contained_justifications()).collect::<HashSet<_>>());
        r11
    }).unwrap();
    let r12 = prf.add_step(Justification(p("forall y, r(y)"), RuleM::ForallIntro, vec![], vec![r10.clone()]));
    (prf, vec![r5, r6, r7, r8, r11], vec![r9, r12])
}

pub fn test_commutation<P: Proof>() -> (P, Vec<P::Reference>, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("(A & B & C) | (P & Q & R & S)"));
    let r2 = prf.add_premise(p("(a <-> b <-> c <-> d) === (bar -> quux)"));
    let r3 = prf.add_step(Justification(p("(Q & R & S & P) | (C & A & B)"), RuleM::Commutation, vec![r1.clone()], vec![]));
    let r4 = prf.add_step(Justification(p("(A & B & C) | (P & Q & R & S)"), RuleM::Commutation, vec![r1.clone()], vec![]));
    let r5 = prf.add_step(Justification(p("(A & B & C) & (P & Q & R & S)"), RuleM::Commutation, vec![r1.clone()], vec![]));
    let r6 = prf.add_step(Justification(p("(a <-> b <-> c <-> d) === (bar -> quux)"), RuleM::Commutation, vec![r2.clone()], vec![]));
    let r7 = prf.add_step(Justification(p("(d <-> a <-> b <-> c) === (bar -> quux)"), RuleM::Commutation, vec![r2.clone()], vec![]));
    let r8 = prf.add_step(Justification(p("(bar -> quux) === (d <-> a <-> b <-> c)"), RuleM::Commutation, vec![r2.clone()], vec![]));
    let r9 = prf.add_step(Justification(p("(a <-> b <-> c <-> d) === (quux -> bar)"), RuleM::Commutation, vec![r2.clone()], vec![]));
    (prf, vec![r3, r4, r6, r7, r8], vec![r5, r9])
}

pub fn test_association<P: Proof>() -> (P, Vec<P::Reference>, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("(A & B & C) | (P & Q & R & S) | (U <-> V <-> W)"));
    let r2 = prf.add_step(Justification(p("(A & (B & C)) | ((((P & Q) & (R & S)) | ((U <-> V) <-> W)))"), RuleM::Association, vec![r1.clone()], vec![]));
    let r3 = prf.add_step(Justification(p("(A & B & C) | (P & Q & R & S) | (U | V | W)"), RuleM::Association, vec![r1.clone()], vec![]));
    (prf, vec![r2], vec![r3])
}

pub fn test_demorgan<P: Proof>() -> (P, Vec<P::Reference>, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();

    let r1 = prf.add_premise(p("~(A & B)"));
    let r2 = prf.add_premise(p("~(A | B)"));
    let r3 = prf.add_premise(p("~(A | B | C)"));
    let r4 = prf.add_premise(p("~~(A | B)"));
    let r5 = prf.add_premise(p("~(~(A & B) | ~(C | D))"));
    let r6 = prf.add_premise(p("~~~~~~~~~~~~~~~~(A & B)"));
    let r7 = prf.add_step(Justification(p("~A | ~B"), RuleM::DeMorgan, vec![r1.clone()], vec![]));
    let r8 = prf.add_step(Justification(p("~(A | B)"), RuleM::DeMorgan, vec![r1.clone()], vec![]));
    let r9 = prf.add_step(Justification(p("~(~A & ~B)"), RuleM::DeMorgan, vec![r1.clone()], vec![]));
    let r10 = prf.add_step(Justification(p("~(~A | ~B)"), RuleM::DeMorgan, vec![r1.clone()], vec![]));
    let r11 = prf.add_step(Justification(p("~A & ~B"), RuleM::DeMorgan, vec![r2.clone()], vec![]));
    let r12 = prf.add_step(Justification(p("~(A & B)"), RuleM::DeMorgan, vec![r2.clone()], vec![]));
    let r13 = prf.add_step(Justification(p("~(~A | ~B)"), RuleM::DeMorgan, vec![r2.clone()], vec![]));
    let r14 = prf.add_step(Justification(p("~(~A & ~B)"), RuleM::DeMorgan, vec![r2.clone()], vec![]));
    let r15 = prf.add_step(Justification(p("~A & ~B & ~C"), RuleM::DeMorgan, vec![r3.clone()], vec![]));
    let r16 = prf.add_step(Justification(p("~(A & B & C)"), RuleM::DeMorgan, vec![r3.clone()], vec![]));
    let r17 = prf.add_step(Justification(p("~A | ~B | ~C"), RuleM::DeMorgan, vec![r3.clone()], vec![]));
    let r18 = prf.add_step(Justification(p("~~A | ~~B"), RuleM::DeMorgan, vec![r4.clone()], vec![]));
    let r19 = prf.add_step(Justification(p("~(~A & ~B)"), RuleM::DeMorgan, vec![r4.clone()], vec![]));
    let r20 = prf.add_step(Justification(p("~((~A | ~B) | ~(C | D))"), RuleM::DeMorgan, vec![r5.clone()], vec![]));
    let r21 = prf.add_step(Justification(p("~(~(A & B) | (~C & ~D))"), RuleM::DeMorgan, vec![r5.clone()], vec![]));
    let r22 = prf.add_step(Justification(p("~((~A | ~B) | (~C & ~D))"), RuleM::DeMorgan, vec![r5.clone()], vec![]));
    let r23 = prf.add_step(Justification(p("~(~A | ~B) & ~(~C & ~D)"), RuleM::DeMorgan, vec![r5.clone()], vec![]));
    let r24 = prf.add_step(Justification(p("(~~A & ~~B) & (~~C | ~~D)"), RuleM::DeMorgan, vec![r5.clone()], vec![]));
    let r25 = prf.add_step(Justification(p("(~~(A & B) & ~~(C | D))"), RuleM::DeMorgan, vec![r5.clone()], vec![]));
    let r26 = prf.add_step(Justification(p("~~((A & B) & (C | D))"), RuleM::DeMorgan, vec![r5.clone()], vec![]));
    let r27 = prf.add_step(Justification(p("~((A | B) | (C & D))"), RuleM::DeMorgan, vec![r5.clone()], vec![]));
    let r28 = prf.add_step(Justification(p("~~((A & B) | (C | D))"), RuleM::DeMorgan, vec![r5.clone()], vec![]));
    let r29 = prf.add_step(Justification(p("~~~~~~~~~~~~~~~~A & ~~~~~~~~~~~~~~~~B"), RuleM::DeMorgan, vec![r6.clone()], vec![]));
    let r30 = prf.add_step(Justification(p("~~~~~~~~~~~~~~~~A | ~~~~~~~~~~~~~~~~B"), RuleM::DeMorgan, vec![r6.clone()], vec![]));

    (prf, vec![r7, r11, r15, r18, r19, r20, r21, r22, r23, r24, r25, r26, r29], vec![r8, r9, r10, r12, r13, r14, r16, r17, r27, r28, r30])
}

pub fn test_idempotence<P: Proof>() -> (P, Vec<P::Reference>, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();

    let r1 = prf.add_premise(p("A & A"));
    let r2 = prf.add_premise(p("A | A"));
    let r3 = prf.add_premise(p("A & A & A & A & A"));
    let r4 = prf.add_premise(p("(A | A) & (A | A)"));
    let r5 = prf.add_premise(p("(A | A) & (B | B)"));
    let r6 = prf.add_premise(p("(A | (A | A)) & ((B & B) | B)"));
    let r7 = prf.add_premise(p("A & A & B"));

    let r8 = prf.add_step(Justification(p("A"), RuleM::Idempotence, vec![r1.clone()], vec![]));
    let r9 = prf.add_step(Justification(p("A"), RuleM::Idempotence, vec![r2.clone()], vec![]));
    let r10 = prf.add_step(Justification(p("A"), RuleM::Idempotence, vec![r3.clone()], vec![]));
    let r11 = prf.add_step(Justification(p("A"), RuleM::Idempotence, vec![r4.clone()], vec![]));
    let r12 = prf.add_step(Justification(p("A & B"), RuleM::Idempotence, vec![r5.clone()], vec![]));
    let r13 = prf.add_step(Justification(p("A"), RuleM::Idempotence, vec![r5.clone()], vec![]));
    let r14 = prf.add_step(Justification(p("B"), RuleM::Idempotence, vec![r5.clone()], vec![]));
    let r15 = prf.add_step(Justification(p("A | B"), RuleM::Idempotence, vec![r5.clone()], vec![]));
    let r16 = prf.add_step(Justification(p("A & B"), RuleM::Idempotence, vec![r6.clone()], vec![]));
    let r17 = prf.add_step(Justification(p("(A | A) & B"), RuleM::Idempotence, vec![r6.clone()], vec![]));
    let r18 = prf.add_step(Justification(p("A & (B | B)"), RuleM::Idempotence, vec![r6.clone()], vec![]));
    let r19 = prf.add_step(Justification(p("A"), RuleM::Idempotence, vec![r6.clone()], vec![]));
    let r20 = prf.add_step(Justification(p("A"), RuleM::Idempotence, vec![r7.clone()], vec![]));
    let r21 = prf.add_step(Justification(p("B"), RuleM::Idempotence, vec![r7.clone()], vec![]));
    //TODO: Should we make this valid? Currently it is invalid as all args must be equal
    let r22 = prf.add_step(Justification(p("A & B"), RuleM::Idempotence, vec![r7.clone()], vec![]));

    (prf, vec![r8, r9, r10, r11, r12, r16, r17, r18], vec![r13, r14, r15, r19, r20, r21, r22])
}

pub fn test_doublenegation<P: Proof>() -> (P, Vec<P::Reference>, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();

    let r1 = prf.add_premise(p("~~A & A"));
    let r2 = prf.add_premise(p("P & Q & ~~~~(~~R | S)"));
    let r3 = prf.add_premise(p("~P -> Q"));

    let r4 = prf.add_step(Justification(p("A & A"), RuleM::DoubleNegation, vec![r1.clone()], vec![]));
    let r5 = prf.add_step(Justification(p("A & ~~~~A"), RuleM::DoubleNegation, vec![r1.clone()], vec![])); 
    let r6 = prf.add_step(Justification(p("~~P & Q & ~~~~(R | ~~~~S)"), RuleM::DoubleNegation, vec![r2.clone()], vec![]));
    let r7 = prf.add_step(Justification(p("~~P & Q & (R | ~~~~S)"), RuleM::DoubleNegation, vec![r2.clone()], vec![]));
    let r8 = prf.add_step(Justification(p("P & Q & (R | S)"), RuleM::DoubleNegation, vec![r2.clone()], vec![]));
    let r9 = prf.add_step(Justification(p("~~~P -> ~~~~Q"), RuleM::DoubleNegation, vec![r3.clone()], vec![]));

    let r10 = prf.add_step(Justification(p("~A & A"), RuleM::DoubleNegation, vec![r1.clone()], vec![]));
    let r11 = prf.add_step(Justification(p("~~~~P -> ~~~Q"), RuleM::DoubleNegation, vec![r3.clone()], vec![]));

    (prf, vec![r4, r5, r6, r7, r8, r9], vec![r10, r11])
}

pub fn test_complement<P: Proof>() -> (P, Vec<P::Reference>, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();

    let r1 = prf.add_premise(p("A & ~A"));
    let r2 = prf.add_premise(p("~A & A"));
    let r3 = prf.add_premise(p("A | ~A"));
    let r4 = prf.add_premise(p("~A | A"));
    let r5 = prf.add_premise(p("~(forall A, A) | (forall B, B)"));
    let r6 = prf.add_premise(p("~(forall A, A) & (forall B, B)"));
    let r7 = prf.add_step(Justification(p("_|_"), RuleM::Complement, vec![r1.clone()], vec![]));
    let r8 = prf.add_step(Justification(p("^|^"), RuleM::Complement, vec![r1.clone()], vec![]));
    let r9 = prf.add_step(Justification(p("_|_"), RuleM::Complement, vec![r2.clone()], vec![]));
    let r10 = prf.add_step(Justification(p("^|^"), RuleM::Complement, vec![r2.clone()], vec![]));
    let r11 = prf.add_step(Justification(p("_|_"), RuleM::Complement, vec![r3.clone()], vec![]));
    let r12 = prf.add_step(Justification(p("^|^"), RuleM::Complement, vec![r3.clone()], vec![]));
    let r13 = prf.add_step(Justification(p("_|_"), RuleM::Complement, vec![r4.clone()], vec![]));
    let r14 = prf.add_step(Justification(p("^|^"), RuleM::Complement, vec![r4.clone()], vec![]));
    let r15 = prf.add_step(Justification(p("_|_"), RuleM::Complement, vec![r5.clone()], vec![]));
    let r16 = prf.add_step(Justification(p("^|^"), RuleM::Complement, vec![r5.clone()], vec![]));
    let r17 = prf.add_step(Justification(p("_|_"), RuleM::Complement, vec![r6.clone()], vec![]));
    let r18 = prf.add_step(Justification(p("^|^"), RuleM::Complement, vec![r6.clone()], vec![]));

    (prf, vec![r7, r9, r12, r14, r16, r17], vec![r8, r10, r11, r13, r15, r18])
}

pub fn test_identity<P: Proof>() -> (P, Vec<P::Reference>, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();

    let r1 = prf.add_premise(p("A & ^|^"));
    let r2 = prf.add_premise(p("^|^ & A"));
    let r3 = prf.add_premise(p("A | _|_"));
    let r4 = prf.add_premise(p("_|_ | A"));
    let r5 = prf.add_step(Justification(p("A"), RuleM::Identity, vec![r1.clone()], vec![]));
    let r6 = prf.add_step(Justification(p("^|^"), RuleM::Identity, vec![r1.clone()], vec![]));
    let r7 = prf.add_step(Justification(p("A"), RuleM::Identity, vec![r2.clone()], vec![]));
    let r8 = prf.add_step(Justification(p("^|^"), RuleM::Identity, vec![r2.clone()], vec![]));
    let r9 = prf.add_step(Justification(p("A"), RuleM::Identity, vec![r3.clone()], vec![]));
    let r10 = prf.add_step(Justification(p("_|_"), RuleM::Identity, vec![r3.clone()], vec![]));
    let r11 = prf.add_step(Justification(p("A"), RuleM::Identity, vec![r4.clone()], vec![]));
    let r12 = prf.add_step(Justification(p("_|_"), RuleM::Identity, vec![r4.clone()], vec![]));

    (prf, vec![r5, r7, r9, r11], vec![r6, r8, r10, r12])
}

pub fn test_annihilation<P: Proof>() -> (P, Vec<P::Reference>, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();

    let r1 = prf.add_premise(p("A & _|_"));
    let r2 = prf.add_premise(p("_|_ & A"));
    let r3 = prf.add_premise(p("A | ^|^"));
    let r4 = prf.add_premise(p("^|^ | A"));
    let r5 = prf.add_step(Justification(p("_|_"), RuleM::Annihilation, vec![r1.clone()], vec![]));
    let r6 = prf.add_step(Justification(p("A"), RuleM::Annihilation, vec![r1.clone()], vec![]));
    let r7 = prf.add_step(Justification(p("_|_"), RuleM::Annihilation, vec![r2.clone()], vec![]));
    let r8 = prf.add_step(Justification(p("A"), RuleM::Annihilation, vec![r2.clone()], vec![]));
    let r9 = prf.add_step(Justification(p("^|^"), RuleM::Annihilation, vec![r3.clone()], vec![]));
    let r10 = prf.add_step(Justification(p("A"), RuleM::Annihilation, vec![r3.clone()], vec![]));
    let r11 = prf.add_step(Justification(p("^|^"), RuleM::Annihilation, vec![r4.clone()], vec![]));
    let r12 = prf.add_step(Justification(p("A"), RuleM::Annihilation, vec![r4.clone()], vec![]));

    (prf, vec![r5, r7, r9, r11], vec![r6, r8, r10, r12])
}

