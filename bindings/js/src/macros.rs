use wasm_bindgen::prelude::*;

#[wasm_bindgen]
pub fn expand(s: &str) -> String {
    aris::macros::expand(s)
}
