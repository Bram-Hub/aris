extern crate jni;

use jni::JNIEnv;
use jni::objects::{JClass, JString, JObject};
use jni::sys::jobject;

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_edu_rpi_aris_ast_Expression_parseViaRust(env: JNIEnv, cls: JClass, e: JString) -> jobject {
    let dummy_expr = {
        use expression_builders::*;
        exists("a", forall("x", assocbinop(ASymbol::And, &[
            not(predicate("y", &["z"])),
            Expr::Bottom,
        ])))
    };
    let r = expr_to_jobject(&env, dummy_expr);
    //println!("{:?}", r);
    r.unwrap_or(std::ptr::null_mut())
}

pub fn expr_to_jobject(env: &JNIEnv, e: Expr) -> jni::errors::Result<jobject> {
    match e {
        Expr::Bottom => Ok(env.new_object("edu/rpi/aris/ast/Expression$BottomExpression", "()V", &[])?.into_inner()),
        Expr::Predicate { name, args } => {
            let obj = env.new_object("edu/rpi/aris/ast/Expression$PredicateExpression", "()V", &[])?;
            env.set_field(obj, "name", "Ljava/lang/String;", JObject::from(env.new_string(name)?).into())?;
            let list = env.get_field(obj, "args", "Ljava/util/List;")?.l()?;
            for arg in args {
                env.call_method(list, "add", "(Ljava/lang/Object;)Z", &[JObject::from(env.new_string(arg)?).into()])?;
            }
            Ok(obj.into_inner())
        },
        Expr::Unop { symbol, operand } => {
            let obj = env.new_object(symbol.get_class(), "()V", &[])?;
            env.set_field(obj, "operand", "Ledu/rpi/aris/ast/Expression;", JObject::from(expr_to_jobject(env, *operand)?).into())?;
            Ok(obj.into_inner())
        },
        Expr::Binop { symbol, left, right } => {
            let obj = env.new_object(symbol.get_class(), "()V", &[])?;
            env.set_field(obj, "l", "Ledu/rpi/aris/ast/Expression;", JObject::from(expr_to_jobject(env, *left)?).into())?;
            env.set_field(obj, "r", "Ledu/rpi/aris/ast/Expression;", JObject::from(expr_to_jobject(env, *right)?).into())?;
            Ok(obj.into_inner())
        },
        Expr::AssocBinop { symbol, exprs } => {
            let obj = env.new_object(symbol.get_class(), "()V", &[])?;
            let list = env.get_field(obj, "exprs", "Ljava/util/ArrayList;")?.l()?;
            for expr in exprs {
                env.call_method(list, "add", "(Ljava/lang/Object;)Z", &[JObject::from(expr_to_jobject(env, expr)?).into()])?;
            }
            Ok(obj.into_inner())
        }
        Expr::Quantifier { symbol, name, body } => {
            let obj = env.new_object(symbol.get_class(), "()V", &[])?;
            env.set_field(obj, "boundvar", "Ljava/lang/String;", JObject::from(env.new_string(name)?).into())?;
            env.set_field(obj, "body", "Ledu/rpi/aris/ast/Expression;", JObject::from(expr_to_jobject(env, *body)?).into())?;
            Ok(obj.into_inner())
        }
    }
}


trait HasClass {
    fn get_class(&self) -> &'static str;
}

#[derive(Clone, Debug)]
pub enum USymbol { Not }
impl HasClass for USymbol {
    fn get_class(&self) -> &'static str {
        match self {
            USymbol::Not => "Ledu/rpi/aris/ast/Expression$NotExpression;",
        }
    }
}

#[derive(Clone, Debug)]
pub enum BSymbol { Implies, Plus, Mult }
impl HasClass for BSymbol {
    fn get_class(&self) -> &'static str {
        match self {
            BSymbol::Implies => "Ledu/rpi/aris/ast/Expression$ImplicationExpression;",
            BSymbol::Plus => "Ledu/rpi/aris/ast/Expression$AddExpression;",
            BSymbol::Mult => "Ledu/rpi/aris/ast/Expression$MultExpression;",
        }
    }
}

#[derive(Clone, Debug)]
pub enum ASymbol { And, Or, Bicon }
impl HasClass for ASymbol {
    fn get_class(&self) -> &'static str {
        match self {
            ASymbol::And => "Ledu/rpi/aris/ast/Expression$AndExpression;",
            ASymbol::Or => "Ledu/rpi/aris/ast/Expression$OrExpression;",
            ASymbol::Bicon => "Ledu/rpi/aris/ast/Expression$BiconExpression;",
        }
    }
}

#[derive(Clone, Debug)]
pub enum QSymbol { Forall, Exists }
impl HasClass for QSymbol {
    fn get_class(&self) -> &'static str {
        match self {
            QSymbol::Forall => "Ledu/rpi/aris/ast/Expression$ForallExpression;",
            QSymbol::Exists => "Ledu/rpi/aris/ast/Expression$ExistsExpression;",
        }
    }
}

#[derive(Clone, Debug)]
pub enum Expr {
    Bottom,
    Predicate { name: String, args: Vec<String> },
    Unop { symbol: USymbol, operand: Box<Expr> },
    Binop { symbol: BSymbol, left: Box<Expr>, right: Box<Expr> },
    AssocBinop { symbol: ASymbol, exprs: Vec<Expr> },
    Quantifier { symbol: QSymbol, name: String, body: Box<Expr> },
}

pub mod expression_builders {
    use super::{Expr, USymbol, BSymbol, ASymbol, QSymbol};
    pub fn predicate(name: &str, args: &[&str]) -> Expr { Expr::Predicate { name: name.into(), args: args.iter().map(|&x| x.into()).collect() } }
    pub fn not(expr: Expr) -> Expr { Expr::Unop { symbol: USymbol::Not, operand: Box::new(expr) } }
    pub fn binop(symbol: BSymbol, l: Expr, r: Expr) -> Expr { Expr::Binop { symbol, left: Box::new(l), right: Box::new(r) } }
    pub fn assocbinop(symbol: ASymbol, exprs: &[Expr]) -> Expr { Expr::AssocBinop { symbol, exprs: exprs.iter().cloned().collect() } }
    pub fn forall(name: &str, body: Expr) -> Expr { Expr::Quantifier { symbol: QSymbol::Forall, name: name.into(), body: Box::new(body) } }
    pub fn exists(name: &str, body: Expr) -> Expr { Expr::Quantifier { symbol: QSymbol::Exists, name: name.into(), body: Box::new(body) } }
}

#[cfg(test)]
mod tests {
}
