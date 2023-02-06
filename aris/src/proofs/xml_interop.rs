use crate::expr::Expr;
use crate::proofs::Justification;
use crate::proofs::PjRef;
use crate::proofs::Proof;
use crate::rules::RuleM;

use std::collections::HashMap;
use std::io::{Read, Write};

use frunk_core::coproduct::Coproduct;
use xml::reader::EventReader;

#[derive(Debug, Clone)]
pub struct ProofMetaData {
    pub author: Option<String>, // TODO: it seems like the java SaveManager might treat this as a Vec<String>
    pub hash: Option<String>,
    pub goals: Vec<Expr>,
}

pub fn proof_from_xml<P: Proof, R: Read>(r: R) -> Result<(P, ProofMetaData), String> {
    let mut er = EventReader::new(r);

    let mut metadata = ProofMetaData { author: None, hash: None, goals: vec![] };

    let mut element_stack = vec![];
    let mut attribute_stack = vec![];
    let mut contents = String::new();

    macro_rules! parse {
        ($x:expr) => {{
            let s: &str = $x;
            match crate::parser::parse(&s) {
                Some(e) => e,
                None if s == "" => Expr::Var { name: "__xml_interop_blank_line".into() },
                None => return Err(format!("Failed to parse {:?}, element stack {:?}", s, element_stack)),
            }
        }};
    }
    //let parse = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let mut subproofs: HashMap<_, <P as Proof>::SubproofReference> = HashMap::new();
    let mut lines_to_subs = HashMap::new();
    let mut line_refs: HashMap<_, PjRef<P>> = HashMap::new();
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
                    }
                    "assumption" => {
                        let linenum = attributes.iter().find(|x| x.name.local_name == "linenum").expect("assumption element has no linenum attribute");
                        last_linenum = linenum.value.clone();
                    }
                    "step" => {
                        let linenum = attributes.iter().find(|x| x.name.local_name == "linenum").expect("step element has no linenum attribute");
                        last_linenum = linenum.value.clone();
                        last_rule = "".into();
                        seen_premises = vec![];
                    }
                    _ => (),
                }
            }
            Ok(Characters(data)) => {
                contents += &data;
            }
            Ok(EndElement { name }) => {
                //println!("end {:?} {:?}", element_stack, contents);
                let element = element_stack.pop().unwrap();
                assert_eq!(name.local_name, element);
                let _attributes = attribute_stack.pop().unwrap();
                //println!("{:?} {:?}", element, attributes);
                macro_rules! on_current_proof {
                    ($n:ident, $x:expr) => {
                        match &*current_proof_id {
                            "0" => {
                                let $n = &mut proof;
                                let _ = $x;
                            }
                            r => {
                                let key = subproofs[r].clone();
                                proof.with_mut_subproof(&key, |sub| {
                                    let $n = sub;
                                    $x
                                });
                            }
                        }
                    };
                }
                match &*element {
                    "author" => metadata.author = Some(contents.clone()),
                    "hash" => metadata.hash = Some(contents.clone()),
                    "raw" => {
                        last_raw = contents.clone();
                    }
                    "assumption" => {
                        on_current_proof! { proof, { let p = proof.add_premise(parse!(&last_raw)); line_refs.insert(last_linenum.clone(), Coproduct::inject(p)).ok_or(format!("Multiple assumptions with line number {last_linenum}")) } }
                    }
                    "rule" => {
                        last_rule = contents.clone();
                    }
                    "premise" => {
                        seen_premises.push(contents.clone());
                    }
                    "step" => {
                        //println!("step {:?} {:?}", last_rule, seen_premises);
                        match &*last_rule {
                            "" => {}
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
                                on_current_proof! { proof, { let p = proof.add_step(just); line_refs.insert(last_linenum.clone(), Coproduct::inject(p)); } }
                            }
                        }
                    }
                    "goal" => {
                        if !last_raw.is_empty() {
                            metadata.goals.push(parse!(&last_raw));
                        }
                    }
                    _ => (),
                }
            }
            Ok(Whitespace(_)) => (),
            Ok(EndDocument) => break,
            Ok(_) => (),
            Err(e) => {
                return Err(format!("Error parsing xml document: {e:?}"));
            }
        }
    }
    Ok((proof, metadata))
}

pub fn xml_from_proof_and_metadata<P: Proof, W: Write>(prf: &P, meta: &ProofMetaData, out: W) -> xml::writer::Result<()> {
    use xml::writer::{
        EmitterConfig, EventWriter,
        XmlEvent::{self, *},
    };
    fn leaf_tag<W: Write>(ew: &mut EventWriter<W>, name: &str, val: &str) -> xml::writer::Result<()> {
        ew.write(XmlEvent::start_element(name))?;
        ew.write(Characters(val))?;
        ew.write(XmlEvent::end_element().name(name))?;
        Ok(())
    }
    let mut ew = EventWriter::new_with_config(out, EmitterConfig::new().perform_indent(true));
    ew.write(StartDocument { version: xml::common::XmlVersion::Version10, encoding: Some("UTF-8"), standalone: Some(false) })?;
    ew.write(XmlEvent::start_element("bram"))?;
    leaf_tag(&mut ew, "program", "Aris")?;
    leaf_tag(&mut ew, "version", "0.1.0")?; // TODO: autodetect from crate metadata?

    ew.write(XmlEvent::start_element("metadata"))?;
    if let Some(author) = &meta.author {
        leaf_tag(&mut ew, "author", author)?;
    }
    if let Some(hash) = &meta.hash {
        leaf_tag(&mut ew, "hash", hash)?;
    }
    ew.write(XmlEvent::end_element().name("metadata"))?;

    struct SerializationState<P: Proof> {
        queue: Vec<(usize, P::SubproofReference)>,
        linenum: usize,
        sproofid: usize,
        deps_map: HashMap<PjRef<P>, usize>,
        sdeps_map: HashMap<P::SubproofReference, usize>,
    }
    fn allocate_identifiers<P: Proof>(prf: &P::Subproof, state: &mut SerializationState<P>) {
        for prem in prf.premises() {
            state.deps_map.insert(Coproduct::inject(prem), state.linenum);
            state.linenum += 1;
        }
        for step in prf.lines() {
            use frunk_core::coproduct::Coproduct::{Inl, Inr};
            match step {
                Inl(jr) => {
                    state.deps_map.insert(Coproduct::inject(jr), state.linenum);
                    state.linenum += 1;
                }
                Inr(Inl(sr)) => {
                    state.sdeps_map.insert(sr.clone(), state.linenum);
                    // the java version seems to require that the linenum of a subproof aliases its first premise, so don't increment linenum here
                    let sub = prf.lookup_subproof(&sr).unwrap();
                    allocate_identifiers(&sub, state);
                }
                Inr(Inr(void)) => match void {},
            }
        }
    }

    fn aux<P: Proof, W: Write>(prf: &P::Subproof, proofid: usize, state: &mut SerializationState<P>, ew: &mut EventWriter<W>) -> xml::writer::Result<()> {
        ew.write(XmlEvent::start_element("proof").attr("id", &format!("{proofid}")))?;
        for prem in prf.premises() {
            ew.write(XmlEvent::start_element("assumption").attr("linenum", &format!("{}", state.deps_map[&Coproduct::inject(prem.clone())])))?;
            if let Some(expr) = prf.lookup_premise(&prem) {
                leaf_tag(ew, "raw", &format!("{expr}"))?;
            }
            ew.write(XmlEvent::end_element())?;
        }
        for step in prf.lines() {
            use frunk_core::coproduct::Coproduct::{Inl, Inr};
            match step {
                Inl(jr) => {
                    let just = prf.lookup_step(&jr).unwrap();
                    ew.write(XmlEvent::start_element("step").attr("linenum", &format!("{}", state.deps_map[&Coproduct::inject(jr.clone())])))?;
                    leaf_tag(ew, "raw", &format!("{}", just.0))?;
                    leaf_tag(ew, "rule", RuleM::to_serialized_name(just.1))?;
                    for dep in just.2 {
                        leaf_tag(ew, "premise", &format!("{}", state.deps_map[&dep]))?;
                    }
                    for sdep in just.3 {
                        leaf_tag(ew, "premise", &format!("{}", state.sdeps_map[&sdep]))?;
                    }
                    ew.write(XmlEvent::end_element().name("step"))?;
                }
                Inr(Inl(sr)) => {
                    ew.write(XmlEvent::start_element("step").attr("linenum", &format!("{}", state.sdeps_map[&sr])))?;
                    leaf_tag(ew, "rule", "SUBPROOF")?;
                    leaf_tag(ew, "premise", &format!("{}", state.sproofid))?;
                    ew.write(XmlEvent::end_element().name("step"))?;
                    state.queue.push((state.sproofid, sr.clone()));
                    state.sproofid += 1;
                }
                Inr(Inr(void)) => match void {},
            }
        }
        ew.write(XmlEvent::end_element().name("proof"))?;
        Ok(())
    }
    let mut state = SerializationState::<P> { queue: vec![], sproofid: 1, linenum: 0, deps_map: HashMap::new(), sdeps_map: HashMap::new() };
    allocate_identifiers(prf.top_level_proof(), &mut state);
    aux(prf.top_level_proof(), 0, &mut state, &mut ew)?;
    while let Some((id, sr)) = state.queue.pop() {
        if let Some(sub) = prf.lookup_subproof(&sr) {
            aux(&sub, id, &mut state, &mut ew)?;
        }
    }
    ew.write(XmlEvent::end_element().name("bram"))?;

    Ok(())
}

pub fn xml_from_proof_and_metadata_with_hash<P: Proof, W: Write>(prf: &P, meta: &ProofMetaData, out: W) -> xml::writer::Result<()> {
    use sha2::Digest;
    let mut meta = meta.clone();
    meta.hash = None;
    let mut payload = vec![];
    xml_from_proof_and_metadata(prf, &meta, &mut payload)?;
    let mut ctx = sha2::Sha256::new();
    ctx.input(&payload[..]);
    ctx.input(b"\n");
    if let Some(author) = &meta.author {
        ctx.input(author);
    }
    let hash = ctx.result();
    meta.hash = Some(base64::encode(&hash[..]));
    xml_from_proof_and_metadata(prf, &meta, out)
}

#[cfg(test)]
mod tests {
    use super::*;

    use crate::proofs::pooledproof::PooledProof;

    use frunk_core::Hlist;

    #[test]
    fn test_xml() {
        let data = &include_bytes!("../../../example-proofs/propositional_logic_arguments_for_proofs_ii_problem_10.bram")[..];
        type P = PooledProof<Hlist![Expr]>;
        let (prf, metadata) = proof_from_xml::<P, _>(data).unwrap();
        println!("{:?} {:?}\n{}", metadata.author, metadata.hash, prf);
        let mut reserialized = vec![];
        xml_from_proof_and_metadata_with_hash(&prf, &metadata, &mut reserialized).unwrap();
        println!("{}", String::from_utf8_lossy(&reserialized));
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
        type P = PooledProof<Hlist![Expr]>;
        let (prf, metadata) = proof_from_xml::<P, _>(&xml[..]).unwrap();
        println!("{:?} {:?}\n{}", metadata.author, metadata.hash, prf);
        let lines = prf.lines();
        let sub = prf.lookup_subproof(&lines[0].get::<<P as Proof>::SubproofReference, _>().unwrap().clone()).unwrap();
        assert_eq!(prf.lookup_premise(&sub.premises()[0]), Some(Expr::var("A")));
        let sub_lines = sub.lines();
        let Justification(e1, r1, d1, s1) = prf.lookup_pj(&Coproduct::inject(*sub_lines[0].get::<<P as Proof>::JustificationReference, _>().unwrap())).unwrap().get::<Justification<_, _, _>, _>().unwrap().clone();
        assert_eq!(e1, Expr::var("A"));
        assert_eq!(r1, RuleM::Reit);
        assert_eq!(d1.len(), 1);
        assert_eq!(s1.len(), 0);
        let Justification(e2, r2, d2, s2) = prf.lookup_pj(&Coproduct::inject(*lines[1].get::<<P as Proof>::JustificationReference, _>().unwrap())).unwrap().get::<Justification<_, _, _>, _>().unwrap().clone();
        assert_eq!(e2, Expr::implies(Expr::var("A"), Expr::var("A")));
        assert_eq!(r2, RuleM::ImpIntro);
        assert_eq!(d2.len(), 0);
        assert_eq!(s2.len(), 1);
    }

    #[test]
    fn test_xml3() {
        let xml = b"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n<bram>\n  <program>Aris</program>\n  <version>0.0.187</version>\n  <metadata>\n    <author>UNKNOWN</author>\n    <hash>aCDnd1IQS0y8QoTmgj7xeVpBG9o1A3m6tZWd0HXkwjg=</hash>\n  </metadata>\n  <proof id=\"0\">\n    <assumption linenum=\"0\">\n      <sen>p</sen>\n      <raw>p</raw>\n    </assumption>\n    <step linenum=\"1\">\n      <sen>p</sen>\n      <raw>p</raw>\n      <rule>REITERATION</rule>\n      <premise>0</premise>\n    </step>\n    <goal>\n      <sen/>\n      <raw/>\n    </goal>\n  </proof>\n</bram>\n";
        type P = PooledProof<Hlist![Expr]>;
        let (prf, metadata) = proof_from_xml::<P, _>(&xml[..]).unwrap();
        println!("{prf}");
        println!("{metadata:?}");
    }
}
