//! Parse infix logical expressions into an AST
//!
//! This file defines a parser for converting infix logical expressions into an Abstract Syntax Tree (AST).
//! It utilizes the 'nom' parser combinator library to handle parsing logic. The resulting AST can represent
//! various logical constructs, such as predicates, quantifiers, tautologies, contradictions, and operators
//! (e.g., AND, OR, IMPLIES).
//!
//! ## Main Functions
//! - 'parse': Converts a logical expression string into an AST ('Expr') or returns 'None' if parsing fails.
//! - 'parse_unwrap': Like 'parse', but panics on failure. Primarily used for testing.
//!
//! ## Grammar and Parsing Notes
//! - The parser handles infix logical expressions with support for parentheses, quantifiers, and operators.
//! - Functions are modular and correspond to specific grammar productions in Extended Backus-Naur Form (EBNF).
//! - The parser includes support for Unicode symbols (e.g., '∀', '∃', '∧', '∨').

use nom::branch::alt;
use nom::bytes::complete::tag;
use nom::character::complete::newline;
use nom::character::complete::one_of;
use nom::combinator::map;
use nom::combinator::peek;
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
    let no_comments: String = input.lines()
        .map(|line| line.split(';').next().unwrap_or("").trim()) // Remove everything after ';' and trim
        .collect::<Vec<_>>()
        .join("\n"); // Rejoin the cleaned lines

    let newlined = format!("{no_comments}\n");
    main(&newlined).map(|(_, expr)| expr).ok()
}

/// parser::parse_unwrap is a convenience function used in the tests, and panics if the input doesn't parse
/// for handling user input, call parser::parse instead and handle the None case
pub fn parse_unwrap(input: &str) -> Expr {
    parse(input).unwrap_or_else(|| panic!("failed parsing: {input}"))
}

/// Custom error helper function for parser failure
fn custom_error<A, B>(a: A) -> nom::IResult<A, B> {
    Err(nom::Err::Error(nom::error::Error { input: a, code: nom::error::ErrorKind::Fail }))
}

/// Parses a variable, ensuring it is not a reserved keyword
fn variable(input: &str) -> nom::IResult<&str, String> {
    verify(variable_, |v| keyword(v).is_err())(input)
}

// All the functions below can be thought of as grammar productions interleaved with code that constructs the AST value associated with each production.
// `alt` corresponds to alternation/choice in an EBNF grammar
// `tag` is used for literal string values, and supports unicode

/// Matches whitespace characters (spaces or tabs)
fn space(input: &str) -> IResult<&str, ()> {
    value((), many0(one_of(" \t")))(input)
}

/// Matches variable-like identifiers (alphanumeric or underscores)
fn variable_(input: &str) -> IResult<&str, String> {
    map(recognize(many1(one_of("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_"))), |v: &str| v.to_owned())(input)
}

/// Matches logical keywords ('forall' or 'exists')
fn keyword(input: &str) -> IResult<&str, &str> {
    alt((tag("forall"), tag("exists")))(input)
}

/// Parses a logical contradiction (e.g., '_⊥_')
fn contradiction(input: &str) -> IResult<&str, Expr> {
    value(Expr::Contra, alt((tag("_|_"), tag("⊥"))))(input)
}

/// Parses a logical tautology (e.g., '⊤')
fn tautology(input: &str) -> IResult<&str, Expr> {
    value(Expr::Taut, alt((tag("^|^"), tag("⊤"))))(input)
}

/// Parses a negation term (e.g., '¬A')
fn notterm(input: &str) -> IResult<&str, Expr> {
    map(preceded(alt((tag("~"), tag("¬"))), paren_expr), |e| Expr::Not { operand: Box::new(e) })(input)
}

/// Parses a predicate or variable term
fn predicate(input: &str) -> IResult<&str, Expr> {
    alt((map(pair(delimited(space, variable, space), delimited(tag("("), separated_list0(tuple((space, tag(","), space)), expr), tag(")"))), |(name, args)| Expr::Apply { func: Box::new(Expr::Var { name }), args }), map(delimited(space, variable, space), |name| Expr::Var { name })))(input)
}

/// Parses a universal quantifier ('∀') and associates it with an expression
fn forall_quantifier(input: &str) -> IResult<&str, QuantKind> {
    value(QuantKind::Forall, alt((tag("forall "), tag("∀"))))(input)
}

/// Parses an existential quantifier ('∃') and associates it with an expression
fn exists_quantifier(input: &str) -> IResult<&str, QuantKind> {
    value(QuantKind::Exists, alt((tag("exists "), tag("∃"))))(input)
}

/// Parses any quantifier ('∀' or '∃')
fn quantifier(input: &str) -> IResult<&str, QuantKind> {
    alt((forall_quantifier, exists_quantifier))(input)
}

/// Matches whitespace characters after quantifier
fn space_after_quantifier(input: &str) -> IResult<&str, ()> {
    value((), many1(one_of(" \t")))(input)
}

/// Matches whitespace characters depending on if there exists a quantifier or not
fn conditional_space(input: &str) -> IResult<&str, ()> {
    let is_next_quantifier = peek(quantifier)(input);

    match is_next_quantifier {
        Ok(_) => value((), space)(input),
        Err(_) => value((), space_after_quantifier)(input),
    }
}

/// Parses a logical binder (quantifier + variable + body)
fn binder(input: &str) -> IResult<&str, Expr> {
    map(
        tuple((
            preceded(space, quantifier),
            preceded(space, variable),
            preceded(
                conditional_space,
                alt((
                    // Parse multiple terms enclosed in parentheses
                    delimited(tuple((space, tag("("), space)), expr, tuple((space, tag(")"), space))),
                    // Parse a single term without parentheses
                    paren_expr,
                )),
            ),
        )),
        |(kind, name, body)| Expr::Quant { kind, name, body: Box::new(body) },
    )(input)
}

/// Parses an implication term (e.g., 'A -> B' or 'A → B')
fn impl_term(input: &str) -> IResult<&str, Expr> {
    map(separated_pair(paren_expr, tuple((space, alt((tag("->"), tag("→"))), space)), paren_expr), |(left, right)| Expr::Impl { left: Box::new(left), right: Box::new(right) })(input)
}

/// Parses an AND operator (e.g., '&', '∧', or '/\')
fn andrepr(input: &str) -> IResult<&str, Op> {
    value(Op::And, alt((tag("&"), tag("∧"), tag("/\\"))))(input)
}

/// Parses an OR operator (e.g., '|', '∨', or '\/')
fn orrepr(input: &str) -> IResult<&str, Op> {
    value(Op::Or, alt((tag("|"), tag("∨"), tag("\\/"))))(input)
}

/// Parses a biconditional operator (e.g., '<->' or '↔')
fn biconrepr(input: &str) -> IResult<&str, Op> {
    value(Op::Bicon, alt((tag("<->"), tag("↔"))))(input)
}

/// Parses an equivalence operator (e.g., '===' or '≡')/// Parses an equivalence operator (e.g., '===' or '≡')
fn equivrepr(input: &str) -> IResult<&str, Op> {
    value(Op::Equiv, alt((tag("==="), tag("≡"))))(input)
}

/// Parses an addition operator ('+')
fn plusrepr(input: &str) -> IResult<&str, Op> {
    value(Op::Add, tag("+"))(input)
}

/// Parses a multiplication operator ('*')
fn multrepr(input: &str) -> IResult<&str, Op> {
    value(Op::Mult, tag("*"))(input)
}

/// Parses a sequence of associative terms and their operators
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
    alt((
        contradiction,
        tautology,
        predicate,
        notterm,
        binder,
        delimited(tuple((space, tag("("), space)), expr, tuple((space, tag(")"), space)))
    ))(input)
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
    println!("{:?}", expr("forall a (b & c)\n"));
    let e = expr("exists x ((Tet(x) & SameCol(x, b)) -> ~forall x (Tet(x) -> LeftOf(x, b)))\n").unwrap();
    let fv = free_vars(&e.1);
    println!("{e:?} {fv:?}");
    let e = expr("forall a (forall b (((forall x (in(x,a) <-> in(x,b)) -> eq(a,b)))))\n").unwrap();
    let fv = free_vars(&e.1);
    assert_eq!(fv, ["eq", "in"].iter().map(|x| String::from(*x)).collect());
    println!("{e:?} {fv:?}");
    fn f(input: &str) -> IResult<&str, Vec<&str>> {
        many1(tag("a"))(input)
    }
    println!("{:?}", f("aa\n"));
}
