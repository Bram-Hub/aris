use super::*;

use aris::rules::Rule;
use aris::rules::RuleClassification;
use aris::rules::RuleM;
use aris::rules::RuleT;

use frunk_core::coproduct::Coproduct;

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_edu_rpi_aris_rules_Rule_fromRule(env: JNIEnv, _: JObject, rule: JObject) -> jobject {
    with_thrown_errors(&env, |env| {
        let cls = env.call_method(rule, "getClass", "()Ljava/lang/Class;", &[])?.l()?;
        let classname = String::from(env.get_string(JString::from(env.call_method(cls, "getName", "()Ljava/lang/String;", &[])?.l()?))?);
        //println!("Rule.fromRule, rule class: {:?}", classname);
        if classname != "edu.rpi.aris.rules.RuleList" {
            return Err(jni::errors::Error::from_kind(jni::errors::ErrorKind::Msg(format!("Rule::fromRule: unknown class {}", classname))));
        }

        let name = String::from(env.get_string(JString::from(env.call_method(rule, "name", "()Ljava/lang/String;", &[])?.l()?))?);
        println!("Rule.fromRule, rule enum name: {:?}", name);
        let rule = match RuleM::from_serialized_name(&name) {
            Some(rule) => rule,
            _ => return Err(jni::errors::Error::from_kind(jni::errors::ErrorKind::Msg(format!("Rule::fromRule: unknown enum name {}", name)))),
        };
        let boxed_rule = Box::into_raw(Box::new(rule)); // prevent boxed_rule from being freed, since it's to be referenced through the java heap

        let jrule = env.new_object("edu/rpi/aris/rules/Rule", "(J)V", &[JValue::from(boxed_rule as jni::sys::jlong)]);
        //println!("Rule.fromRule, boxed_rule: {:?}, jrule: {:?}", boxed_rule, jrule);
        Ok(jrule?.into_inner())
    })
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_edu_rpi_aris_rules_Rule_toString(env: JNIEnv, obj: JObject) -> jstring {
    with_thrown_errors(&env, |env| {
        let ptr: jni::sys::jlong = env.get_field(obj, "pointerToRustHeap", "J")?.j()?;
        let rule: &Rule = unsafe { &*(ptr as *mut Rule) };
        Ok(env.new_string(format!("{:?}", rule))?.into_inner())
    })
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_edu_rpi_aris_rules_Rule_getName(env: JNIEnv, obj: JObject) -> jstring {
    with_thrown_errors(&env, |env| {
        let ptr: jni::sys::jlong = env.get_field(obj, "pointerToRustHeap", "J")?.j()?;
        let rule: &Rule = unsafe { &*(ptr as *mut Rule) };
        Ok(env.new_string(rule.get_name())?.into_inner())
    })
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_edu_rpi_aris_rules_Rule_getRuleType(env: JNIEnv, obj: JObject) -> jarray {
    let jv = |s: &str| -> jni::errors::Result<JValue> { Ok(JObject::from(env.new_string(s)?).into()) };
    with_thrown_errors(&env, |env| {
        let ptr: jni::sys::jlong = env.get_field(obj, "pointerToRustHeap", "J")?.j()?;
        let rule: &Rule = unsafe { &*(ptr as *mut Rule) };
        let classifications = rule.get_classifications();
        let types = env.new_object_array(classifications.len() as _, "edu/rpi/aris/rules/Rule$Type", JObject::null())?;
        let cls = env.call_static_method("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", &[jv("edu.rpi.aris.rules.Rule$Type")?])?;
        for (i, classification) in classifications.iter().enumerate() {
            use RuleClassification::*;
            let ty = match classification {
                Introduction => env.call_static_method("java/lang/Enum", "valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;", &[cls, jv("INTRO")?])?,
                Elimination => env.call_static_method("java/lang/Enum", "valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;", &[cls, jv("ELIM")?])?,
                BooleanEquivalence => env.call_static_method("java/lang/Enum", "valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;", &[cls, jv("BOOL_EQUIVALENCE")?])?,
                ConditionalEquivalence => env.call_static_method("java/lang/Enum", "valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;", &[cls, jv("CONDITIONAL_EQUIVALENCE")?])?,
                QuantifierEquivalence => env.call_static_method("java/lang/Enum", "valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;", &[cls, jv("QUANTIFIER_EQUIVALENCE")?])?,
                MiscInference => env.call_static_method("java/lang/Enum", "valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;", &[cls, jv("MISC_INFERENCE")?])?,
            };
            env.set_object_array_element(types, i as _, ty.l()?)?;
        }
        Ok(types)
    })
}


#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_edu_rpi_aris_rules_Rule_requiredPremises(env: JNIEnv, obj: JObject) -> jni::sys::jlong {
    with_thrown_errors(&env, |env| {
        let ptr: jni::sys::jlong = env.get_field(obj, "pointerToRustHeap", "J")?.j()?;
        let rule: &Rule = unsafe { &*(ptr as *mut Rule) };
        Ok(rule.num_deps().unwrap_or(1) as _) // it looks like the java version represents generalizable premises as 1 premise, with the flag indicating >= instead of ==
    })
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_edu_rpi_aris_rules_Rule_canGeneralizePremises(env: JNIEnv, obj: JObject) -> jni::sys::jboolean {
    with_thrown_errors(&env, |env| {
        let ptr: jni::sys::jlong = env.get_field(obj, "pointerToRustHeap", "J")?.j()?;
        let rule: &Rule = unsafe { &*(ptr as *mut Rule) };
        Ok(if rule.num_deps().is_none() { 1 } else { 0 })
    })
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_edu_rpi_aris_rules_Rule_subProofPremises(env: JNIEnv, obj: JObject) -> jni::sys::jlong {
    with_thrown_errors(&env, |env| {
        let ptr: jni::sys::jlong = env.get_field(obj, "pointerToRustHeap", "J")?.j()?;
        let rule: &Rule = unsafe { &*(ptr as *mut Rule) };
        Ok(rule.num_subdeps().unwrap_or(0) as _)
    })
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_edu_rpi_aris_rules_Rule_verifyClaim(env: JNIEnv, ruleobj: JObject, conclusion: JObject, premises: jarray) -> jstring {
    use aris::proofs::java_shallow_proof::JavaShallowProof;
    with_thrown_errors(&env, |env| {
        let ptr: jni::sys::jlong = env.get_field(ruleobj, "pointerToRustHeap", "J")?.j()?;
        let rule: &Rule = unsafe { &*(ptr as *mut Rule) };
        let conc = jobject_to_expr(env, conclusion)?;
        let prem_len = env.get_array_length(premises)?;
        println!("Rule::verifyClaim conclusion: {:?}, {} premises", conc, prem_len);
        let mut deps = vec![];
        let mut sdeps = vec![];
        for i in 0..prem_len {
            let prem = env.get_object_array_element(premises, i)?;
            //println!("prem[{}] {:?}", i, prem);
            if env.call_method(prem, "isSubproof", "()Z", &[])?.z()? {
                let mut sdep = JavaShallowProof(vec![]);
                sdep.0.push(jobject_to_expr(env, env.call_method(prem, "getAssumption", "()Ledu/rpi/aris/ast/Expression;", &[])?.l()?)?);
                let lines = env.call_method(prem, "getSubproofLines", "()[Ledu/rpi/aris/ast/Expression;", &[])?.l()?;
                for j in 0..env.get_array_length(lines.into_inner())? {
                    sdep.0.push(jobject_to_expr(env, env.get_object_array_element(lines.into_inner(), j)?)?);
                }
                sdeps.push(sdep);
            } else {
                deps.push(Coproduct::Inl(jobject_to_expr(env, env.call_method(prem, "getPremise", "()Ledu/rpi/aris/ast/Expression;", &[])?.l()?)?));
            }
        }
        println!("Rule::verifyClaim deps: {:?} {:?}", deps, sdeps);
        if let Err(e) = rule.check(&JavaShallowProof(vec![]), conc, deps, sdeps) {
            Ok(env.new_string(format!("{}", e))?.into_inner())
        } else {
            Ok(std::ptr::null_mut())
        }
    })
}
