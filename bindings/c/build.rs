use std::env;

fn write_headers() {
    let crate_dir = env::var("CARGO_MANIFEST_DIR").unwrap();

    cbindgen::Builder::new().with_crate(crate_dir).with_language(cbindgen::Language::C).with_std_types(true).with_parse_deps(true).with_parse_include(&["aris"]).generate().expect("Unable to generate bindings").write_to_file("aris.h");
}

fn main() {
    // https://github.com/rust-lang/rls-vscode/issues/586
    #[cfg(not(feature = "workaround_rls_vscode_bug_586"))]
    write_headers();
}
