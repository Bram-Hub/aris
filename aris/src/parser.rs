//! Parse infix logical expressions into an AST

use nom::branch::alt;
use nom::bytes::complete::tag;
use nom::character::complete::newline;
use nom::character::complete::one_of;
use nom::combinator::map;
use nom::combinator::recognize;
use nom::combinator::value;
use nom::combinator::verify;
use nom::multi::many0;
use nom::multi::many1;
use nom::multi::separated_list0;
use nom::sequence::delimited;
use nom::sequence::pair;
use nom::sequence::preceded;
use nom::sequence::separated_pair;
use nom::sequence::terminated;
use nom::sequence::tuple;
use nom::IResult;

use crate::expr::Expr;
use crate::expr::Op;
use crate::expr::QuantKind;

/// parser::parse parses a string slice into an Expr AST, returning None if there's an error
pub fn parse(input: &str) -> Option<Expr> {
    let newlined = format!("{input}\n");
    main(&newlined).map(|(_, expr)| expr).ok()
}

/// parser::parse_unwrap is a convenience function used in the tests, and panics if the input doesn't parse
/// for handling user input, call parser::parse instead and handle the None case
pub fn parse_unwrap(input: &str) -> Expr {
    parse(input).unwrap_or_else(|| panic!("failed parsing: {input}"))
}

fn custom_error<A, B>(a: A) -> nom::IResult<A, B> {
    Err(nom::Err::Error(nom::error::Error { input: a, code: nom::error::ErrorKind::Fail }))
}

fn variable(input: &str) -> nom::IResult<&str, String> {
    verify(variable_, |v| keyword(v).is_err())(input)
}

// All the functions below can be thought of as grammar productions interleaved with code that constructs the AST value associated with each production.
// `alt` corresponds to alternation/choice in an EBNF grammar
// `tag` is used for literal string values, and supports unicode

fn space(input: &str) -> IResult<&str, ()> {
    value((), many0(one_of(" \t")))(input)
}

fn variable_(input: &str) -> IResult<&str, String> {
    map(recognize(many1(one_of("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_"))), |v: &str| v.to_owned())(input)
}

fn keyword(input: &str) -> IResult<&str, &str> {
    alt((tag("forall"), tag("exists")))(input)
}

fn contradiction(input: &str) -> IResult<&str, Expr> {
    value(Expr::Contra, alt((tag("_|_"), tag("⊥"))))(input)
}

fn tautology(input: &str) -> IResult<&str, Expr> {
    value(Expr::Taut, alt((tag("^|^"), tag("⊤"))))(input)
}

fn notterm(input: &str) -> IResult<&str, Expr> {
    map(preceded(alt((tag("~"), tag("¬"))), paren_expr), |e| Expr::Not { operand: Box::new(e) })(input)
}

fn predicate(input: &str) -> IResult<&str, Expr> {
    alt((map(pair(delimited(space, variable, space), delimited(tag("("), separated_list0(tuple((space, tag(","), space)), expr), tag(")"))), |(name, args)| Expr::Apply { func: Box::new(Expr::Var { name }), args }), map(delimited(space, variable, space), |name| Expr::Var { name })))(input)
}

fn forall_quantifier(input: &str) -> IResult<&str, QuantKind> {
    value(QuantKind::Forall, alt((tag("forall "), tag("∀"))))(input)
}

fn exists_quantifier(input: &str) -> IResult<&str, QuantKind> {
    value(QuantKind::Exists, alt((tag("exists "), tag("∃"))))(input)
}

fn quantifier(input: &str) -> IResult<&str, QuantKind> {
    alt((forall_quantifier, exists_quantifier))(input)
}

fn binder(input: &str) -> IResult<&str, Expr> {
    map(tuple((preceded(space, quantifier), preceded(space, variable), preceded(tuple((space, tag(","), space)), expr))), |(kind, name, body)| Expr::Quant { kind, name, body: Box::new(body) })(input)
}

fn impl_term(input: &str) -> IResult<&str, Expr> {
    map(separated_pair(paren_expr, tuple((space, alt((tag("->"), tag("→"))), space)), paren_expr), |(left, right)| Expr::Impl { left: Box::new(left), right: Box::new(right) })(input)
}

fn andrepr(input: &str) -> IResult<&str, Op> {
    value(Op::And, alt((tag("&"), tag("∧"), tag("/\\"))))(input)
}

fn orrepr(input: &str) -> IResult<&str, Op> {
    value(Op::Or, alt((tag("|"), tag("∨"), tag("\\/"))))(input)
}

fn biconrepr(input: &str) -> IResult<&str, Op> {
    value(Op::Bicon, alt((tag("<->"), tag("↔"))))(input)
}

fn equivrepr(input: &str) -> IResult<&str, Op> {
    value(Op::Equiv, alt((tag("==="), tag("≡"))))(input)
}

fn plusrepr(input: &str) -> IResult<&str, Op> {
    value(Op::Add, tag("+"))(input)
}

fn multrepr(input: &str) -> IResult<&str, Op> {
    value(Op::Mult, tag("*"))(input)
}

fn assoc_term_aux(input: &str) -> IResult<&str, (Vec<Expr>, Vec<Op>)> {
    alt((
        map(tuple((paren_expr, delimited(space, alt((andrepr, orrepr, biconrepr, equivrepr, plusrepr, multrepr)), space), assoc_term_aux)), |(e, sym, (mut es, mut syms))| {
            es.push(e);
            syms.push(sym);
            (es, syms)
        }),
        map(paren_expr, |e| (vec![e], vec![])),
    ))(input)
}

/// Enforce that all symbols are the same.
/// This check is what rules out `(a /\ b \/ c)` without further parenthesization.
fn assoc_term(s: &str) -> nom::IResult<&str, Expr> {
    let (rest, (mut exprs, syms)) = assoc_term_aux(s)?;
    assert_eq!(exprs.len(), syms.len() + 1);
    if exprs.len() == 1 {
        return custom_error(rest);
    }
    let op = syms[0];
    if !syms.iter().all(|x| x == &op) {
        return custom_error(rest);
    }
    exprs.reverse();
    Ok((rest, Expr::Assoc { op, exprs }))
}

// paren_expr is a factoring of expr that eliminates left-recursion, which parser combinators have trouble with
fn paren_expr(input: &str) -> IResult<&str, Expr> {
    alt((contradiction, tautology, predicate, notterm, binder, delimited(tuple((space, tag("("), space)), expr, tuple((space, tag(")"), space)))))(input)
}

fn expr(input: &str) -> IResult<&str, Expr> {
    alt((assoc_term, impl_term, paren_expr))(input)
}

fn main(input: &str) -> IResult<&str, Expr> {
    terminated(expr, newline)(input)
}

#[test]
fn test_parser() {
    use crate::expr::free_vars;
    println!("{:?}", predicate("a(   b, c)"));
    println!("{:?}", predicate("s(s(s(s(s(z)))))"));
    println!("{:?}", expr("a & b & c(x,y)\n"));
    println!("{:?}", expr("forall a, (b & c)\n"));
    let e = expr("exists x, (Tet(x) & SameCol(x, b)) -> ~forall x, (Tet(x) -> LeftOf(x, b))\n").unwrap();
    let fv = free_vars(&e.1);
    println!("{e:?} {fv:?}");
    let e = expr("forall a, forall b, ((forall x, in(x,a) <-> in(x,b)) -> eq(a,b))\n").unwrap();
    let fv = free_vars(&e.1);
    assert_eq!(fv, ["eq", "in"].iter().map(|x| String::from(*x)).collect());
    println!("{e:?} {fv:?}");
    fn f(input: &str) -> IResult<&str, Vec<&str>> {
        many1(tag("a"))(input)
    }
    println!("{:?}", f("aa\n"));
}
