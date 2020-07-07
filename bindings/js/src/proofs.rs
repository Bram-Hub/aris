use crate::JsResult;

use aris::expression::Expr;
use aris::proofs::pooledproof::JustKey;
use aris::proofs::pooledproof::PremKey;
use aris::proofs::pooledproof::SubKey;
use aris::proofs::JSRef;
use aris::proofs::Justification;
use aris::proofs::PJRef;
use aris::proofs::PJSRef;
use aris::proofs::Proof as ProofT;

use std::collections::HashSet;

use frunk_core::Coprod;
use frunk_core::Hlist;
use serde_wasm_bindgen::from_value;
use serde_wasm_bindgen::to_value;
use wasm_bindgen::prelude::*;

type P = aris::proofs::pooledproof::PooledProof<Hlist![Expr]>;
type SP = aris::proofs::pooledproof::PooledSubproof<Hlist![Expr]>;

#[wasm_bindgen]
pub struct Proof(P);

#[wasm_bindgen]
impl Proof {
    pub fn new() -> Self {
        Self(P::new())
    }

    pub fn top_level_proof(&self) -> Subproof {
        Subproof(self.0.top_level_proof().clone())
    }

    pub fn lookup_premise(&self, r: JsValue) -> JsResult<JsValue> {
        let r: PremKey = from_value(r)?;
        let ret = self
            .0
            .lookup_premise(&r)
            .ok_or("aris: failed looking up premise")?;
        let ret = to_value(&ret)?;
        Ok(ret)
    }

    pub fn lookup_step(&self, r: JsValue) -> JsResult<JsValue> {
        let r: JustKey = from_value(r)?;
        let ret = self
            .0
            .lookup_step(&r)
            .ok_or("aris: failed looking up step")?;
        let ret = to_value(&ret)?;
        Ok(ret)
    }

    pub fn lookup_subproof(&self, r: JsValue) -> JsResult<Subproof> {
        let r: SubKey = from_value(r)?;
        let ret = self
            .0
            .lookup_subproof(&r)
            .ok_or("aris: failed looking up subproof")?;
        Ok(Subproof(ret))
    }

    pub fn with_premise(&mut self, r: JsValue, f: js_sys::Function) -> JsResult<JsValue> {
        let r: PremKey = from_value(r)?;
        let f = |expr: &mut Expr| -> JsResult<JsValue> {
            let expr = to_value(&expr)?;
            f.call1(&JsValue::NULL, &expr)
        };
        let ret = self.0.with_mut_premise(&r, f);
        let ret = ret.ok_or("aris: premise not found")?;
        ret
    }

    pub fn with_step(&mut self, r: JsValue, f: js_sys::Function) -> JsResult<JsValue> {
        let r: JustKey = from_value(r)?;
        let f = |just: &mut Justification<Expr, PJRef<P>, SubKey>| -> JsResult<JsValue> {
            let just = to_value(&just)?;
            f.call1(&JsValue::NULL, &just)
        };
        let ret = self.0.with_mut_step(&r, f);
        let ret = ret.ok_or("aris: step not found")?;
        ret
    }

    pub fn with_subproof(&mut self, r: JsValue, f: js_sys::Function) -> JsResult<JsValue> {
        let r: SubKey = from_value(r)?;
        let f = |subproof: &mut SP| -> JsResult<JsValue> {
            let subproof = Subproof(subproof.clone());
            let subproof = JsValue::from(subproof);
            f.call1(&JsValue::NULL, &subproof)
        };
        let ret = self.0.with_mut_subproof(&r, f);
        let ret = ret.ok_or("aris: step not found")?;
        ret
    }

    pub fn add_premise(&mut self, expr: JsValue) -> JsResult<JsValue> {
        let expr: Expr = from_value(expr)?;
        let ret = self.0.add_premise(expr);
        let ret = to_value(&ret)?;
        Ok(ret)
    }

    pub fn add_subproof(&mut self) -> JsResult<JsValue> {
        let ret = self.0.add_subproof();
        let ret = to_value(&ret)?;
        Ok(ret)
    }

    pub fn add_step(&mut self, just: JsValue) -> JsResult<JsValue> {
        let just: Justification<Expr, PJRef<P>, SubKey> = from_value(just)?;
        let ret = self.0.add_step(just);
        let ret = to_value(&ret)?;
        Ok(ret)
    }

    pub fn add_premise_relative(
        &mut self,
        expr: JsValue,
        r: JsValue,
        after: bool,
    ) -> JsResult<JsValue> {
        let expr: Expr = from_value(expr)?;
        let r: PremKey = from_value(r)?;
        let ret = self.0.add_premise_relative(expr, &r, after);
        let ret = to_value(&ret)?;
        Ok(ret)
    }

    pub fn add_subproof_relative(&mut self, r: JsValue, after: bool) -> JsResult<JsValue> {
        let r: JSRef<P> = from_value(r)?;
        let ret = self.0.add_subproof_relative(&r, after);
        let ret = to_value(&ret)?;
        Ok(ret)
    }

    pub fn add_step_relative(
        &mut self,
        just: JsValue,
        r: JsValue,
        after: bool,
    ) -> JsResult<JsValue> {
        let just: Justification<Expr, PJRef<P>, SubKey> = from_value(just)?;
        let r: JSRef<P> = from_value(r)?;
        let ret = self.0.add_step_relative(just, &r, after);
        let ret = to_value(&ret)?;
        Ok(ret)
    }

    pub fn remove_line(&mut self, r: JsValue) -> JsResult<()> {
        let r: PJRef<P> = from_value(r)?;
        self.0.remove_line(&r);
        Ok(())
    }

    pub fn remove_subproof(&mut self, r: JsValue) -> JsResult<()> {
        let r: SubKey = from_value(r)?;
        self.0.remove_subproof(&r);
        Ok(())
    }

    pub fn premises(&self) -> JsResult<Vec<JsValue>> {
        let ret = self
            .0
            .premises()
            .into_iter()
            .map(|prem_key: PremKey| to_value(&prem_key))
            .collect::<Result<Vec<JsValue>, _>>()?;
        Ok(ret)
    }

    pub fn lines(&self) -> JsResult<Vec<JsValue>> {
        let ret = self
            .0
            .lines()
            .into_iter()
            .map(|js_ref: JSRef<P>| to_value(&js_ref))
            .collect::<Result<Vec<JsValue>, _>>()?;
        Ok(ret)
    }

    pub fn parent_of_line(&self, r: JsValue) -> JsResult<JsValue> {
        let r: PJSRef<P> = from_value(r)?;
        let ret = self.0.parent_of_line(&r);
        let ret = ret.ok_or("aris: failed getting parent of line")?;
        let ret = to_value(&ret)?;
        Ok(ret)
    }

    pub fn verify_line(&self, r: JsValue) -> JsResult<()> {
        let r: PJRef<P> = from_value(r)?;
        self.0.verify_line(&r).map_err(|err| err.to_string())?;
        Ok(())
    }

    pub fn lookup_expr(&self, r: JsValue) -> JsResult<JsValue> {
        let r: PJRef<P> = from_value(r)?;
        let ret = self
            .0
            .lookup_expr(&r)
            .ok_or("aris: failed looking up expression")?;
        let ret = to_value(&ret)?;
        Ok(ret)
    }

    pub fn lookup_expr_or_die(&self, r: JsValue) -> JsResult<JsValue> {
        let r: PJRef<P> = from_value(r)?;
        let ret = self
            .0
            .lookup_expr_or_die(&r)
            .map_err(|err| err.to_string())?;
        let ret = to_value(&ret)?;
        Ok(ret)
    }

    pub fn lookup_premise_or_die(&self, r: JsValue) -> JsResult<JsValue> {
        let r: PremKey = from_value(r)?;
        let ret = self
            .0
            .lookup_premise_or_die(&r)
            .map_err(|err| err.to_string())?;
        let ret = to_value(&ret)?;
        Ok(ret)
    }

    pub fn lookup_justification_or_die(&self, r: JsValue) -> JsResult<JsValue> {
        let r: JustKey = from_value(r)?;
        let ret = self
            .0
            .lookup_justification_or_die(&r)
            .map_err(|err| err.to_string())?;
        let ret = to_value(&ret)?;
        Ok(ret)
    }

    pub fn lookup_pj(&self, r: JsValue) -> JsResult<JsValue> {
        let r: PJRef<P> = from_value(r)?;
        let ret = self
            .0
            .lookup_pj(&r)
            .ok_or("aris: failed looking up premise or justification")?;
        let ret = to_value(&ret)?;
        Ok(ret)
    }

    pub fn lookup_subproof_or_die(&self, r: JsValue) -> JsResult<Subproof> {
        let r: SubKey = from_value(r)?;
        let ret = self
            .0
            .lookup_subproof_or_die(&r)
            .map_err(|err| err.to_string())?;
        Ok(Subproof(ret))
    }

    pub fn direct_lines(&self) -> JsResult<Vec<JsValue>> {
        let ret = self
            .0
            .direct_lines()
            .into_iter()
            .map(|just_key: JustKey| to_value(&just_key))
            .collect::<Result<Vec<JsValue>, _>>()?;
        Ok(ret)
    }

    pub fn exprs(&self) -> JsResult<Vec<JsValue>> {
        let ret = self
            .0
            .exprs()
            .into_iter()
            .map(|pj_ref: PJRef<P>| to_value(&pj_ref))
            .collect::<Result<Vec<JsValue>, _>>()?;
        Ok(ret)
    }

    pub fn contained_justifications(&self, include_premises: bool) -> JsResult<JsValue> {
        let ret = self.0.contained_justifications(include_premises);
        let ret = to_value(&ret)?;
        Ok(ret)
    }

    pub fn transitive_dependencies(&self, line: JsValue) -> JsResult<JsValue> {
        let line: PJRef<P> = from_value(line)?;
        let ret = self.0.transitive_dependencies(line);
        let ret = to_value(&ret)?;
        Ok(ret)
    }

    pub fn depth_of_line(&self, r: JsValue) -> JsResult<usize> {
        let r: PJSRef<P> = from_value(r)?;
        let ret = self.0.depth_of_line(&r);
        Ok(ret)
    }

    pub fn possible_deps_for_line(&self, r: JsValue) -> JsResult<JsValue> {
        let r: PJRef<P> = from_value(r)?;
        let mut deps = HashSet::new();
        let mut sdeps = HashSet::new();
        self.0.possible_deps_for_line(&r, &mut deps, &mut sdeps);
        let ret = to_value(&(deps, sdeps))?;
        Ok(ret)
    }

    pub fn can_reference_dep(&self, r1: JsValue, r2: JsValue) -> JsResult<bool> {
        let r1: PJRef<P> = from_value(r1)?;
        let r2: Coprod![PJRef<P>, SubKey] = from_value(r2)?;
        let ret = self.0.can_reference_dep(&r1, &r2);
        Ok(ret)
    }
}

#[wasm_bindgen]
pub struct Subproof(SP);
