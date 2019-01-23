#[macro_use] extern crate frunk;
#[macro_use] extern crate nom;
extern crate jni;

pub mod parser;
pub mod expression;
use expression::*;
pub mod proofs;
use proofs::*;
pub mod rules;
use rules::*;

use jni::JNIEnv;
use jni::strings::JavaStr;
use jni::objects::{JClass, JString, JValue, JObject};
use jni::sys::{jobject, jstring};

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

#[cfg(test)]
mod tests {
}
