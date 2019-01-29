use super::*;

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

pub fn demo_proof_2<P: Proof>() -> P {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A & B & C & D")); // 1
    let r2 = prf.add_premise(p("E | F")); // 2
    prf.add_step(Justification(p("A"), RuleM::AndElim, vec![r1.clone()], vec![])); // 3
    prf.add_step(Justification(p("E"), RuleM::AndElim, vec![r1.clone()], vec![])); // 4
    prf.add_step(Justification(p("A"), RuleM::AndElim, vec![r1.clone(), r1.clone()], vec![])); // 5
    prf.add_step(Justification(p("A"), RuleM::AndElim, vec![r2], vec![])); // 6
    prf
}

pub fn demo_proof_3<P: Proof>() -> P {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("_|_")); // 1
    let r2 = prf.add_premise(p("A & B")); // 2
    prf.add_step(Justification(p("forall x, x & ~ x"), RuleM::ContradictionElim, vec![r1.clone()], vec![])); // 3
    prf.add_step(Justification(p("Q"), RuleM::ContradictionElim, vec![r2.clone()], vec![])); // 4
    prf
}

pub fn demo_proof_4<P: Proof>() -> P {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A")); // 1
    prf.add_step(Justification(p("A | B | C"), RuleM::OrIntro, vec![r1.clone()], vec![])); // 2
    prf.add_step(Justification(p("P | Q"), RuleM::OrIntro, vec![r1.clone()], vec![])); // 3
    prf.add_step(Justification(p("P & Q"), RuleM::OrIntro, vec![r1.clone()], vec![])); // 4
    prf
}

pub fn demo_proof_5<P: Proof>() -> P {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A")); // 1
    prf.add_step(Justification(p("A"), RuleM::Reit, vec![r1.clone()], vec![])); // 2
    prf.add_step(Justification(p("B"), RuleM::Reit, vec![r1.clone()], vec![])); // 3
    prf
}

pub fn demo_proof_6<P: Proof>() -> P {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A")); // 1
    let r2 = prf.add_premise(p("B")); // 2
    let r3 = prf.add_premise(p("C")); // 3
    prf.add_step(Justification(p("A & B"), RuleM::AndIntro, vec![r1.clone(), r2.clone()], vec![])); // 4
    prf.add_step(Justification(p("A & B"), RuleM::AndIntro, vec![r1.clone(), r2.clone(), r3.clone()], vec![])); // 5
    prf.add_step(Justification(p("A & B"), RuleM::AndIntro, vec![r1.clone()], vec![])); // 6
    prf
}

pub fn demo_proof_7<P: Proof>() -> P {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A")); // 1
    let r2 = prf.add_premise(p("~A")); // 2
    let r3 = prf.add_premise(p("~~A")); // 3
    let r4 = prf.add_premise(p("B")); // 4
    prf.add_step(Justification(p("_|_"), RuleM::ContradictionIntro, vec![r1.clone(), r2.clone()], vec![])); // 5
    prf.add_step(Justification(p("_|_"), RuleM::ContradictionIntro, vec![r2.clone(), r3.clone()], vec![])); // 6
    prf.add_step(Justification(p("_|_"), RuleM::ContradictionIntro, vec![r1.clone(), r3.clone()], vec![])); // 7
    prf.add_step(Justification(p("_|_"), RuleM::ContradictionIntro, vec![r1.clone(), r4.clone()], vec![])); // 8
    prf.add_step(Justification(p("Q(E,D)"), RuleM::ContradictionIntro, vec![r1.clone(), r2.clone()], vec![])); // 9
    prf
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
