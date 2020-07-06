#[macro_use] extern crate frunk_core;
#[macro_use] extern crate nom;
#[macro_use] extern crate lazy_static;

extern crate base64;
extern crate failure;
extern crate petgraph;
extern crate sha2;
extern crate varisat;
extern crate xml;
extern crate itertools;
extern crate strum;
#[macro_use]
extern crate strum_macros;

pub mod zipper_vec;
use zipper_vec::*;

/// aris::parser parses infix logical expressions into the AST type aris::expression::Expr.
pub mod parser;

/// aris::expression defines the ASTs for logical expressions, and contains utilities for constructing and inspecting them.
pub mod expression;
use expression::*;

/// aris::proofs contains various datastructures for representing natural deduction style proofs.
pub mod proofs;
use proofs::*;

/// aris::rules contains implementations of various logical inference rules for checking individual steps of a proof.
pub mod rules;
use rules::*;

/// aris::rewrite_rules implements a fixpoint engine for applying transformations to a formula in a loop until they stop applying.
pub mod rewrite_rules;
use rewrite_rules::*;

/// aris::equivalences contains patterns for rewriting equivalences (a specific type of rule).
pub mod equivalences;
use equivalences::*;

/// aris::combinatorics contains utilities for calculating permutations and combinations of elements
pub mod combinatorics;
use combinatorics::*;

pub mod solver_integration {
    pub mod solver;
}
