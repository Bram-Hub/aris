use super::*;
use super::treeproof::*;
use super::proof_tests::*;

#[test]
fn test_notelim(){
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let prf = demo_proof_8();
    println!("{}", prf);
    let prf = decorate_line_and_indent(prf).bimap(&mut |(li, ())| li, &mut |_| ());
    use ProofCheckError::*;

    assert_eq!(check_rule_at_line(&prf,5),Ok(()));
    assert_eq!(check_rule_at_line(&prf,6),Ok(()));

    assert!(if let Err(DepOfWrongForm(_)) = check_rule_at_line(&prf,7) {true} else {false});
    assert!(if let Err(DepOfWrongForm(_)) = check_rule_at_line(&prf,8) {true} else {false});
    assert!(if let Err(ConclusionOfWrongForm(_)) = check_rule_at_line(&prf,9) {true} else {false});
}

#[test]
fn demo_prettyprinting() {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let proof1 = TreeProof {
        premises: vec![((),p("A")), ((),p("B"))],
        lines: vec![
            Line::Direct((), Justification(p("A & B"), RuleM::AndIntro, vec![LineDep(1), LineDep(2)], vec![])),
            Line::Subproof((), TreeProof {
                premises: vec![((),p("C"))],
                lines: vec![Line::Direct((), Justification(p("A & B"), RuleM::Reit, vec![LineDep(3)], vec![]))],
            }),
            Line::Direct((), Justification(p("C -> (A & B)"), RuleM::ImpIntro, vec![], vec![SubproofDep(4..5)])),
        ],
    };
    let proof1_: TreeProof<(), ()> = demo_proof_1();
    let proof2 = decorate_line_and_indent(proof1.clone());
    println!("{:?}\n{}\n{}\n{:?}", proof1, proof1_, proof1, proof2);
    assert_eq!(proof1, proof1_);
}
