extern crate cbindgen;
extern crate version_check;

use std::env;

fn main() {
    // generate_docstrings_from_macros is an internal detail of 
    // define_rewrite_rule, to make it only generate documentation on versions of rust 
    // in which it is legal for a macro invocation to be captured by an expr metavariable
    // https://blog.rust-lang.org/2019/12/19/Rust-1.40.0.html#macro-and-attribute-improvements
    if version_check::is_min_version("1.40.0").unwrap_or(false) {
        println!("cargo:rustc-cfg=generate_docstrings_from_macros");
    }

    let crate_dir = env::var("CARGO_MANIFEST_DIR").unwrap();

    cbindgen::Builder::new()
        .with_crate(crate_dir)
        .with_language(cbindgen::Language::C)
        .with_std_types(true)
        .generate()
        .expect("Unable to generate bindings")
        .write_to_file("aris.h");
}
