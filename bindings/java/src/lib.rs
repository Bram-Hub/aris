pub mod java_expression;
pub mod java_rule;
use java_expression::*;
pub mod java_proof;

use jni::objects::{JClass, JObject, JString, JValue};
use jni::strings::JavaStr;
use jni::sys::{jarray, jobject, jstring};
use jni::JNIEnv;

use std::panic::{catch_unwind, UnwindSafe};

fn jobject_to_string(env: &JNIEnv, obj: JObject) -> jni::errors::Result<String> {
    Ok(String::from(env.get_string(JString::from(obj))?))
}

/// Calls a Rust function on each Java object of a Java Iterator
pub fn java_iterator_for_each<F: FnMut(JObject) -> jni::errors::Result<()>>(env: &JNIEnv, iterable: JObject, mut f: F) -> jni::errors::Result<()> {
    let iter = env.call_method(iterable, "iterator", "()Ljava/util/Iterator;", &[])?.l()?;
    while env.call_method(iter, "hasNext", "()Z", &[])?.z()? {
        let obj = env.call_method(iter, "next", "()Ljava/lang/Object;", &[])?.l()?;
        f(obj)?
    }
    Ok(())
}

/// Wraps a Rust function, converting both Result::Err and panic into instances of Java's RuntimeException.
/// Please use this on all native methods, otherwise a Rust panic/unwrap will crash the Java UI instead of popping a dialog box with the message.
#[allow(deprecated)]
pub fn with_thrown_errors<A, F: FnOnce(&JNIEnv) -> jni::errors::Result<A> + UnwindSafe>(env: &JNIEnv, f: F) -> A {
    use std::panic::{set_hook, take_hook, PanicInfo};
    let old_hook = take_hook();
    let (tx, rx) = std::sync::mpsc::channel::<String>();
    let mtx = std::sync::Mutex::new(tx);
    set_hook(Box::new(move |info: &PanicInfo| {
        let mut msg = format!("Panic at {:?}", info.location());
        if let Some(e) = info.payload().downcast_ref::<&str>() {
            msg += &*format!(": {e:?}");
        }
        if let Some(e) = info.payload().downcast_ref::<String>() {
            msg += &*format!(": {e:?}");
        }
        if let Ok(tx) = mtx.lock() {
            let _ = tx.send(msg);
        }
    }));
    let ret = catch_unwind(|| {
        f(env).unwrap_or_else(|e| {
            let _ = env.throw_new("java/lang/RuntimeException", &*format!("{e:?}"));
            unsafe { std::mem::zeroed() }
        }) // handle Result::Err
    })
    .unwrap_or_else(|_| {
        // handle panic
        let msg = rx.recv().unwrap_or_else(|_| "with_thrown_errors: recv failed".to_string());
        let _ = env.throw_new("java/lang/RuntimeException", msg);
        unsafe { std::mem::zeroed() }
    });
    set_hook(old_hook);
    ret
}
