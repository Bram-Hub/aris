use super::*;
use super::treeproof::*;

#[test]
fn test_andelim() {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut prf = TreeProof::new();
    prf.add_premise(p("A & B & C & D")); // 1
    prf.add_premise(p("E | F")); // 2
    prf.add_step(Justification(p("A"), Rule::AndElim, vec![1..1])); // 3
    prf.add_step(Justification(p("E"), Rule::AndElim, vec![1..1])); // 4
    prf.add_step(Justification(p("A"), Rule::AndElim, vec![1..1, 1..1])); // 5
    prf.add_step(Justification(p("A"), Rule::AndElim, vec![2..2])); // 6
    println!("{}", prf);
    let prf = decorate_line_and_indent(prf).bimap(&mut |(li, ())| li, &mut |_| ());
    use ProofCheckError::*;
    assert_eq!(check_rule_at_line(&prf, 3), Ok(()));
    assert_eq!(check_rule_at_line(&prf, 4), Err(DoesNotOccur(p("E"), p("A & B & C & D"))));
    assert_eq!(check_rule_at_line(&prf, 5), Err(IncorrectDepCount(vec![1..1, 1..1], 1, 0)));
    assert!(if let Err(DepOfWrongForm(_)) = check_rule_at_line(&prf, 6) { true } else { false });
}

fn demo_proof_1<P: Proof>() -> P {
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

#[test]
fn demo_prettyprinting() {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let proof1 = TreeProof {
        premises: vec![((),p("A")), ((),p("B"))],
        lines: vec![
            Line::Direct((), Justification(p("A & B"), Rule::AndIntro, vec![1..1, 2..2])),
            Line::Subproof((), TreeProof {
                premises: vec![((),p("C"))],
                lines: vec![Line::Direct((), Justification(p("A & B"), Rule::Reit, vec![3..3]))],
            }),
            Line::Direct((), Justification(p("C -> (A & B)"), Rule::ImpIntro, vec![4..5])),
        ],
    };
    let proof1_: TreeProof<(), ()> = demo_proof_1();
    let proof2 = decorate_line_and_indent(proof1.clone());
    println!("{:?}\n{}\n{}\n{:?}", proof1, proof1_, proof1, proof2);
    assert_eq!(proof1, proof1_);
}
