use crate::with_null_options;

pub use aris::expression::*;

use std::ffi::CStr;

#[no_mangle] pub extern "C" fn aris_vec_expr_index(x: &Vec<Expr>, i: usize) -> Expr { x[i].clone() }
#[no_mangle] pub extern "C" fn aris_vec_string_index(x: &Vec<String>, i: usize) -> String { x[i].clone() }
#[no_mangle] pub extern "C" fn aris_box_expr_deref(x: &Box<Expr>) -> Expr { *x.clone() }

#[no_mangle]
pub extern "C" fn aris_expr_parse(e: *const i8) -> *mut Expr {
    with_null_options(|| {
        let s = unsafe { CStr::from_ptr(e) }.to_string_lossy().into_owned();
        aris::parser::parse(&s)
    })
}
