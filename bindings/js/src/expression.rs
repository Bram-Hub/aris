use crate::JsResult;

use aris::expr::Expr;

use std::collections::HashSet;

use serde_wasm_bindgen::from_value;
use serde_wasm_bindgen::to_value;
use wasm_bindgen::prelude::*;

#[wasm_bindgen]
pub fn freevars(expr: JsValue) -> JsResult<JsValue> {
    let expr: Expr = from_value(expr)?;
    let vars = aris::expr::freevars(&expr);
    let vars = to_value(&vars)?;
    Ok(vars)
}

#[wasm_bindgen]
pub fn gensym(orig: &str, avoid: JsValue) -> JsResult<String> {
    let avoid: HashSet<String> = from_value(avoid)?;
    Ok(aris::expr::gensym(orig, &avoid))
}

#[wasm_bindgen]
pub fn subst(expr: JsValue, to_replace: &str, replacement: JsValue) -> JsResult<JsValue> {
    let expr: Expr = from_value(expr)?;
    let replacement: Expr = from_value(replacement)?;
    let ret = aris::expr::subst(&expr, to_replace, replacement);
    let ret = to_value(&ret)?;
    Ok(ret)
}
