fn main() {
    // generate_docstrings_from_macros is an internal detail of
    // define_rewrite_rule, to make it only generate documentation on versions of rust
    // in which it is legal for a macro invocation to be captured by an expr metavariable
    // https://blog.rust-lang.org/2019/12/19/Rust-1.40.0.html#macro-and-attribute-improvements
    if version_check::is_min_version("1.40.0").unwrap_or(false) {
        println!("cargo:rustc-cfg=generate_docstrings_from_macros");
    }
}
