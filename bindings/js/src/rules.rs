use crate::JsResult;

use aris::rules::RuleM;

use serde_wasm_bindgen::to_value;
use wasm_bindgen::prelude::*;

#[wasm_bindgen]
pub fn rule_names() -> JsResult<Vec<JsValue>> {
    let ret = aris::rules::RuleM::ALL_SERIALIZED_NAMES.iter().map(to_value).collect::<Result<Vec<JsValue>, _>>()?;
    Ok(ret)
}

#[wasm_bindgen]
pub struct Rule(aris::rules::Rule);

#[wasm_bindgen]
impl Rule {
    #[wasm_bindgen(constructor)]
    pub fn new(name: &str) -> JsResult<Rule> {
        Ok(Rule(RuleM::from_serialized_name(name).ok_or("aris: invalid rule name")?))
    }
}
