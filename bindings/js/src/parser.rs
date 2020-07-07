use crate::JsResult;

use serde_wasm_bindgen::to_value;
use wasm_bindgen::prelude::*;

#[wasm_bindgen]
pub fn parse(input: &str) -> JsResult<JsValue> {
    let ret = aris::parser::parse(input).ok_or("aris: parse error")?;
    let ret = to_value(&ret)?;
    Ok(ret)
}

#[wasm_bindgen]
pub fn prettify_expr(s: &str) -> String {
    aris::parser::prettify_expr(s)
}
