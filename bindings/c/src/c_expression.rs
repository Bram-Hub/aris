use crate::with_null_options;

pub use aris::expr::Expr;

use std::ffi::CStr;

#[no_mangle]
pub extern "C" fn aris_expr_parse(e: *const i8) -> *mut Expr {
    with_null_options(|| {
        let s = unsafe { CStr::from_ptr(e) }.to_string_lossy().into_owned();
        aris::parser::parse(&s)
    })
}
