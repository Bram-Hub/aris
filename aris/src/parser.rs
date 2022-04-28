//! Parse infix logical expressions into an AST

use crate::expr::Expr;
use crate::expr::Op;
use crate::expr::QuantKind;

use nom::*;

/// parser::parse parses a string slice into an Expr AST, returning None if there's an error
pub fn parse(input: &str) -> Option<Expr> {
    let newlined = format!("{}\n", input);
    main(&newlined).map(|(_, expr)| expr).ok()
}

/// parser::parse_unwrap is a convenience function used in the tests, and panics if the input doesn't parse
/// for handling user input, call parser::parse instead and handle the None case
pub fn parse_unwrap(input: &str) -> Expr {
    parse(input).unwrap_or_else(|| panic!("failed parsing: {}", input))
}

fn custom_error<A, B>(a: A, x: u32) -> nom::IResult<A, B> {
    Err(nom::Err::Error(nom::Context::Code(a, nom::ErrorKind::Custom(x))))
}

/// variable is implemented as a function instead of via nom's macros in order to more conveniently reject keywords as variables
/// in nom5, the "verify" combinator does this properly, in nom4, the "verify" macro's predicate requires ownership of the return value
fn variable(s: &str) -> nom::IResult<&str, String> {
    let r = variable_(s);
    if let Ok((rest, ref var)) = r {
        if let Ok((_, _)) = keyword(var) {
            return custom_error(rest, 0);
        }
    }
    r
}

// All the `named!` things below can be thought of as grammar productions interleaved with code that constructs the AST value associated with each production.
// `alt!` corresponds to alternation/choice in an EBNF grammar
// `do_parse!` corresponds to sequencing, and optionally provides names for the parts of the sequence that build up the AST value
// `tag!` is used for literal string values, and supports unicode

named!(space<&str, ()>, do_parse!(many0!(one_of!(" \t")) >> (())));
named!(variable_<&str, String>, do_parse!(x: many1!(one_of!("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_")) >> ({let mut y = String::new(); for c in x { y.push(c); }; y})));
named!(keyword<&str, &str>, alt!(tag!("forall") | tag!("exists")));

named!(contradiction<&str, Expr>, do_parse!(alt!(tag!("_|_") | tag!("⊥")) >> (Expr::Contra)));
named!(tautology<&str, Expr>, do_parse!(alt!(tag!("^|^") | tag!("⊤")) >> (Expr::Taut)));

named!(notterm<&str, Expr>, do_parse!(alt!(tag!("~") | tag!("¬")) >> e: paren_expr >> (Expr::Not { operand: Box::new(e) })));

named!(predicate<&str, Expr>, alt!(
do_parse!(space >> name: variable >> space >> tag!("(") >> space >> args: separated_list!(do_parse!(space >> tag!(",") >> space >> (())), expr) >> tag!(")") >> (Expr::Apply { func: Box::new(Expr::Var { name }), args })) |
do_parse!(space >> name: variable >> space >> (Expr::Var { name }))
));

named!(forall_quantifier<&str, QuantKind>, do_parse!(alt!(tag!("forall ") | tag!("∀")) >> (QuantKind::Forall)));
named!(exists_quantifier<&str, QuantKind>, do_parse!(alt!(tag!("exists ") | tag!("∃")) >> (QuantKind::Exists)));
named!(quantifier<&str, QuantKind>, alt!(forall_quantifier | exists_quantifier));
named!(binder<&str, Expr>, do_parse!(space >> kind: quantifier >> space >> name: variable >> space >> tag!(",") >> space >> body: expr >> (Expr::Quant { kind, name, body: Box::new(body) })));

named!(impl_term<&str, Expr>, do_parse!(left: paren_expr >> space >> alt!(tag!("->") | tag!("→")) >> space >> right: paren_expr >> (Expr::Impl { left: Box::new(left), right: Box::new(right) })));

named!(andrepr<&str, Op>, do_parse!(alt!(tag!("&") | tag!("∧") | tag!("/\\")) >> (Op::And)));
named!(orrepr<&str, Op>, do_parse!(alt!(tag!("|") | tag!("∨") | tag!("\\/")) >> (Op::Or)));
named!(biconrepr<&str, Op>, do_parse!(alt!(tag!("<->") | tag!("↔")) >> (Op::Bicon)));
named!(equivrepr<&str, Op>, do_parse!(alt!(tag!("===") | tag!("≡")) >> (Op::Equiv)));
named!(plusrepr<&str, Op>, do_parse!(tag!("+") >> (Op::Add)));
named!(multrepr<&str, Op>, do_parse!(tag!("*") >> (Op::Mult)));

named!(assoc_term_aux<&str, (Vec<Expr>, Vec<Op>)>, alt!(
do_parse!(space >> e: paren_expr >> space >> sym: alt!(andrepr | orrepr | biconrepr | equivrepr | plusrepr | multrepr) >> space >> rec: assoc_term_aux >> ({ let (mut es, mut syms) = rec; es.push(e); syms.push(sym); (es, syms) })) |
do_parse!(e: paren_expr >> (vec![e], vec![]))
));

/// assocterm is implemented as a function instead of using nom's macros because
/// enforcing that all the symbols are the same is more easily done with iterators.
/// This check is what rules out `(a /\ b \/ c)` without further parenthesization.
fn assoc_term(s: &str) -> nom::IResult<&str, Expr> {
    let (rest, (mut exprs, syms)) = assoc_term_aux(s)?;
    assert_eq!(exprs.len(), syms.len() + 1);
    if exprs.len() == 1 {
        return custom_error(rest, 0);
    }
    let op = syms[0];
    if !syms.iter().all(|x| x == &op) {
        return custom_error(rest, 0);
    }
    exprs.reverse();
    Ok((rest, Expr::Assoc { op, exprs }))
}

// paren_expr is a factoring of expr that eliminates left-recursion, which parser combinators have trouble with
named!(paren_expr<&str, Expr>, alt!(contradiction | tautology | predicate | notterm | binder | do_parse!(space >> tag!("(") >> space >> e: expr >> space >> tag!(")") >> space >> (e))));
named!(expr<&str, Expr>, alt!(assoc_term | impl_term | paren_expr));
named!(main<&str, Expr>, do_parse!(e: expr >> tag!("\n") >> (e)));

#[test]
fn test_parser() {
    use crate::expr::free_vars;
    println!("{:?}", predicate("a(   b, c)"));
    println!("{:?}", predicate("s(s(s(s(s(z)))))"));
    println!("{:?}", expr("a & b & c(x,y)\n"));
    println!("{:?}", expr("forall a, (b & c)\n"));
    let e = expr("exists x, (Tet(x) & SameCol(x, b)) -> ~forall x, (Tet(x) -> LeftOf(x, b))\n");
    let fv = e.clone().map(|x| free_vars(&x.1));
    println!("{:?} {:?}", e, fv);
    let e = expr("forall a, forall b, ((forall x, in(x,a) <-> in(x,b)) -> eq(a,b))\n");
    let fv = e.clone().map(|x| free_vars(&x.1));
    assert_eq!(fv, Ok(["eq", "in"].iter().map(|x| String::from(*x)).collect()));
    println!("{:?} {:?}", e, fv);
    named!(f<&str, Vec<&str>>, many1!(tag!("a")));
    println!("{:?}", f("aa\n"));
}
