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
    assert_eq!(check_rule_at_line(&prf, 5), Err(IncorrectDepCount(vec![LineDep(1), LineDep(1)], 1)));
    assert!(if let Err(DepOfWrongForm(_)) = check_rule_at_line(&prf, 6) { true } else { false });
}

#[test]
fn test_contelim() {
    let prf = demo_proof_3();
    println!("{}", prf);
    let prf = decorate_line_and_indent(prf).bimap(&mut |(li, ())| li, &mut |_| ());
    use ProofCheckError::*;
    assert_eq!(check_rule_at_line(&prf, 3), Ok(()));
    assert!(if let Err(DepOfWrongForm(_)) = check_rule_at_line(&prf, 4) { true } else { false });
}

#[test]
fn test_orintro() {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let prf = demo_proof_4();
    println!("{}", prf);
    let prf = decorate_line_and_indent(prf).bimap(&mut |(li, ())| li, &mut |_| ());
    use ProofCheckError::*;
    assert_eq!(check_rule_at_line(&prf, 2), Ok(()));
    assert_eq!(check_rule_at_line(&prf, 3), Err(DoesNotOccur(p("A"), p("P | Q"))));
    assert!(if let Err(ConclusionOfWrongForm(_)) = check_rule_at_line(&prf, 4) { true } else { false });
}

#[test]
fn test_reit() {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let prf = demo_proof_5();
    println!("{}", prf);
    let prf = decorate_line_and_indent(prf).bimap(&mut |(li, ())| li, &mut |_| ());
    use ProofCheckError::*;
    assert_eq!(check_rule_at_line(&prf, 2), Ok(()));
    assert_eq!(check_rule_at_line(&prf, 3), Err(DoesNotOccur(p("B"), p("A"))));
}

#[test]
fn test_andintro() {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let prf = demo_proof_6();
    println!("{}", prf);
    let prf = decorate_line_and_indent(prf).bimap(&mut |(li, ())| li, &mut |_| ());
    use ProofCheckError::*;
    assert_eq!(check_rule_at_line(&prf, 4), Ok(()));
    assert_eq!(check_rule_at_line(&prf, 5), Err(DoesNotOccur(p("C"), p("A & B"))));
    assert_eq!(check_rule_at_line(&prf, 6), Err(DepDoesNotExist(p("B"))));
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
