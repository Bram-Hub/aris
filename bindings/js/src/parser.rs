use crate::JsResult;

use aris::expr::Expr;

use serde_wasm_bindgen::to_value;
use wasm_bindgen::prelude::*;

pub fn parse_helper(input: &str) -> JsResult<Expr> {
    let ret = aris::parser::parse(input).ok_or("aris: parse error")?;
    Ok(ret)
}

#[wasm_bindgen]
pub fn parse(input: &str) -> JsResult<JsValue> {
    let ret = parse_helper(input)?;
    let ret = to_value(&ret)?;
    Ok(ret)
}
