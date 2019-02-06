use super::*;
use std::fmt::Debug;

fn test_rules<P: Proof+Display>() where P::Reference: Debug+Eq, P::SubproofReference: Debug+Eq {
    test_andelim::<P>();
    test_contelim::<P>();
    test_orintro::<P>();
    test_reit::<P>();
    test_andintro::<P>();
    test_contradictionintro::<P>();
    test_notelim::<P>();
    test_impelim::<P>();
    test_biconelim::<P>();
}

fn test_rules_with_subproofs<P: Proof+Display>() where P::Reference: Debug+Eq, P::SubproofReference: Debug+Eq {
    test_impintro::<P>();
    test_notintro::<P>();
    test_orelim::<P>();
}

#[test] fn test_rules_on_treeproof() { test_rules::<treeproof::TreeProof<(), ()>>(); }
#[test] fn test_rules_on_pooledproof() { 
    test_rules::<pooledproof::PooledProof<Expr>>();
    test_rules_with_subproofs::<pooledproof::PooledProof<Expr>>();
}

pub fn demo_proof_1<P: Proof>() -> P where P: PartialEq+std::fmt::Debug, P::Reference: PartialEq+std::fmt::Debug, P::SubproofReference: PartialEq+std::fmt::Debug {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A"));
    let r2 = prf.add_premise(p("B"));
    let r3 = prf.add_step(Justification(p("A & B"), RuleM::AndIntro, vec![r1.clone(), r2.clone()], vec![]));
    let r4 = prf.add_subproof({
        let mut sub = P::new();
        sub.add_premise(p("C"));
        sub.add_step(Justification(p("A & B"), RuleM::Reit, vec![r3.clone()], vec![]));
        sub
    });
    let r5 = prf.add_step(Justification(p("C -> (A & B)"), RuleM::ImpIntro, vec![], vec![r4.clone()]));
    assert_eq!(prf.lookup(r1.clone()), Some(Coproduct::inject(p("A"))));
    assert_eq!(prf.lookup(r2.clone()), Some(Coproduct::inject(p("B"))));
    assert_eq!(prf.lookup(r3.clone()), Some(Coproduct::inject(Justification(p("A&B"), RuleM::AndIntro, vec![r1.clone(), r2.clone()], vec![]))));
    if let Some(sub) = prf.lookup_subproof(r4.clone()) { let _: P = sub; println!("lookup4 good"); } else { println!("lookup4 bad"); }
    assert_eq!(prf.lookup(r5), Some(Coproduct::inject(Justification(p("C->(A&B)"), RuleM::ImpIntro, vec![], vec![r4.clone()]))));
    prf
}

pub fn demo_proof_2<P: Proof>() -> (P, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A & B & C & D")); // 1
    let r2 = prf.add_premise(p("E | F")); // 2
    let r3 = prf.add_step(Justification(p("A"), RuleM::AndElim, vec![r1.clone()], vec![])); // 3
    let r4 = prf.add_step(Justification(p("E"), RuleM::AndElim, vec![r1.clone()], vec![])); // 4
    let r5 = prf.add_step(Justification(p("A"), RuleM::AndElim, vec![r1.clone(), r1.clone()], vec![])); // 5
    let r6 = prf.add_step(Justification(p("A"), RuleM::AndElim, vec![r2.clone()], vec![])); // 6
    (prf, vec![r1, r2, r3, r4, r5, r6])
}

fn test_andelim<P: Proof+Display>() where P::Reference: Debug+Eq, P::SubproofReference: Debug+Eq {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let (prf, lines) = demo_proof_2::<P>();
    println!("{}", prf);
    use ProofCheckError::*;
    assert_eq!(prf.verify_line(&lines[2]), Ok(()));
    assert_eq!(prf.verify_line(&lines[3]), Err(DoesNotOccur(p("E"), p("A & B & C & D"))));
    assert!(if let Err(IncorrectDepCount(v, 1)) = prf.verify_line(&lines[4]) { v.len() == 2 } else { false });
    assert!(if let Err(DepDoesNotExist(_, true)) = prf.verify_line(&lines[5]) { true } else { false });
}

pub fn demo_proof_3<P: Proof>() -> (P, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("_|_")); // 1
    let r2 = prf.add_premise(p("A & B")); // 2
    let r3 = prf.add_step(Justification(p("forall x, x & ~ x"), RuleM::ContradictionElim, vec![r1.clone()], vec![])); // 3
    let r4 = prf.add_step(Justification(p("Q"), RuleM::ContradictionElim, vec![r2.clone()], vec![])); // 4
    (prf, vec![r1, r2, r3, r4])
}

fn test_contelim<P: Proof+Display>() where P::Reference: Debug+Eq, P::SubproofReference: Debug+Eq {
    let (prf, lines) = demo_proof_3::<P>();
    println!("{}", prf);
    use ProofCheckError::*;
    assert_eq!(prf.verify_line(&lines[2]), Ok(()));
    assert!(if let Err(DepOfWrongForm(_, _)) = prf.verify_line(&lines[3]) { true } else { false });
}

pub fn demo_proof_4<P: Proof>() -> (P, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A")); // 1
    let r2 = prf.add_step(Justification(p("A | B | C"), RuleM::OrIntro, vec![r1.clone()], vec![])); // 2
    let r3 = prf.add_step(Justification(p("P | Q"), RuleM::OrIntro, vec![r1.clone()], vec![])); // 3
    let r4 = prf.add_step(Justification(p("P & Q"), RuleM::OrIntro, vec![r1.clone()], vec![])); // 4
    (prf, vec![r1, r2, r3, r4])
}

fn test_orintro<P: Proof+Display>() where P::Reference: Debug+Eq, P::SubproofReference: Debug+Eq {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let (prf, lines) = demo_proof_4::<P>();
    println!("{}", prf);
    use ProofCheckError::*;
    assert_eq!(prf.verify_line(&lines[1]), Ok(()));
    assert_eq!(prf.verify_line(&lines[2]), Err(DoesNotOccur(p("A"), p("P | Q"))));
    assert!(if let Err(ConclusionOfWrongForm(_)) = prf.verify_line(&lines[3]) { true } else { false });
}

pub fn demo_proof_5<P: Proof>() -> (P, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A")); // 1
    let r2 = prf.add_step(Justification(p("A"), RuleM::Reit, vec![r1.clone()], vec![])); // 2
    let r3 = prf.add_step(Justification(p("B"), RuleM::Reit, vec![r1.clone()], vec![])); // 3
    (prf, vec![r1, r2, r3])
}

fn test_reit<P: Proof+Display>() where P::Reference: Debug+Eq, P::SubproofReference: Debug+Eq {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let (prf, lines) = demo_proof_5::<P>();
    println!("{}", prf);
    use ProofCheckError::*;
    assert_eq!(prf.verify_line(&lines[1]), Ok(()));
    assert_eq!(prf.verify_line(&lines[2]), Err(DoesNotOccur(p("B"), p("A"))));
}

pub fn demo_proof_6<P: Proof>() -> (P, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A")); // 1
    let r2 = prf.add_premise(p("B")); // 2
    let r3 = prf.add_premise(p("C")); // 3
    let r4 = prf.add_step(Justification(p("A & B"), RuleM::AndIntro, vec![r1.clone(), r2.clone()], vec![])); // 4
    let r5 = prf.add_step(Justification(p("A & B"), RuleM::AndIntro, vec![r1.clone(), r2.clone(), r3.clone()], vec![])); // 5
    let r6 = prf.add_step(Justification(p("A & B"), RuleM::AndIntro, vec![r1.clone()], vec![])); // 6
    let r7 = prf.add_step(Justification(p("A & A"), RuleM::AndIntro, vec![r1.clone()], vec![])); // 7
    (prf, vec![r1, r2, r3, r4, r5, r6, r7])
}

fn test_andintro<P: Proof+Display>() where P::Reference: Debug+Eq, P::SubproofReference: Debug+Eq {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let (prf, lines) = demo_proof_6::<P>();
    println!("{}", prf);
    use ProofCheckError::*;
    assert_eq!(prf.verify_line(&lines[3]), Ok(()));
    assert_eq!(prf.verify_line(&lines[4]), Err(DoesNotOccur(p("C"), p("A & B"))));
    assert_eq!(prf.verify_line(&lines[5]), Err(DepDoesNotExist(p("B"), false)));
    assert_eq!(prf.verify_line(&lines[6]), Ok(()));
}

pub fn demo_proof_7<P: Proof>() -> (P, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A")); // 1
    let r2 = prf.add_premise(p("~A")); // 2
    let r3 = prf.add_premise(p("~~A")); // 3
    let r4 = prf.add_premise(p("B")); // 4
    let r5 = prf.add_step(Justification(p("_|_"), RuleM::ContradictionIntro, vec![r1.clone(), r2.clone()], vec![])); // 5
    let r6 = prf.add_step(Justification(p("_|_"), RuleM::ContradictionIntro, vec![r2.clone(), r3.clone()], vec![])); // 6
    let r7 = prf.add_step(Justification(p("_|_"), RuleM::ContradictionIntro, vec![r1.clone(), r3.clone()], vec![])); // 7
    let r8 = prf.add_step(Justification(p("_|_"), RuleM::ContradictionIntro, vec![r1.clone(), r4.clone()], vec![])); // 8
    let r9 = prf.add_step(Justification(p("Q(E,D)"), RuleM::ContradictionIntro, vec![r1.clone(), r2.clone()], vec![])); // 9
    (prf, vec![r1, r2, r3, r4, r5, r6, r7, r8, r9])
}

fn test_contradictionintro<P: Proof+Display>() where P::Reference: Debug+Eq, P::SubproofReference: Debug+Eq {
    let (prf, lines) = demo_proof_7::<P>();
    println!("{}", prf);
    use ProofCheckError::*;
    assert_eq!(prf.verify_line(&lines[4]), Ok(()));
    assert_eq!(prf.verify_line(&lines[5]), Ok(()));
    assert!(if let Err(Other(_)) = prf.verify_line(&lines[6]) { true } else { false });
    assert!(if let Err(Other(_)) = prf.verify_line(&lines[7]) { true } else { false });
    assert!(if let Err(ConclusionOfWrongForm(_)) = prf.verify_line(&lines[8]) { true } else { false });
}

pub fn demo_proof_8<P: Proof>() -> (P, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("~~A")); // 1
    let r2 = prf.add_premise(p("~~(A & B)")); // 2
    let r3 = prf.add_premise(p("~A")); // 3
    let r4 = prf.add_premise(p("A")); // 4

    let r5 = prf.add_step(Justification(p("A"), RuleM::NotElim, vec![r1.clone()], vec![]));  // 5
    let r6 = prf.add_step(Justification(p("A & B"), RuleM::NotElim, vec![r2.clone()], vec![])); // 6
    let r7 = prf.add_step(Justification(p("A"), RuleM::NotElim, vec![r3.clone()], vec![])); // 7
    let r8 = prf.add_step(Justification(p("A"), RuleM::NotElim, vec![r4.clone()], vec![])); // 8
    let r9 = prf.add_step(Justification(p("B"), RuleM::NotElim, vec![r2.clone()], vec![])); // 9

    (prf, vec![r1, r2, r3, r4, r5, r6, r7, r8, r9])
}

fn test_notelim<P: Proof+Display>() where P::Reference: Debug+Eq, P::SubproofReference: Debug+Eq {
    let (prf, lines) = demo_proof_8::<P>();
    println!("{}", prf);
    use ProofCheckError::*;
    assert_eq!(prf.verify_line(&lines[4]),Ok(()));
    assert_eq!(prf.verify_line(&lines[5]),Ok(()));
    assert!(if let Err(DepDoesNotExist(_, true)) = prf.verify_line(&lines[6]) {true} else {false});
    assert!(if let Err(DepDoesNotExist(_, true)) = prf.verify_line(&lines[7]) {true} else {false});
    assert!(if let Err(ConclusionOfWrongForm(_)) = prf.verify_line(&lines[8]) {true} else {false});
}

pub fn demo_proof_9<P: Proof>() -> (P, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();

    let r1 = prf.add_premise(p("P")); // 1
    let r2 = prf.add_premise(p("P -> Q")); // 2
    let r3 = prf.add_premise(p("Q")); // 3
    let r4 = prf.add_premise(p("A")); //4
    let r4p5 = prf.add_premise(p("A -> A")); //4.5

    //P -> Q, P and P, P->Q should get same result of Q
    let r5 = prf.add_step(Justification(p("Q"), RuleM::ImpElim, vec![r1.clone(), r2.clone()], vec![])); // 5
    let r6 = prf.add_step(Justification(p("Q"), RuleM::ImpElim, vec![r2.clone(), r1.clone()], vec![])); // 6

    //ensure that P -> Q, P should only give Q and not allow something else
    let r7 = prf.add_step(Justification(p("B"), RuleM::ImpElim, vec![r1.clone(), r2.clone()],vec![])); // 7

    //P -> Q, Q and Q, P->Q should get same result of error
    let r8 = prf.add_step(Justification(p("P"), RuleM::ImpElim, vec![r3.clone(), r2.clone()], vec![])); // 8
    let r9 = prf.add_step(Justification(p("P"), RuleM::ImpElim, vec![r2.clone(), r3.clone()], vec![])); // 9

    //ensure that you can't use this rule without an implication in premises
    let r10 = prf.add_step(Justification(p("B"), RuleM::ImpElim, vec![r3.clone(), r4.clone()], vec![])); // 10
    //can't do P->Q, A => Q
    let r11 = prf.add_step(Justification(p("Q"), RuleM::ImpElim, vec![r2.clone(), r4.clone()], vec![])); // 11
    let r12 = prf.add_step(Justification(p("A"), RuleM::ImpElim, vec![r4.clone(), r4p5.clone()], vec![])); // 12

    (prf, vec![r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r4p5,r12])
}

fn test_impelim<P: Proof+Display>() where P::Reference: Debug+Eq, P::SubproofReference: Debug+Eq {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let (prf, lines) = demo_proof_9::<P>();
    println!("{}", prf);
    use ProofCheckError::*;

    assert_eq!(prf.verify_line(&lines[4]), Ok(()));
    assert_eq!(prf.verify_line(&lines[4]), prf.verify_line(&lines[5]));

    assert_eq!(prf.verify_line(&lines[6]), Err(DoesNotOccur(p("B"), p("Q"))));
    assert!(if let Err(DoesNotOccur(_, _)) = prf.verify_line(&lines[7]) {true} else {false});
    assert!(if let Err(DoesNotOccur(_, _)) = prf.verify_line(&lines[8]) {true} else {false});
    assert!(if let Err(DepDoesNotExist(_, true)) = prf.verify_line(&lines[9]) {true} else {false});
    assert_eq!(prf.verify_line(&lines[10]), Err(DoesNotOccur(p("P -> Q"), p("A"))));
    assert_eq!(prf.verify_line(&lines[12]), Ok(()));
}

pub fn demo_proof_10<P: Proof>() -> (P, Vec<P::Reference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A <-> B <-> C"));
    let r2 = prf.add_premise(p("A"));
    let r3 = prf.add_step(Justification(p("B"), RuleM::BiconditionalElim, vec![r1.clone(), r2.clone()], vec![]));
    let r4 = prf.add_step(Justification(p("C"), RuleM::BiconditionalElim, vec![r1.clone(), r2.clone()], vec![]));
    let r5 = prf.add_step(Justification(p("A"), RuleM::BiconditionalElim, vec![r1.clone(), r4.clone()], vec![]));
    let r6 = prf.add_step(Justification(p("D"), RuleM::BiconditionalElim, vec![r1.clone(), r4.clone()], vec![]));
    let r7 = prf.add_step(Justification(p("A"), RuleM::BiconditionalElim, vec![r1.clone(), r6.clone()], vec![]));
    (prf, vec![r1, r2, r3, r4, r5, r6, r7])
}

fn test_biconelim<P: Proof+Display>() where P::Reference: Debug+Eq, P::SubproofReference: Debug+Eq {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let (prf, lines) = demo_proof_10::<P>();
    println!("{}", prf);
    use ProofCheckError::*;
    assert_eq!(prf.verify_line(&lines[2]), Ok(()));
    assert_eq!(prf.verify_line(&lines[3]), Ok(()));
    assert_eq!(prf.verify_line(&lines[4]), Ok(()));
    assert_eq!(prf.verify_line(&lines[5]), Err(DoesNotOccur(p("D"), p("A <-> B <-> C"))));
    assert_eq!(prf.verify_line(&lines[6]), Err(DoesNotOccur(p("D"), p("A <-> B <-> C"))));
}

pub fn demo_proof_11<P: Proof>() -> (P, Vec<P::Reference>, Vec<P::SubproofReference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A"));
    let r2 = prf.add_premise(p("B"));
    let mut sub1 = P::new();
    let r3 = sub1.add_premise(p("A"));
    let r4 = sub1.add_step(Justification(p("B"), RuleM::Reit, vec![r2.clone()], vec![]));
    let r5 = prf.add_subproof(sub1);
    let mut sub2 = P::new();
    let r6 = sub2.add_premise(p("A"));
    let r7 = sub2.add_step(Justification(p("A"), RuleM::Reit, vec![r1.clone()], vec![]));
    let r8 = prf.add_subproof(sub2);
    let r9 = prf.add_step(Justification(p("A -> B"), RuleM::ImpIntro, vec![], vec![r5.clone()]));
    let r10 = prf.add_step(Justification(p("A -> A"), RuleM::ImpIntro, vec![], vec![r8.clone()]));
    let r11 = prf.add_step(Justification(p("B -> A"), RuleM::ImpIntro, vec![], vec![r5.clone()]));
    (prf, vec![r1, r2, r3, r4, r6, r7, r9, r10, r11], vec![r5, r8])
}

fn test_impintro<P: Proof+Display>() where P::Reference: Debug+Eq, P::SubproofReference: Debug+Eq {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let (prf, lines, _) = demo_proof_11::<P>();
    println!("{}", prf);
    use ProofCheckError::*;
    assert_eq!(prf.verify_line(&lines[6]), Ok(()));
    assert_eq!(prf.verify_line(&lines[7]), Ok(()));
    assert_eq!(prf.verify_line(&lines[8]), Err(DoesNotOccur(p("B"), p("A"))));
}

pub fn demo_proof_12<P: Proof>() -> (P, Vec<P::Reference>, Vec<P::SubproofReference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A -> _|_"));
    let mut sub1 = P::new();
    let r2 = sub1.add_premise(p("A"));
    let r3 = sub1.add_step(Justification(p("_|_"), RuleM::ImpElim, vec![r1.clone(), r2.clone()], vec![])); // 5
    let r4 = prf.add_subproof(sub1);
    let r5 = prf.add_step(Justification(p("~A"), RuleM::NotIntro, vec![], vec![r4.clone()]));
    let r6 = prf.add_step(Justification(p("~B"), RuleM::NotIntro, vec![], vec![r4.clone()]));
    (prf, vec![r1, r2, r3, r5, r6], vec![r4])
}

fn test_notintro<P: Proof+Display>() where P::Reference: Debug+Eq, P::SubproofReference: Debug+Eq {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let (prf, lines, _) = demo_proof_12::<P>();
    println!("{}", prf);
    use ProofCheckError::*;
    assert_eq!(prf.verify_line(&lines[3]), Ok(()));
    assert_eq!(prf.verify_line(&lines[4]), Err(DoesNotOccur(p("B"), p("A"))));
}

pub fn demo_proof_13<P: Proof>() -> (P, Vec<P::Reference>, Vec<P::SubproofReference>) {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A | B"));
    let mut sub1 = P::new();
    let r2 = sub1.add_premise(p("A"));
    let r3 = sub1.add_step(Justification(p("C"), RuleM::Reit, vec![], vec![]));
    let r4 = prf.add_subproof(sub1);
    let mut sub2 = P::new();
    let r5 = sub2.add_premise(p("B"));
    let r6 = sub2.add_step(Justification(p("C"), RuleM::Reit, vec![], vec![]));
    let r7 = prf.add_subproof(sub2);
    let mut sub3 = P::new();
    let r8 = sub3.add_premise(p("B"));
    let r9 = sub3.add_step(Justification(p("D"), RuleM::Reit, vec![], vec![]));
    let r10 = prf.add_subproof(sub3);
    let r11 = prf.add_step(Justification(p("C"), RuleM::OrElim, vec![r1.clone()], vec![r4.clone(), r7.clone()]));
    let r12 = prf.add_step(Justification(p("D"), RuleM::OrElim, vec![r1.clone()], vec![r4.clone(), r7.clone()]));
    let r13 = prf.add_step(Justification(p("C"), RuleM::OrElim, vec![r1.clone()], vec![r4.clone(), r10.clone()]));
    (prf, vec![r1, r2, r3, r5, r6, r8, r9, r11, r12, r13], vec![r4, r7, r10])
}

fn test_orelim<P: Proof+Display>() where P::Reference: Debug+Eq, P::SubproofReference: Debug+Eq {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let (prf, lines, _) = demo_proof_13::<P>();
    println!("{}", prf);
    use ProofCheckError::*;
    assert_eq!(prf.verify_line(&lines[7]), Ok(()));
    assert_eq!(prf.verify_line(&lines[8]), Err(DepDoesNotExist(p("D"), false)));
    assert_eq!(prf.verify_line(&lines[9]), Err(DepDoesNotExist(p("C"), false)));
}
