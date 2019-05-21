use super::*;

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_edu_rpi_aris_ast_Expression_toDebugString(env: JNIEnv, obj: JObject) -> jstring {
    with_thrown_errors(&env, |env| {
        let expr = jobject_to_expr(&env, obj);
        Ok(env.new_string(format!("{:?}", expr?))?.into_inner())
    })
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_edu_rpi_aris_ast_Expression_toString(env: JNIEnv, obj: JObject) -> jstring {
    with_thrown_errors(&env, |env| {
        let expr = jobject_to_expr(&env, obj);
        Ok(env.new_string(format!("{}", expr?))?.into_inner())
    })
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_edu_rpi_aris_ast_Expression_equals(env: JNIEnv, this: JObject, other: JObject) -> jni::sys::jboolean {
    (|env| -> jni::errors::Result<_>{
        let expr1 = jobject_to_expr(&env, this)?;
        let expr2 = jobject_to_expr(&env, other)?;
        Ok((expr1 == expr2) as _)
    })(env).unwrap_or(false as _)
}

pub fn jobject_to_expr(env: &JNIEnv, obj: JObject) -> jni::errors::Result<Expr> {
    let cls = env.call_method(obj, "getClass", "()Ljava/lang/Class;", &[])?.l()?;
    let name = String::from(env.get_string(JString::from(env.call_method(cls, "getName", "()Ljava/lang/String;", &[])?.l()?))?);
    use expression_builders::*;
    let handle_binop = |symbol: BSymbol| -> jni::errors::Result<Expr> {
        let left = env.get_field(obj, "l", "Ledu/rpi/aris/ast/Expression;")?.l()?;
        let right = env.get_field(obj, "r", "Ledu/rpi/aris/ast/Expression;")?.l()?;
        Ok(binop(symbol, jobject_to_expr(env, left)?, jobject_to_expr(env, right)?))
    };
    let handle_abe = |symbol: ASymbol| -> jni::errors::Result<Expr> {
        let mut exprs = vec![];
        java_iterator_for_each(env, env.get_field(obj, "exprs", "Ljava/util/ArrayList;")?.l()?, |expr| { Ok(exprs.push(jobject_to_expr(env, expr)?)) })?;
        Ok(Expr::AssocBinop { symbol, exprs })
    };
    let handle_quantifier = |symbol: QSymbol| -> jni::errors::Result<Expr> {
        let name = jobject_to_string(env, env.get_field(obj, "boundvar", "Ljava/lang/String;")?.l()?)?;
        let body = env.get_field(obj, "body", "Ledu/rpi/aris/ast/Expression;")?.l()?;
        Ok(Expr::Quantifier { symbol, name, body: Box::new(jobject_to_expr(env, body)?) })
    };
    match &*name {
        "edu.rpi.aris.ast.Expression$NotExpression" => {
            let operand = env.get_field(obj, "operand", "Ledu/rpi/aris/ast/Expression;")?.l()?;
            Ok(not(jobject_to_expr(env, operand)?))
        },
        "edu.rpi.aris.ast.Expression$VarExpression" => {
            let name = jobject_to_string(env, env.get_field(obj, "name", "Ljava/lang/String;")?.l()?)?;
            Ok(var(&name))
        },
        "edu.rpi.aris.ast.Expression$ImplicationExpression" => handle_binop(BSymbol::Implies),
        "edu.rpi.aris.ast.Expression$AddExpression" => handle_binop(BSymbol::Plus),
        "edu.rpi.aris.ast.Expression$MultExpression" => handle_binop(BSymbol::Mult),
        "edu.rpi.aris.ast.Expression$AndExpression" => handle_abe(ASymbol::And),
        "edu.rpi.aris.ast.Expression$OrExpression" => handle_abe(ASymbol::Or),
        "edu.rpi.aris.ast.Expression$BiconExpression" => handle_abe(ASymbol::Bicon),
        "edu.rpi.aris.ast.Expression$EquivExpression" => handle_abe(ASymbol::Equiv),
        "edu.rpi.aris.ast.Expression$ForallExpression" => handle_quantifier(QSymbol::Forall),
        "edu.rpi.aris.ast.Expression$ExistsExpression" => handle_quantifier(QSymbol::Exists),
        "edu.rpi.aris.ast.Expression$ContradictionExpression" => Ok(Expr::Contradiction),
        "edu.rpi.aris.ast.Expression$TautologyExpression" => Ok(Expr::Tautology),
        "edu.rpi.aris.ast.Expression$ApplyExpression" => {
            let func = jobject_to_expr(env, env.get_field(obj, "func", "Ledu/rpi/aris/ast/Expression;")?.l()?)?;
            let mut args = vec![];
            java_iterator_for_each(env, env.get_field(obj, "args", "Ljava/util/List;")?.l()?, |arg| { Ok(args.push(jobject_to_expr(env, arg)?)) })?;
            Ok(expression_builders::apply(func, &args[..]))
        },
        _ => Err(jni::errors::Error::from_kind(jni::errors::ErrorKind::Msg(format!("jobject_to_expr: unknown class {}", name)))),
    }
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_edu_rpi_aris_ast_Expression_parseViaRust(env: JNIEnv, _cls: JClass, e: JString) -> jobject {
    with_thrown_errors(&env, |env| {
        if let Ok(e) = JavaStr::from_env(&env, e)?.to_str() {
            //println!("received {:?}", e);
            let e = format!("{}\n", e);
            let parsed = parser::main(&e);
            //println!("parse: {:?}", parsed);
            if let Ok(("", expr)) = parsed {
                let r = expr_to_jobject(&env, expr)?;
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
    let rec = |e: Expr| -> jni::errors::Result<JValue> { Ok(JObject::from(expr_to_jobject(env, e)?).into()) };
    match e {
        Expr::Contradiction => (),
        Expr::Tautology => (),
        Expr::Var { name } => env.set_field(obj, "name", "Ljava/lang/String;", jv(&name)?)?,
        Expr::Apply { func, args } => {
            env.set_field(obj, "func", "Ledu/rpi/aris/ast/Expression;", rec(*func)?)?;
            let list = env.get_field(obj, "args", "Ljava/util/List;")?.l()?;
            for arg in args {
                env.call_method(list, "add", "(Ljava/lang/Object;)Z", &[rec(arg)?])?;
            }
        },
        Expr::Unop { symbol: _, operand } => {
            env.set_field(obj, "operand", "Ledu/rpi/aris/ast/Expression;", rec(*operand)?)?;
        },
        Expr::Binop { symbol: _, left, right } => {
            env.set_field(obj, "l", "Ledu/rpi/aris/ast/Expression;", rec(*left)?)?;
            env.set_field(obj, "r", "Ledu/rpi/aris/ast/Expression;", rec(*right)?)?;
        },
        Expr::AssocBinop { symbol: _, exprs } => {
            let list = env.get_field(obj, "exprs", "Ljava/util/ArrayList;")?.l()?;
            for expr in exprs {
                env.call_method(list, "add", "(Ljava/lang/Object;)Z", &[rec(expr)?])?;
            }
        }
        Expr::Quantifier { symbol: _, name, body } => {
            env.set_field(obj, "boundvar", "Ljava/lang/String;", jv(&name)?)?;
            env.set_field(obj, "body", "Ledu/rpi/aris/ast/Expression;", rec(*body)?)?;
        }
    }
    Ok(obj)
}

trait HasClass {
    fn get_class(&self) -> &'static str;
}
impl HasClass for USymbol {
    fn get_class(&self) -> &'static str {
        match self {
            USymbol::Not => "Ledu/rpi/aris/ast/Expression$NotExpression;",
        }
    }
}
impl HasClass for BSymbol {
    fn get_class(&self) -> &'static str {
        match self {
            BSymbol::Implies => "Ledu/rpi/aris/ast/Expression$ImplicationExpression;",
            BSymbol::Plus => "Ledu/rpi/aris/ast/Expression$AddExpression;",
            BSymbol::Mult => "Ledu/rpi/aris/ast/Expression$MultExpression;",
        }
    }
}
impl HasClass for ASymbol {
    fn get_class(&self) -> &'static str {
        match self {
            ASymbol::And => "Ledu/rpi/aris/ast/Expression$AndExpression;",
            ASymbol::Or => "Ledu/rpi/aris/ast/Expression$OrExpression;",
            ASymbol::Bicon => "Ledu/rpi/aris/ast/Expression$BiconExpression;",
            ASymbol::Equiv => "Ledu/rpi/aris/ast/Expression$EquivExpression;",
        }
    }
}
impl HasClass for QSymbol {
    fn get_class(&self) -> &'static str {
        match self {
            QSymbol::Forall => "Ledu/rpi/aris/ast/Expression$ForallExpression;",
            QSymbol::Exists => "Ledu/rpi/aris/ast/Expression$ExistsExpression;",
        }
    }
}
impl HasClass for Expr {
    fn get_class(&self) -> &'static str {
        match self {
            Expr::Contradiction => "Ledu/rpi/aris/ast/Expression$ContradictionExpression;",
            Expr::Tautology => "Ledu/rpi/aris/ast/Expression$TautologyExpression;",
            Expr::Var { .. } => "Ledu/rpi/aris/ast/Expression$VarExpression;",
            Expr::Apply { .. } => "Ledu/rpi/aris/ast/Expression$ApplyExpression;",
            Expr::Unop { symbol, .. } => symbol.get_class(),
            Expr::Binop { symbol, .. } => symbol.get_class(),
            Expr::AssocBinop { symbol, .. } => symbol.get_class(),
            Expr::Quantifier { symbol, .. } => symbol.get_class(),
        }
    }
}

