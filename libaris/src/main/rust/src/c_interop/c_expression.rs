use super::*;
use std::ffi::{CStr/*, CString*/};

#[no_mangle] pub extern "C" fn aris_vec_expr_index(x: &Vec<Expr>, i: usize) -> Expr { x[i].clone() }
#[no_mangle] pub extern "C" fn aris_vec_string_index(x: &Vec<String>, i: usize) -> String { x[i].clone() }
#[no_mangle] pub extern "C" fn aris_box_expr_deref(x: &Box<Expr>) -> Expr { *x.clone() }

#[no_mangle]
pub extern "C" fn aris_expr_parse(e: *const i8) -> *mut Expr {
    with_null_options(|| {
        let mut s = unsafe { CStr::from_ptr(e) }.to_string_lossy().into_owned();
        s.push('\n');
        if let Ok((_, expr)) = parser::main(&s) {
            Some(expr)
        } else {
            None
        }
    })
}
