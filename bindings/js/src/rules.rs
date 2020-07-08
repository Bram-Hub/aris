use crate::JsResult;

use aris::rules::RuleM;

use serde_wasm_bindgen::to_value;
use wasm_bindgen::prelude::*;

#[wasm_bindgen]
pub fn all_rule_names() -> JsResult<Vec<JsValue>> {
    let ret = aris::rules::RuleM::ALL_SERIALIZED_NAMES
        .iter()
        .map(to_value)
        .collect::<Result<Vec<JsValue>, _>>()?;
    Ok(ret)
}

#[wasm_bindgen]
pub fn rule_from_name(name: &str) -> JsResult<JsValue> {
    let ret = RuleM::from_serialized_name(name);
    let ret = to_value(&ret)?;
    Ok(ret)
}
