#[macro_use] extern crate frunk;
#[macro_use] extern crate nom;
#[macro_use] extern crate lazy_static;

#[cfg(feature="java")]
extern crate jni;
#[cfg(feature="js")]
extern crate yew;
#[cfg(feature="js")]
extern crate wasm_bindgen;
#[cfg(feature="js")]
extern crate wee_alloc;

extern crate petgraph;
extern crate xml;
extern crate varisat;
extern crate failure;

pub mod zipper_vec;
use zipper_vec::*;

/// libaris::parser parses infix logical expressions into the AST type libaris::expression::Expr.
pub mod parser;

/// libaris::expression defines the ASTs for logical expressions, and contains utilities for constructing and inspecting them.
pub mod expression;
use expression::*;

/// libaris::proofs contains various datastructures for representing natural deduction style proofs.
pub mod proofs;
use proofs::*;

/// libaris::rules contains implementations of various logical inference rules for checking individual steps of a proof.
pub mod rules;
use rules::*;

/// libaris::rewrite_rules implements a fixpoint engine for applying transformations to a formula in a loop until they stop applying.
pub mod rewrite_rules;
use rewrite_rules::*;

/// libaris::equivalences contains patterns for rewriting equivalences (a specific type of rule).
pub mod equivalences;
use equivalences::*;

/// libaris::java_interop contains native methods for various objects in the Java version of Aris, as well as convenience methods for dealing with Java objects.
#[cfg(feature="java")]
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
    pub fn with_thrown_errors<A, F: FnOnce(&JNIEnv) -> jni::errors::Result<A> + UnwindSafe>(env: &JNIEnv, f: F) -> A {
        use std::panic::{take_hook, set_hook, PanicInfo};
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
#[cfg(feature="java")]
use java_interop::*;

pub mod c_interop {
    use super::*;
    pub mod c_expression;

    pub fn with_null_options<A, F: FnOnce() -> Option<A>>(f: F) -> *mut A {
        f().map(|e| Box::into_raw(Box::new(e))).unwrap_or(std::ptr::null_mut())
    }
}

#[cfg(feature="js")]
pub mod js_interop {
    use super::*;
    pub mod js_ui;
    use wasm_bindgen::prelude::*;

    #[wasm_bindgen]
    pub fn run_app() -> Result<(), JsValue> {
        #[global_allocator]
        static ALLOC: wee_alloc::WeeAlloc = wee_alloc::WeeAlloc::INIT;

        yew::start_app::<js_ui::App>();
        Ok(())
    }
}

pub mod solver_integration {
    pub mod solver;
}