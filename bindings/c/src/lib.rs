pub mod c_expression;

pub fn with_null_options<A, F: FnOnce() -> Option<A>>(f: F) -> *mut A {
    f().map(|e| Box::into_raw(Box::new(e)))
        .unwrap_or(std::ptr::null_mut())
}
