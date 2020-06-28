use super::*;

use aris::expression::Expr;
use aris::proofs::Proof;
use aris::proofs::lined_proof::LinedProof;
use aris::proofs::pooledproof::PooledProof;

use frunk::Hlist;

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_edu_rpi_aris_proof_RustProof_toString(env: JNIEnv, obj: JObject) -> jstring {
    with_thrown_errors(&env, |env| {
        let ptr: jni::sys::jlong = env.get_field(obj, "pointerToRustHeap", "J")?.j()?;
        let prf: &LinedProof<PooledProof<Hlist![Expr]>> = unsafe { &*(ptr as *mut LinedProof<PooledProof<Hlist![Expr]>>) };
        Ok(env.new_string(format!("{:?}", prf))?.into_inner())
    })
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_edu_rpi_aris_proof_RustProof_createProof(env: JNIEnv, _: JObject) -> jobject {
    with_thrown_errors(&env, |env| {
        let prf = Box::into_raw(Box::new(LinedProof::<PooledProof<Hlist![Expr]>>::new()));
        let jprf = env.new_object("edu/rpi/aris/proof/RustProof", "(J)V", &[JValue::from(prf as jni::sys::jlong)]);
        Ok(jprf?.into_inner())
    })
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_edu_rpi_aris_proof_RustProof_addLine(env: JNIEnv, this: JObject, index: jni::sys::jlong, is_assumption: jni::sys::jboolean, subproof_level: jni::sys::jlong) {
    with_thrown_errors(&env, |env| {
        let ptr: jni::sys::jlong = env.get_field(this, "pointerToRustHeap", "J")?.j()?;
        let self_: &mut LinedProof<PooledProof<Hlist![Expr]>> = unsafe { &mut*(ptr as *mut _) };
        self_.add_line(index as _, is_assumption != 0, subproof_level as _);
        Ok(())
    })
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_edu_rpi_aris_proof_RustProof_setExpressionString(env: JNIEnv, this: JObject, index: jni::sys::jlong, text: JObject) {
    with_thrown_errors(&env, |env| {
        let ptr: jni::sys::jlong = env.get_field(this, "pointerToRustHeap", "J")?.j()?;
        let self_: &mut LinedProof<PooledProof<Hlist![Expr]>> = unsafe { &mut*(ptr as *mut _) };
        self_.set_expr(index as _, jobject_to_string(env, text)?);
        Ok(())
    })
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_edu_rpi_aris_proof_RustProof_moveCursor(env: JNIEnv, this: JObject, index: jni::sys::jlong) {
    with_thrown_errors(&env, |env| {
        let ptr: jni::sys::jlong = env.get_field(this, "pointerToRustHeap", "J")?.j()?;
        let self_: &mut LinedProof<PooledProof<Hlist![Expr]>> = unsafe { &mut*(ptr as *mut _) };
        // Java ends up passing -1 here on startup for some reason, so ignore that.
        // Other invalid values should still trigger an assert in ZipperVec::move_cursor.
        if index != -1 {
            self_.move_cursor(index as _);
        }
        Ok(())
    })
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_edu_rpi_aris_proof_RustProof_fromXml(env: JNIEnv, _: JObject, jxml: JString) -> jobject {
    with_thrown_errors(&env, |env| {
        let xml = String::from(env.get_string(jxml)?);
        println!("{:?}", xml);
        if let Ok((prf, _)) = aris::proofs::xml_interop::proof_from_xml::<PooledProof<Hlist![Expr]>, _>(xml.as_bytes()) {
            println!("{}", prf);
            let prf = Box::into_raw(Box::new(LinedProof::<PooledProof<Hlist![Expr]>>::from_proof(prf)));
            let jprf = env.new_object("edu/rpi/aris/proof/RustProof", "(J)V", &[JValue::from(prf as jni::sys::jlong)])?;
            Ok(jprf.into_inner())
        } else {
            Ok(std::ptr::null_mut())
        }
    })
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_edu_rpi_aris_proof_RustProof_checkRuleAtLine(env: JNIEnv, this: JObject, linenum: jni::sys::jlong) -> jstring {
    with_thrown_errors(&env, |env| {
        let ptr: jni::sys::jlong = env.get_field(this, "pointerToRustHeap", "J")?.j()?;
        let self_: &mut LinedProof<PooledProof<Hlist![Expr]>> = unsafe { &mut*(ptr as *mut _) };
        println!("RustProof::checkRuleAtLine {:?}", self_);
        if let Some(line) = self_.lines.get(linenum as _) {
            if let Err(e) = self_.proof.verify_line(&line.reference) {
                Ok(env.new_string(&format!("{}", e))?.into_inner())
            } else {
                Ok(std::ptr::null_mut())
            }
        } else {
            Ok(env.new_string(&format!("Failed to dereference line {} in {:?}", linenum, self_))?.into_inner())
        }
    })
}
