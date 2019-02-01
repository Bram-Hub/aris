use super::*;

pub mod java_rule;
pub mod java_expression;
use java_expression::*;

use jni::JNIEnv;
use jni::strings::JavaStr;
use jni::objects::{JClass, JString, JValue, JObject};
use jni::sys::{jobject, jstring, jarray};

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

pub fn with_thrown_errors<A, F: FnOnce(&JNIEnv) -> jni::errors::Result<A>>(env: &JNIEnv, f: F) -> A {
    f(env).unwrap_or_else(|e| { let _ = env.throw(&*format!("{:?}", e)); unsafe { std::mem::zeroed() } })
}

