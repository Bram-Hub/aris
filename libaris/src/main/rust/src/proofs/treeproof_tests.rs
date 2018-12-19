use super::*;
use super::treeproof::*;
use super::proof_tests::*;

#[test]
fn test_andelim() {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let prf = demo_proof_2();
    println!("{}", prf);
    let prf = decorate_line_and_indent(prf).bimap(&mut |(li, ())| li, &mut |_| ());
    use ProofCheckError::*;
    assert_eq!(check_rule_at_line(&prf, 3), Ok(()));
    assert_eq!(check_rule_at_line(&prf, 4), Err(DoesNotOccur(p("E"), p("A & B & C & D"))));
    assert_eq!(check_rule_at_line(&prf, 5), Err(IncorrectDepCount(vec![LineDep(1..1), LineDep(1..1)], 1, 0)));
    assert!(if let Err(DepOfWrongForm(_)) = check_rule_at_line(&prf, 6) { true } else { false });
}

#[test]
fn demo_prettyprinting() {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let proof1 = TreeProof {
        premises: vec![((),p("A")), ((),p("B"))],
        lines: vec![
            Line::Direct((), Justification(p("A & B"), Rule::AndIntro, vec![LineDep(1..1), LineDep(2..2)])),
            Line::Subproof((), TreeProof {
                premises: vec![((),p("C"))],
                lines: vec![Line::Direct((), Justification(p("A & B"), Rule::Reit, vec![LineDep(3..3)]))],
            }),
            Line::Direct((), Justification(p("C -> (A & B)"), Rule::ImpIntro, vec![LineDep(4..5)])),
        ],
    };
    let proof1_: TreeProof<(), ()> = demo_proof_1();
    let proof2 = decorate_line_and_indent(proof1.clone());
    println!("{:?}\n{}\n{}\n{:?}", proof1, proof1_, proof1, proof2);
    assert_eq!(proof1, proof1_);
}
