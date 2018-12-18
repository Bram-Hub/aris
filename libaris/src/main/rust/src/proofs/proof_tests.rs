use super::*;

pub fn demo_proof_1<P: Proof>() -> P {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A"));
    let r2 = prf.add_premise(p("B"));
    let r3 = prf.add_step(Justification(p("A & B"), Rule::AndIntro, vec![r1, r2.clone()]));
    let r4 = prf.add_subproof({
        let mut sub = P::new();
        sub.add_premise(p("C"));
        sub.add_step(Justification(p("A & B"), Rule::Reit, vec![r3]));
        sub
    });
    prf.add_step(Justification(p("C -> (A & B)"), Rule::ImpIntro, vec![r4]));
    prf
}

pub fn demo_proof_2<P: Proof>() -> P {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = P::new();
    let r1 = prf.add_premise(p("A & B & C & D")); // 1
    let r2 = prf.add_premise(p("E | F")); // 2
    prf.add_step(Justification(p("A"), Rule::AndElim, vec![r1.clone()])); // 3
    prf.add_step(Justification(p("E"), Rule::AndElim, vec![r1.clone()])); // 4
    prf.add_step(Justification(p("A"), Rule::AndElim, vec![r1.clone(), r1.clone()])); // 5
    prf.add_step(Justification(p("A"), Rule::AndElim, vec![r2])); // 6
    prf
}

