use super::*;

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_edu_rpi_aris_rules_Rule_fromRule(env: JNIEnv, _: JObject, rule: JObject) -> jobject {
    with_thrown_errors(&env, |env| {
        let cls = env.call_method(rule, "getClass", "()Ljava/lang/Class;", &[])?.l()?;
        let classname = String::from(env.get_string(JString::from(env.call_method(cls, "getName", "()Ljava/lang/String;", &[])?.l()?))?);
        println!("Rule.fromRule, rule class: {:?}", classname);
        if classname != "edu.rpi.aris.rules.RuleList" {
            return Err(jni::errors::Error::from_kind(jni::errors::ErrorKind::Msg(format!("Rule::fromRule: unknown class {}", classname))));
        }

        let name = String::from(env.get_string(JString::from(env.call_method(rule, "name", "()Ljava/lang/String;", &[])?.l()?))?);
        println!("Rule.fromRule, rule enum name: {:?}", name);
        let rule = match &*name {
            "CONJUNCTION" => RuleM::AndIntro,
            "SIMPLIFICATION" => RuleM::AndElim,
            "ADDITION" => RuleM::OrIntro,
            "DISJUNCTIVE_SYLLOGISM" => RuleM::OrElim,
            "MODUS_PONENS" => RuleM::ImpElim,
            "MODUS_TOLLENS" => RuleM::ModusTollens,
            "HYPOTHETICAL_SYLLOGISM" => RuleM::HypotheticalSyllogism,
            "EXCLUDED_MIDDLE" => RuleM::ExcludedMiddle,
            "CONSTRUCTIVE_DILEMMA" => RuleM::ConstructiveDilemma,
            "ASSOCIATION" => RuleM::Association,
            "COMMUTATION" => RuleM::Commutation,
            "DOUBLENEGATION" => RuleM::NotElim,
            "IDEMPOTENCE" => RuleM::Idempotence,
            "DE_MORGAN" => RuleM::DeMorgan,
            "DISTRIBUTION" => RuleM::Distribution,
            _ => { let e = Err(jni::errors::Error::from_kind(jni::errors::ErrorKind::Msg(format!("Rule::fromRule: unknown enum name {}", name)))); println!("{:?}", e); return e; },
        };
        let boxed_rule = Box::into_raw(Box::new(rule)); // prevent boxed_rule from being freed, since it's to be referenced through the java heap

        let jrule = env.new_object("edu/rpi/aris/rules/Rule", "(J)V", &[JValue::from(boxed_rule as jni::sys::jlong)]);
        println!("Rule.fromRule, boxed_rule: {:?}, jrule: {:?}", boxed_rule, jrule);
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
