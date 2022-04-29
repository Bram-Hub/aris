use super::*;

use aris::expr::Expr;
use aris::expr::Op;
use aris::expr::QuantKind;

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_edu_rpi_aris_ast_Expression_toDebugString(env: JNIEnv, obj: JObject) -> jstring {
    with_thrown_errors(&env, |env| {
        let expr = jobject_to_expr(env, obj);
        Ok(env.new_string(format!("{:?}", expr?))?.into_inner())
    })
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_edu_rpi_aris_ast_Expression_toString(env: JNIEnv, obj: JObject) -> jstring {
    with_thrown_errors(&env, |env| {
        let expr = jobject_to_expr(env, obj);
        Ok(env.new_string(format!("{}", expr?))?.into_inner())
    })
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_edu_rpi_aris_ast_Expression_equals(env: JNIEnv, this: JObject, other: JObject) -> jni::sys::jboolean {
    (|env| -> jni::errors::Result<_> {
        let expr1 = jobject_to_expr(&env, this)?;
        let expr2 = jobject_to_expr(&env, other)?;
        Ok((expr1 == expr2) as _)
    })(env)
    .unwrap_or(false as _)
}

pub fn jobject_to_expr(env: &JNIEnv, obj: JObject) -> jni::errors::Result<Expr> {
    let cls = env.call_method(obj, "getClass", "()Ljava/lang/Class;", &[])?.l()?;
    let name = String::from(env.get_string(JString::from(env.call_method(cls, "getName", "()Ljava/lang/String;", &[])?.l()?))?);
    let handle_impl = || -> jni::errors::Result<Expr> {
        let left = env.get_field(obj, "l", "Ledu/rpi/aris/ast/Expression;")?.l()?;
        let right = env.get_field(obj, "r", "Ledu/rpi/aris/ast/Expression;")?.l()?;
        Ok(Expr::implies(jobject_to_expr(env, left)?, jobject_to_expr(env, right)?))
    };
    let handle_assoc = |op: Op| -> jni::errors::Result<Expr> {
        let mut exprs = vec![];
        java_iterator_for_each(env, env.get_field(obj, "exprs", "Ljava/util/ArrayList;")?.l()?, |expr| {
            exprs.push(jobject_to_expr(env, expr)?);
            Ok(())
        })?;
        Ok(Expr::Assoc { op, exprs })
    };
    let handle_quantifier = |kind: QuantKind| -> jni::errors::Result<Expr> {
        let name = jobject_to_string(env, env.get_field(obj, "boundvar", "Ljava/lang/String;")?.l()?)?;
        let body = env.get_field(obj, "body", "Ledu/rpi/aris/ast/Expression;")?.l()?;
        Ok(Expr::Quant { kind, name, body: Box::new(jobject_to_expr(env, body)?) })
    };
    match &*name {
        "edu.rpi.aris.ast.Expression$NotExpression" => {
            let operand = env.get_field(obj, "operand", "Ledu/rpi/aris/ast/Expression;")?.l()?;
            Ok(!jobject_to_expr(env, operand)?)
        }
        "edu.rpi.aris.ast.Expression$VarExpression" => {
            let name = jobject_to_string(env, env.get_field(obj, "name", "Ljava/lang/String;")?.l()?)?;
            Ok(Expr::var(&name))
        }
        "edu.rpi.aris.ast.Expression$ImplicationExpression" => handle_impl(),
        "edu.rpi.aris.ast.Expression$AddExpression" => handle_assoc(Op::Add),
        "edu.rpi.aris.ast.Expression$MultExpression" => handle_assoc(Op::Mult),
        "edu.rpi.aris.ast.Expression$AndExpression" => handle_assoc(Op::And),
        "edu.rpi.aris.ast.Expression$OrExpression" => handle_assoc(Op::Or),
        "edu.rpi.aris.ast.Expression$BiconExpression" => handle_assoc(Op::Bicon),
        "edu.rpi.aris.ast.Expression$EquivExpression" => handle_assoc(Op::Equiv),
        "edu.rpi.aris.ast.Expression$ForallExpression" => handle_quantifier(QuantKind::Forall),
        "edu.rpi.aris.ast.Expression$ExistsExpression" => handle_quantifier(QuantKind::Exists),
        "edu.rpi.aris.ast.Expression$ContradictionExpression" => Ok(Expr::Contra),
        "edu.rpi.aris.ast.Expression$TautologyExpression" => Ok(Expr::Taut),
        "edu.rpi.aris.ast.Expression$ApplyExpression" => {
            let func = jobject_to_expr(env, env.get_field(obj, "func", "Ledu/rpi/aris/ast/Expression;")?.l()?)?;
            let mut args = vec![];
            java_iterator_for_each(env, env.get_field(obj, "args", "Ljava/util/List;")?.l()?, |arg| {
                args.push(jobject_to_expr(env, arg)?);
                Ok(())
            })?;
            Ok(Expr::apply(func, &args[..]))
        }
        _ => Err(jni::errors::Error::from_kind(jni::errors::ErrorKind::Msg(format!("jobject_to_expr: unknown class {}", name)))),
    }
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_edu_rpi_aris_ast_Expression_parseViaRust(env: JNIEnv, _cls: JClass, e: JString) -> jobject {
    with_thrown_errors(&env, |env| {
        if let Ok(e) = JavaStr::from_env(env, e)?.to_str() {
            //println!("received {:?}", e);
            let parsed = aris::parser::parse(e);
            //println!("parse: {:?}", parsed);
            if let Some(expr) = parsed {
                let r = expr_to_jobject(env, expr)?;
                Ok(r.into_inner())
            } else {
                Ok(std::ptr::null_mut())
            }
        } else {
            Ok(std::ptr::null_mut())
        }
    })
}

pub fn expr_to_jobject<'a>(env: &'a JNIEnv, e: Expr) -> jni::errors::Result<JObject<'a>> {
    let obj = env.new_object(e.get_class(), "()V", &[])?;
    let jv = |s: &str| -> jni::errors::Result<JValue> { Ok(JObject::from(env.new_string(s)?).into()) };
    let rec = |e: Expr| -> jni::errors::Result<JValue> { Ok(expr_to_jobject(env, e)?.into()) };
    match e {
        Expr::Contra => (),
        Expr::Taut => (),
        Expr::Var { name } => env.set_field(obj, "name", "Ljava/lang/String;", jv(&name)?)?,
        Expr::Apply { func, args } => {
            env.set_field(obj, "func", "Ledu/rpi/aris/ast/Expression;", rec(*func)?)?;
            let list = env.get_field(obj, "args", "Ljava/util/List;")?.l()?;
            for arg in args {
                env.call_method(list, "add", "(Ljava/lang/Object;)Z", &[rec(arg)?])?;
            }
        }
        Expr::Not { operand } => {
            env.set_field(obj, "operand", "Ledu/rpi/aris/ast/Expression;", rec(*operand)?)?;
        }
        Expr::Impl { left, right } => {
            env.set_field(obj, "l", "Ledu/rpi/aris/ast/Expression;", rec(*left)?)?;
            env.set_field(obj, "r", "Ledu/rpi/aris/ast/Expression;", rec(*right)?)?;
        }
        Expr::Assoc { op: _, exprs } => {
            let list = env.get_field(obj, "exprs", "Ljava/util/ArrayList;")?.l()?;
            for expr in exprs {
                env.call_method(list, "add", "(Ljava/lang/Object;)Z", &[rec(expr)?])?;
            }
        }
        Expr::Quant { kind: _, name, body } => {
            env.set_field(obj, "boundvar", "Ljava/lang/String;", jv(&name)?)?;
            env.set_field(obj, "body", "Ledu/rpi/aris/ast/Expression;", rec(*body)?)?;
        }
    }
    Ok(obj)
}

trait HasClass {
    fn get_class(&self) -> &'static str;
}
impl HasClass for Op {
    fn get_class(&self) -> &'static str {
        match self {
            Op::And => "Ledu/rpi/aris/ast/Expression$AndExpression;",
            Op::Or => "Ledu/rpi/aris/ast/Expression$OrExpression;",
            Op::Bicon => "Ledu/rpi/aris/ast/Expression$BiconExpression;",
            Op::Equiv => "Ledu/rpi/aris/ast/Expression$EquivExpression;",
            Op::Add => "Ledu/rpi/aris/ast/Expression$AddExpression;",
            Op::Mult => "Ledu/rpi/aris/ast/Expression$MultExpression;",
        }
    }
}
impl HasClass for QuantKind {
    fn get_class(&self) -> &'static str {
        match self {
            QuantKind::Forall => "Ledu/rpi/aris/ast/Expression$ForallExpression;",
            QuantKind::Exists => "Ledu/rpi/aris/ast/Expression$ExistsExpression;",
        }
    }
}
impl HasClass for Expr {
    fn get_class(&self) -> &'static str {
        match self {
            Expr::Contra => "Ledu/rpi/aris/ast/Expression$ContradictionExpression;",
            Expr::Taut => "Ledu/rpi/aris/ast/Expression$TautologyExpression;",
            Expr::Var { .. } => "Ledu/rpi/aris/ast/Expression$VarExpression;",
            Expr::Apply { .. } => "Ledu/rpi/aris/ast/Expression$ApplyExpression;",
            Expr::Not { .. } => "Ledu/rpi/aris/ast/Expression$NotExpression;",
            Expr::Impl { .. } => "Ledu/rpi/aris/ast/Expression$ImplicationExpression;",
            Expr::Assoc { op, .. } => op.get_class(),
            Expr::Quant { kind, .. } => kind.get_class(),
        }
    }
}
