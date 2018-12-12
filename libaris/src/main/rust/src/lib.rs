extern crate jni;

use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jobject;

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_edu_rpi_aris_ast_ASTConstructor_parseViaRust(env: JNIEnv, cls: JClass, e: JString) -> jobject {
    println!("Hello from Rust!");
    std::ptr::null_mut()
}



#[derive(Debug)]
enum USymbol { Not }

#[derive(Debug)]
enum BSymbol { Implies, Plus, Mult }

#[derive(Debug)]
enum ASymbol { And, Or, Bicon }

#[derive(Debug)]
enum QSymbol { Forall, Exists }

#[derive(Debug)]
enum Expression {
    Bottom,
    Predicate { name: String, args: Vec<String> },
    Unop { symbol: USymbol, operand: Box<Expression> },
    Binop { symbol: BSymbol, left: Box<Expression>, right: Box<Expression> },
    AssocBinop { symbol: ASymbol, exprs: Vec<Expression> },
    Quantifier { symbol: QSymbol, name: String, body: Box<Expression> },
}

#[cfg(test)]
mod tests {
}
