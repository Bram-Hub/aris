use wasm_bindgen::prelude::*;

pub mod expression;
pub mod parser;
pub mod proofs;
pub mod rules;

pub type JsResult<T> = Result<T, JsValue>;
