use super::*;
use std::collections::HashMap;
use std::io::Read;
use xml::reader::EventReader;


pub fn proof_from_xml<P: Proof, R: Read>(r: R) -> Option<(P, Option<String>, Option<String>)> {
    let mut er = EventReader::new(r);

    let mut author = None;
    let mut hash = None;

    let mut element_stack = vec![];
    let mut attribute_stack = vec![];
    let mut contents = String::new();

    macro_rules! parse {
        ($x:expr) => { { let s: &str = $x; let t = format!("{}\n", s); if let Ok((_, u)) = parser::main(&t) { u } else { return None } } }
    }
    //let parse = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut subproofs: HashMap<_, <P as Proof>::SubproofReference> = HashMap::new();
    let mut lines_to_subs = HashMap::new();
    let mut line_refs: HashMap<_, <P as Proof>::Reference> = HashMap::new();
    let mut last_linenum = "".into();
    let mut proof = P::new();
    let mut current_proof_id = "0".into();
    let mut last_raw = "".into();

    let mut last_rule = "".into();
    let mut seen_premises = vec![];

    loop {
        use xml::reader::XmlEvent::*;
        match er.next() {
            //ref e if { println!("{:?}", e); false } => (),
            Ok(StartElement { name, attributes, namespace: _ }) => {
                let element = name.local_name;
                element_stack.push(element.clone());
                attribute_stack.push(attributes.clone());
                contents = String::new();
                match &*element {
                    "proof" => {
                        let id = attributes.iter().find(|x| x.name.local_name == "id").expect("proof element has no id attribute");
                        current_proof_id = id.value.clone();
                    },
                    "assumption" => {
                        let linenum = attributes.iter().find(|x| x.name.local_name == "linenum").expect("assumption element has no linenum attribute");
                        last_linenum = linenum.value.clone();
                    },
                    "step" => {
                        let linenum = attributes.iter().find(|x| x.name.local_name == "linenum").expect("step element has no linenum attribute");
                        last_linenum = linenum.value.clone();
                        last_rule = "".into();
                        seen_premises = vec![];
                    }
                    _ => (),
                }
            },
            Ok(Characters(data)) => { contents += &data; },
            Ok(EndElement { name }) => {
                //println!("end {:?} {:?}", element_stack, contents);
                let element = element_stack.pop().unwrap();
                assert_eq!(name.local_name, element);
                let _attributes = attribute_stack.pop().unwrap();
                //println!("{:?} {:?}", element, attributes);
                macro_rules! on_current_proof {
                    ($n:ident, $x:expr) => {
                        match &*current_proof_id {
                            "0" => { let $n = &mut proof; $x; },
                            r => { let key = subproofs[r].clone(); proof.with_mut_subproof(&key, |sub| { let $n = sub; $x }); },
                        }
                    }
                }
                match &*element {
                    "author" => { author = Some(contents.clone()) },
                    "hash" => { hash = Some(contents.clone()) },
                    "raw" => { last_raw = contents.clone(); },
                    "assumption" => {
                        on_current_proof! { proof, { let p = proof.add_premise(parse!(&last_raw)); line_refs.insert(last_linenum.clone(), p) } }
                    },
                    "rule" => { last_rule = contents.clone(); },
                    "premise" => { seen_premises.push(contents.clone()); },
                    "step" => {
                        //println!("step {:?} {:?}", last_rule, seen_premises);
                        match &*last_rule {
                            "SUBPROOF" => {
                                on_current_proof! { proof, { let p = proof.add_subproof(); subproofs.insert(seen_premises[0].clone(), p.clone()); lines_to_subs.insert(last_linenum.clone(), p) } }
                            }
                            rulename => {
                                let rule = RuleM::from_serialized_name(rulename).unwrap_or(RuleM::Reit); // TODO: explicit RuleM::NoSelectionMade?
                                //println!("{:?}", rule);
                                let deps = seen_premises.iter().filter_map(|x| line_refs.get(x)).cloned().collect::<Vec<_>>();
                                let sdeps = seen_premises.iter().filter_map(|x| lines_to_subs.get(x)).cloned().collect::<Vec<_>>();
                                //println!("{:?} {:?}", line_refs, subproofs);
                                //println!("{:?} {:?}", deps, sdeps);
                                let just = Justification(parse!(&last_raw), rule, deps, sdeps);
                                //println!("{:?}", just);
                                on_current_proof! { proof, { let p = proof.add_step(just); line_refs.insert(last_linenum.clone(), p); } }
                            },
                        }
                    },
                    _ => (),
                }
            },
            Ok(Whitespace(_)) => (),
            Ok(EndDocument) => break,
            Ok(_) => (),
            Err(e) => { println!("Error parsing xml document: {:?}", e); return None; },
        }
    }
    Some((proof, author, hash))
}

#[test]
fn test_xml() {
    let data = &include_bytes!("../../propositional_logic_arguments_for_proofs_ii_problem_10.bram")[..];
    type P = super::proofs::pooledproof::PooledProof<Hlist![Expr]>;
    let (prf, author, hash) = proof_from_xml::<P, _>(data).unwrap();
    println!("{:?} {:?}\n{}", author, hash, prf);
}

#[test]
fn test_xml2() {
    /*
    1 | | A
      | -----
    2 | | A ; REIT [1] []
    3 | A -> A ; -> I [] [1]
    */
    let xml = br#"
    <bram>
        <proof id="0">
            <step linenum="1">
                <rule>SUBPROOF</rule>
                <premise>1</premise>
            </step>
            <step linenum="3">
                <rule>CONDITIONAL_PROOF</rule>
                <raw>A -> A</raw>
                <premise>1</premise>
            </step>
        </proof>
        <proof id="1">
            <assumption linenum="1">
                <raw>A</raw>
            </assumption>
            <step linenum="2">
                <rule>REITERATION</rule>
                <raw>A</raw>
                <premise>1</premise>
            </step>
        </proof>
    </bram>
    "#;
    type P = super::proofs::pooledproof::PooledProof<Hlist![Expr]>;
    let (prf, author, hash) = proof_from_xml::<P, _>(&xml[..]).unwrap();
    println!("{:?} {:?}\n{}", author, hash, prf);
    let lines = prf.lines();
    let sub = prf.lookup_subproof(lines[0].get::<<P as Proof>::SubproofReference, _>().unwrap().clone()).unwrap();
    use expression_builders::{var, binop};
    assert_eq!(prf.lookup_expr(sub.premises()[0]), Some(var("A")));
    let sub_lines = sub.lines();
    let Justification(e1, r1, d1, s1) = prf.lookup(sub_lines[0].get::<<P as Proof>::Reference, _>().unwrap().clone()).unwrap().get::<Justification<_, _, _>, _>().unwrap().clone();
    assert_eq!(e1, var("A")); assert_eq!(r1, RuleM::Reit); assert_eq!(d1.len(), 1); assert_eq!(s1.len(), 0);
    let Justification(e2, r2, d2, s2) = prf.lookup(lines[1].get::<<P as Proof>::Reference, _>().unwrap().clone()).unwrap().get::<Justification<_, _, _>, _>().unwrap().clone();
    assert_eq!(e2, binop(BSymbol::Implies, var("A"), var("A"))); assert_eq!(r2, RuleM::ImpIntro); assert_eq!(d2.len(), 0); assert_eq!(s2.len(), 1);
}
