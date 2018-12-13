#[macro_use] extern crate nom;
extern crate jni;

pub mod parser;

use jni::JNIEnv;
use jni::strings::JavaStr;
use jni::objects::{JClass, JString, JValue, JObject};
use jni::sys::jobject;

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_edu_rpi_aris_ast_Expression_parseViaRust(env: JNIEnv, _cls: JClass, e: JString) -> jobject {
    (|| -> jni::errors::Result<jobject> {
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
    })().unwrap_or(std::ptr::null_mut())
}

pub fn expr_to_jobject<'a>(env: &'a JNIEnv, e: Expr) -> jni::errors::Result<JObject<'a>> {
    let obj = env.new_object(e.get_class(), "()V", &[])?;
    let jv = |s: &str| -> jni::errors::Result<JValue> { Ok(JObject::from(env.new_string(s)?).into()) };
    let rec = |e: Expr| -> jni::errors::Result<JValue> { Ok(JObject::from(expr_to_jobject(env, e)?).into()) };
    match e {
        Expr::Bottom => (),
        Expr::Predicate { name, args } => {
            env.set_field(obj, "name", "Ljava/lang/String;", jv(&name)?)?;
            let list = env.get_field(obj, "args", "Ljava/util/List;")?.l()?;
            for arg in args {
                env.call_method(list, "add", "(Ljava/lang/Object;)Z", &[jv(&arg)?])?;
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


#[derive(Clone, Debug, PartialEq, Eq)]
pub enum USymbol { Not }
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum BSymbol { Implies, Plus, Mult }
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum ASymbol { And, Or, Bicon }
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum QSymbol { Forall, Exists }

#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Expr {
    Bottom,
    Predicate { name: String, args: Vec<String> },
    Unop { symbol: USymbol, operand: Box<Expr> },
    Binop { symbol: BSymbol, left: Box<Expr>, right: Box<Expr> },
    AssocBinop { symbol: ASymbol, exprs: Vec<Expr> },
    Quantifier { symbol: QSymbol, name: String, body: Box<Expr> },
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
            Expr::Bottom => "Ledu/rpi/aris/ast/Expression$BottomExpression;",
            Expr::Predicate { .. } => "Ledu/rpi/aris/ast/Expression$PredicateExpression;",
            Expr::Unop { symbol, .. } => symbol.get_class(),
            Expr::Binop { symbol, .. } => symbol.get_class(),
            Expr::AssocBinop { symbol, .. } => symbol.get_class(),
            Expr::Quantifier { symbol, .. } => symbol.get_class(),
        }
    }
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
