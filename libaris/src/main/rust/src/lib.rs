#[macro_use] extern crate frunk;
#[macro_use] extern crate nom;
#[macro_use] extern crate lazy_static;
extern crate jni;
extern crate petgraph;
extern crate xml;

pub mod zipper_vec;
use zipper_vec::*;
pub mod parser;
pub mod expression;
use expression::*;
pub mod proofs;
use proofs::*;
pub mod rules;
use rules::*;
pub mod rewrite_rules;
use rewrite_rules::*;
pub mod equivalences;
use equivalences::*;

pub mod java_interop {
    use super::*;

    pub mod java_rule;
    pub mod java_expression;
    use java_expression::*;
    pub mod java_proof;

    use jni::JNIEnv;
    use jni::strings::JavaStr;
    use jni::objects::{JClass, JString, JValue, JObject};
    use jni::sys::{jobject, jstring, jarray};

    use std::panic::{catch_unwind, UnwindSafe};

    fn jobject_to_string(env: &JNIEnv, obj: JObject) -> jni::errors::Result<String> {
        Ok(String::from(env.get_string(JString::from(obj))?))
    }

    pub fn java_iterator_for_each<F: FnMut(JObject) -> jni::errors::Result<()>>(env: &JNIEnv, iterable: JObject, mut f: F) -> jni::errors::Result<()> {
        let iter = env.call_method(iterable, "iterator", "()Ljava/util/Iterator;", &[])?.l()?;
        while env.call_method(iter, "hasNext", "()Z", &[])?.z()? {
            let obj = env.call_method(iter, "next", "()Ljava/lang/Object;", &[])?.l()?;
            f(obj)?
        }
        Ok(())
    }

    pub fn with_thrown_errors<A, F: FnOnce(&JNIEnv) -> jni::errors::Result<A> + UnwindSafe>(env: &JNIEnv, f: F) -> A {
        use std::panic::{take_hook, set_hook, PanicInfo, Location};
        let old_hook = take_hook();
        let (tx, rx) = std::sync::mpsc::channel::<String>();
        let mtx = std::sync::Mutex::new(tx);
        set_hook(Box::new(move |info: &PanicInfo| {
            let mut msg = format!("Panic at {:?}", info.location());
            if let Some(e) = info.payload().downcast_ref::<&str>() {
                msg += &*format!(": {:?}", e);
            }
            if let Some(e) = info.payload().downcast_ref::<String>() {
                msg += &*format!(": {:?}", e);
            }
            if let Ok(tx) = mtx.lock() {
                let _ = tx.send(msg);
            }
        }));
        let ret = catch_unwind(|| {
            f(env).unwrap_or_else(|e| { let _ = env.throw_new("java/lang/RuntimeException", &*format!("{:?}", e)); unsafe { std::mem::zeroed() } }) // handle Result::Err
        }).unwrap_or_else(|_| {
            // handle panic
            let msg = rx.recv().unwrap_or(format!("with_thrown_errors: recv failed"));
            let _ = env.throw_new("java/lang/RuntimeException", &*msg);
            unsafe { std::mem::zeroed() }
        });
        set_hook(old_hook);
        ret
    }
}
use java_interop::*;

pub mod c_interop {
    use super::*;
    pub mod c_expression;

    pub fn with_null_options<A, F: FnOnce() -> Option<A>>(f: F) -> *mut A {
        f().map(|e| Box::into_raw(Box::new(e))).unwrap_or(std::ptr::null_mut())
    }
}
