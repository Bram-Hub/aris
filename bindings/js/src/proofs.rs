use crate::JsResult;

use aris::expr::Expr;
use aris::proofs::Proof as ProofT;

use std::collections::HashSet;

use frunk_core::coproduct::Coproduct;
use frunk_core::hlist;
use frunk_core::Coprod;
use frunk_core::Hlist;
use serde_wasm_bindgen::from_value;
use serde_wasm_bindgen::to_value;
use wasm_bindgen::prelude::*;

type P = aris::proofs::pooledproof::PooledProof<Hlist![Expr]>;
type SP = aris::proofs::pooledproof::PooledSubproof<Hlist![Expr]>;
type JustificationInner = aris::proofs::Justification<Expr, aris::proofs::PJRef<P>, aris::proofs::pooledproof::SubKey>;

#[wasm_bindgen]
pub struct Proof(P);

//#[wasm_bindgen]
impl Proof {
    //#[wasm_bindgen(constructor)]
    pub fn new() -> Self {
        Self(P::new())
    }

    pub fn top_level_proof(&self) -> Subproof {
        Subproof(self.0.top_level_proof().clone())
    }

    pub fn lookup_premise(&self, r: PRef) -> JsResult<JsValue> {
        let ret = self.0.lookup_premise(&r.0).ok_or("aris: failed looking up premise")?;
        let ret = to_value(&ret)?;
        Ok(ret)
    }

    pub fn lookup_step(&self, r: JRef) -> JsResult<Justification> {
        let ret = self.0.lookup_step(&r.0).ok_or("aris: failed looking up step")?;
        Ok(Justification(ret))
    }

    pub fn lookup_subproof(&self, r: SRef) -> JsResult<Subproof> {
        let ret = self.0.lookup_subproof(&r.0).ok_or("aris: failed looking up subproof")?;
        Ok(Subproof(ret))
    }

    pub fn with_premise(&mut self, r: PRef, f: js_sys::Function) -> JsResult<JsValue> {
        let f = |expr: &mut Expr| -> JsResult<JsValue> {
            let expr = to_value(&expr)?;
            f.call1(&JsValue::NULL, &expr)
        };
        let ret = self.0.with_mut_premise(&r.0, f);
        let ret = ret.ok_or("aris: premise not found")?;
        ret
    }

    pub fn with_step(&mut self, r: JRef, f: js_sys::Function) -> JsResult<JsValue> {
        let f = |just: &mut JustificationInner| -> JsResult<JsValue> {
            let just = JsValue::from(Justification(just.clone()));
            f.call1(&JsValue::NULL, &just)
        };
        let ret = self.0.with_mut_step(&r.0, f);
        let ret = ret.ok_or("aris: step not found")?;
        ret
    }

    pub fn with_subproof(&mut self, r: SRef, f: js_sys::Function) -> JsResult<JsValue> {
        let f = |subproof: &mut SP| -> JsResult<JsValue> {
            let subproof = Subproof(subproof.clone());
            let subproof = JsValue::from(subproof);
            f.call1(&JsValue::NULL, &subproof)
        };
        let ret = self.0.with_mut_subproof(&r.0, f);
        let ret = ret.ok_or("aris: step not found")?;
        ret
    }

    pub fn add_premise(&mut self, expr: JsValue) -> JsResult<PRef> {
        let expr: Expr = from_value(expr)?;
        let ret = self.0.add_premise(expr);
        Ok(PRef(ret))
    }

    pub fn add_subproof(&mut self) -> SRef {
        SRef(self.0.add_subproof())
    }

    pub fn add_step(&mut self, just: Justification) -> JRef {
        JRef(self.0.add_step(just.0))
    }

    pub fn add_premise_relative(&mut self, expr: JsValue, r: PRef, after: bool) -> JsResult<PRef> {
        let expr: Expr = from_value(expr)?;
        let ret = self.0.add_premise_relative(expr, &r.0, after);
        Ok(PRef(ret))
    }

    pub fn add_subproof_relative(&mut self, r: JSRef, after: bool) -> SRef {
        SRef(self.0.add_subproof_relative(&r.0, after))
    }

    pub fn add_step_relative(&mut self, just: Justification, r: JSRef, after: bool) -> JRef {
        JRef(self.0.add_step_relative(just.0, &r.0, after))
    }

    pub fn remove_line(&mut self, r: PJRef) {
        self.0.remove_line(&r.0);
    }

    pub fn remove_subproof(&mut self, r: SRef) {
        self.0.remove_subproof(&r.0);
    }

    pub fn premises(&self) -> Vec<JsValue> {
        self.0.premises().into_iter().map(|p| JsValue::from(PRef(p))).collect()
    }

    pub fn lines(&self) -> Vec<JsValue> {
        self.0.lines().into_iter().map(|js| JsValue::from(JSRef(js))).collect()
    }

    pub fn parent_of_line(&self, r: PJSRef) -> JsResult<SRef> {
        let ret = self.0.parent_of_line(&r.0);
        let ret = ret.ok_or("aris: failed getting parent of line")?;
        Ok(SRef(ret))
    }

    pub fn verify_line(&self, r: PJRef) -> JsResult<()> {
        self.0.verify_line(&r.0).map_err(|err| err.to_string())?;
        Ok(())
    }

    pub fn lookup_expr(&self, r: PJRef) -> JsResult<JsValue> {
        let ret = self.0.lookup_expr(&r.0).ok_or("aris: failed looking up expression")?;
        let ret = to_value(&ret)?;
        Ok(ret)
    }

    pub fn lookup_expr_or_die(&self, r: PJRef) -> JsResult<JsValue> {
        let ret = self.0.lookup_expr_or_die(&r.0).map_err(|err| err.to_string())?;
        let ret = to_value(&ret)?;
        Ok(ret)
    }

    pub fn lookup_premise_or_die(&self, r: PRef) -> JsResult<JsValue> {
        let ret = self.0.lookup_premise_or_die(&r.0).map_err(|err| err.to_string())?;
        let ret = to_value(&ret)?;
        Ok(ret)
    }

    pub fn lookup_justification_or_die(&self, r: JRef) -> JsResult<Justification> {
        let ret = self.0.lookup_justification_or_die(&r.0).map_err(|err| err.to_string())?;
        Ok(Justification(ret))
    }

    pub fn lookup_pj(&self, r: PJRef) -> JsResult<JsValue> {
        let ret = self.0.lookup_pj(&r.0).ok_or("aris: failed looking up premise or justification")?;
        ret.fold(hlist![|expr| -> JsResult<JsValue> { Ok(to_value(&expr)?) }, |just| -> JsResult<JsValue> { Ok(JsValue::from(Justification(just))) },])
    }

    pub fn lookup_subproof_or_die(&self, r: SRef) -> JsResult<Subproof> {
        let ret = self.0.lookup_subproof_or_die(&r.0).map_err(|err| err.to_string())?;
        Ok(Subproof(ret))
    }

    pub fn direct_lines(&self) -> Vec<JsValue> {
        self.0.direct_lines().into_iter().map(|j| JsValue::from(JRef(j))).collect()
    }

    pub fn exprs(&self) -> Vec<JsValue> {
        self.0.exprs().into_iter().map(|pj| JsValue::from(PJRef(pj))).collect()
    }

    pub fn contained_justifications(&self, include_premises: bool) -> Vec<JsValue> {
        self.0.contained_justifications(include_premises).into_iter().map(|pj| JsValue::from(PJRef(pj))).collect::<Vec<JsValue>>()
    }

    pub fn transitive_dependencies(&self, line: PJRef) -> Vec<JsValue> {
        self.0.transitive_dependencies(line.0).into_iter().map(|pj| JsValue::from(PJRef(pj))).collect::<Vec<JsValue>>()
    }

    pub fn depth_of_line(&self, r: PJSRef) -> usize {
        self.0.depth_of_line(&r.0)
    }

    pub fn possible_deps_for_line(&self, r: PJRef) -> Vec<JsValue> {
        let mut deps = HashSet::new();
        let mut sdeps = HashSet::new();
        self.0.possible_deps_for_line(&r.0, &mut deps, &mut sdeps);
        deps.into_iter().map(|pj| JsValue::from(PJRef(pj))).collect::<Vec<JsValue>>()
    }

    pub fn possible_sdeps_for_line(&self, r: PJRef) -> Vec<JsValue> {
        let mut deps = HashSet::new();
        let mut sdeps = HashSet::new();
        self.0.possible_deps_for_line(&r.0, &mut deps, &mut sdeps);
        sdeps.into_iter().map(|s| JsValue::from(SRef(s))).collect::<Vec<JsValue>>()
    }

    pub fn can_reference_dep(&self, r1: PJRef, r2: PJSRef) -> JsResult<bool> {
        type PJRefInner = aris::proofs::PJRef<P>;
        type PJSRefAlt = Coprod![PJRefInner, aris::proofs::pooledproof::SubKey];
        let r2 = r2.0.fold(hlist![
            |p| {
                let pj: PJRefInner = Coproduct::inject(p);
                let pjs: PJSRefAlt = Coproduct::inject(pj);
                pjs
            },
            |j| {
                let pj: PJRefInner = Coproduct::inject(j);
                let pjs: PJSRefAlt = Coproduct::inject(pj);
                pjs
            },
            |s| {
                let pjs: PJSRefAlt = Coproduct::inject(s);
                pjs
            },
        ]);
        let ret = self.0.can_reference_dep(&r1.0, &r2);
        Ok(ret)
    }
}

#[wasm_bindgen]
pub struct Subproof(SP);

#[wasm_bindgen]
pub struct PRef(aris::proofs::pooledproof::PremKey);

#[wasm_bindgen]
pub struct JRef(aris::proofs::pooledproof::JustKey);

#[wasm_bindgen]
pub struct SRef(aris::proofs::pooledproof::SubKey);

#[wasm_bindgen]
pub struct PJRef(aris::proofs::PJRef<P>);

#[wasm_bindgen]
pub struct JSRef(aris::proofs::JSRef<P>);

#[wasm_bindgen]
pub struct PJSRef(aris::proofs::PJSRef<P>);

#[wasm_bindgen]
pub struct Justification(JustificationInner);

/*
#[wasm_bindgen]
impl Justification {
    #[wasm_bindgen(constructor)]
    pub fn new(
        expr: JsValue,
        rule: Rule,
        deps: Vec<JsValue>,
        sdeps: Vec<JsValue>,
    ) -> JsResult<Justification> {
        let expr: Expr = match from_value::<String>(expr.clone()) {
            Ok(s) => crate::parser::parse_helper(&s)?,
            Err(_) => from_value(expr)?,
        };
        let deps = deps
            .into_iter()
            .map(|dep| dep.dyn_into::<PJRef>())
            .collect::<JsResult<Vec<PJRef>>>()?;
        let sdeps = sdeps
            .into_iter()
            .map(|dep| dep.dyn_into::<SRef>())
            .collect::<JsResult<Vec<SRef>>>()?;
        Ok(Justification(aris::proofs::Justification(
            expr, rule, deps, sdeps,
        )))
    }
}
*/
