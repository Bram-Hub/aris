use crate::util::calculate_lineinfo;

use aris::proofs::js_to_pjs;
use aris::proofs::PJRef;
use aris::proofs::Proof;

use std::collections::HashMap;

use frunk_core::coproduct::Coproduct;

pub struct ProofUiData<P: Proof> {
    pub ref_to_line_depth: HashMap<PJRef<P>, (usize, usize)>,
    pub ref_to_input: HashMap<PJRef<P>, String>,
}

impl<P: Proof> ProofUiData<P> {
    pub fn from_proof(prf: &P) -> ProofUiData<P> {
        let mut ref_to_line_depth = HashMap::new();
        calculate_lineinfo::<P>(&mut ref_to_line_depth, prf.top_level_proof(), &mut 1, &mut 0);
        ProofUiData { ref_to_line_depth, ref_to_input: initialize_inputs(prf) }
    }
}

fn initialize_inputs<P: Proof>(prf: &P) -> HashMap<PJRef<P>, String> {
    fn aux<P: Proof>(p: &<P as Proof>::Subproof, out: &mut HashMap<PJRef<P>, String>) {
        use Coproduct::{Inl, Inr};
        for line in p.premises().into_iter().map(Coproduct::inject).chain(p.lines().into_iter().map(js_to_pjs::<P>)) {
            match line {
                Inl(pr) => {
                    if let Some(e) = p.lookup_expr(&Coproduct::inject(pr.clone())) {
                        out.insert(Coproduct::inject(pr.clone()), format!("{}", e));
                    }
                }
                Inr(Inl(jr)) => {
                    if let Some(e) = p.lookup_expr(&Coproduct::inject(jr.clone())) {
                        out.insert(Coproduct::inject(jr.clone()), format!("{}", e));
                    }
                }
                Inr(Inr(Inl(sr))) => aux::<P>(&p.lookup_subproof(&sr).unwrap(), out),
                Inr(Inr(Inr(void))) => match void {},
            }
        }
    }

    let mut out = HashMap::new();
    aux::<P>(prf.top_level_proof(), &mut out);
    out
}
