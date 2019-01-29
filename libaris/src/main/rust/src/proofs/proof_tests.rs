use super::*;
use std::fmt::Debug;

fn test_rules<P: Proof+Display>() where P::Reference: Debug+Eq, P::SubproofReference: Debug+Eq {
    test_andelim::<P>();
    test_contelim::<P>();
    test_orintro::<P>();
    test_reit::<P>();
    test_andintro::<P>();
    test_contradictionintro::<P>();
}

#[test] fn test_rules_on_treeproof() { test_rules::<treeproof::TreeProof<(), ()>>(); }
#[test] fn test_rules_on_pooledproof() { test_rules::<pooledproof::PooledProof<Expr>>(); }

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
    assert!(if let Err(DepOfWrongForm(_)) = prf.verify_line(&lines[5]) { true } else { false });
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
    assert!(if let Err(DepOfWrongForm(_)) = prf.verify_line(&lines[3]) { true } else { false });
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
    (prf, vec![r1, r2, r3, r4, r5, r6])
}

fn test_andintro<P: Proof+Display>() where P::Reference: Debug+Eq, P::SubproofReference: Debug+Eq {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let (prf, lines) = demo_proof_6::<P>();
    println!("{}", prf);
    use ProofCheckError::*;
    assert_eq!(prf.verify_line(&lines[3]), Ok(()));
    assert_eq!(prf.verify_line(&lines[4]), Err(DoesNotOccur(p("C"), p("A & B"))));
    assert_eq!(prf.verify_line(&lines[5]), Err(DepDoesNotExist(p("B"))));
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
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let (prf, lines) = demo_proof_7::<P>();
    println!("{}", prf);
    use ProofCheckError::*;
    assert_eq!(prf.verify_line(&lines[4]), Ok(()));
    assert_eq!(prf.verify_line(&lines[5]), Ok(()));
    assert!(if let Err(DepOfWrongForm(_)) = prf.verify_line(&lines[6]) { true } else { false });
    assert!(if let Err(DepOfWrongForm(_)) = prf.verify_line(&lines[7]) { true } else { false });
    assert!(if let Err(ConclusionOfWrongForm(_)) = prf.verify_line(&lines[8]) { true } else { false });
}

pub fn demo_proof_8<P: Proof>() -> P{
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("~~A")); // 1
    let r2 = prf.add_premise(p("~~(A & B)")); // 2
    let r3 = prf.add_premise(p("~A")); // 3
    let r4 = prf.add_premise(p("A")); // 4

    prf.add_step(Justification(p("A"), RuleM::NotElim, vec![r1.clone()], vec![]));  // 5
    prf.add_step(Justification(p("A & B"), RuleM::NotElim, vec![r2.clone()], vec![])); // 6
    prf.add_step(Justification(p("A"), RuleM::NotElim, vec![r3.clone()], vec![])); // 7
    prf.add_step(Justification(p("A"), RuleM::NotElim, vec![r4.clone()], vec![])); // 8
    prf.add_step(Justification(p("B"), RuleM::NotElim, vec![r2.clone()], vec![])); // 9

    prf
}
