use super::*;
use super::treeproof::*;

#[test]
fn test_andelim() {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let prf = Proof {
        premises: vec![
            ((), p("A & B & C & D")), // 1
            ((), p("E | F")) // 2
        ],
        lines: vec![
            Line::Direct((), Justification(p("A"), Rule::AndElim, vec![1..1])), // 3
            Line::Direct((), Justification(p("E"), Rule::AndElim, vec![1..1])), // 4
            Line::Direct((), Justification(p("A"), Rule::AndElim, vec![1..1, 1..1])), // 5
            Line::Direct((), Justification(p("A"), Rule::AndElim, vec![2..2])), // 6
        ],
    };
    println!("{}", prf);
    let prf = decorate_line_and_indent(prf).bimap(&mut |(li, ())| li, &mut |_| ());
    use ProofCheckError::*;
    assert_eq!(check_rule_at_line(&prf, 3), Ok(()));
    assert_eq!(check_rule_at_line(&prf, 4), Err(DoesNotOccur(p("E"), p("A & B & C & D"))));
    assert_eq!(check_rule_at_line(&prf, 5), Err(IncorrectDepCount(vec![1..1, 1..1], 1, 0)));
    assert!(if let Err(DepOfWrongForm(_)) = check_rule_at_line(&prf, 6) { true } else { false });
}

#[test]
fn demo_prettyprinting() {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let proof1 = Proof {
        premises: vec![((),p("A")), ((),p("B"))],
        lines: vec![
            Line::Direct((), Justification(p("A & B"), Rule::AndIntro, vec![1..1, 2..2])),
            Line::Subproof((), Proof {
                premises: vec![((),p("C"))],
                lines: vec![Line::Direct((), Justification(p("A & B"), Rule::Reit, vec![3..3]))],
            }),
            Line::Direct((), Justification(p("C -> (A & B)"), Rule::ImpIntro, vec![4..5])),
        ],
    };
    let proof2 = decorate_line_and_indent(proof1.clone());
    println!("{:?}\n{}\n{:?}", proof1, proof1, proof2);
}
