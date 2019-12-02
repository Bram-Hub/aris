use super::*;
use super::treeproof::*;

#[test]
fn demo_prettyprinting() {
    use parser::parse as p;
    let proof1 = TreeProof(TreeSubproof {
        premises: vec![((),p("A")), ((),p("B"))],
        lines: vec![
            Line::Direct((), Justification(p("A & B"), RuleM::AndIntro, vec![LineDep(1), LineDep(2)], vec![])),
            Line::Subproof((), TreeSubproof {
                premises: vec![((),p("C"))],
                lines: vec![Line::Direct((), Justification(p("A & B"), RuleM::Reit, vec![LineDep(3)], vec![]))],
            }),
            Line::Direct((), Justification(p("C -> (A & B)"), RuleM::ImpIntro, vec![], vec![SubproofDep(4..5)])),
        ],
    });
    //let proof1_: TreeProof<(), ()> = demo_proof_1();
    let proof2 = decorate_line_and_indent(proof1.clone());
    println!("{:?}\n{}\n{:?}", proof1, proof1, proof2);
    //assert_eq!(proof1, proof1_);
}
