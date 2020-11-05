#![warn(missing_docs)]

#[macro_use]
extern crate lazy_static;

mod equivs;
pub mod expr;
pub mod macros;
pub mod parser;
pub mod proofs;
mod rewrite_rules;
pub mod rules;
mod zipper_vec;
