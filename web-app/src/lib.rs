#![recursion_limit = "1024"]

mod box_chars;
mod components;
mod proof_ui_data;
mod util;

use wasm_bindgen::prelude::*;

#[wasm_bindgen(start)]
pub fn run_app() -> Result<(), JsValue> {
    yew::Renderer::<components::app::App>::new().render();
    Ok(())
}
