use super::{Expr, USymbol, BSymbol, ASymbol, QSymbol};

/// parser::parse parses a string slice into an Expr AST, returning None if there's an error
pub fn parse(input: &str) -> Option<Expr> {
    let newlined = format!("{}\n", input);
    main(&newlined).map(|(_, expr)| expr).ok()
}

/// parser::parse_unwrap is a convenience function used in the tests, and panics if the input doesn't parse
/// for handling user input, call parser::parse instead and handle the None case
pub fn parse_unwrap(input: &str) -> Expr {
    parse(input).unwrap()
}

fn custom_error<A, B>(a: A, x: u32) -> nom::IResult<A, B> {
    return Err(nom::Err::Error(nom::Context::Code(a, nom::ErrorKind::Custom(x))));
}

/// variable is implemented as a function instead of via nom's macros in order to more conveniently reject keywords as variables
/// in nom5, the "verify" combinator does this properly, in nom4, the "verify" macro's predicate requires ownership of the return value
fn variable(s: &str) -> nom::IResult<&str, String> {
    let r = variable_(s);
    if let Ok((ref rest, ref var)) = r {
        if let Ok((_, _)) = keyword(&var) {
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

named!(contradiction<&str, Expr>, do_parse!(alt!(tag!("_|_") | tag!("⊥")) >> (Expr::Contradiction)));
named!(tautology<&str, Expr>, do_parse!(alt!(tag!("^|^") | tag!("⊤")) >> (Expr::Tautology)));

named!(notterm<&str, Expr>, do_parse!(alt!(tag!("~") | tag!("¬")) >> e: paren_expr >> (Expr::Unop { symbol: USymbol::Not, operand: Box::new(e) })));

named!(predicate<&str, Expr>, alt!(
    do_parse!(space >> name: variable >> space >> tag!("(") >> space >> args: separated_list!(do_parse!(space >> tag!(",") >> space >> (())), expr) >> tag!(")") >> (Expr::Apply { func: Box::new(Expr::Var { name }), args })) |
    do_parse!(space >> name: variable >> space >> (Expr::Var { name }))
    ));

named!(forall_quantifier<&str, QSymbol>, do_parse!(alt!(tag!("forall ") | tag!("∀")) >> (QSymbol::Forall)));
named!(exists_quantifier<&str, QSymbol>, do_parse!(alt!(tag!("exists ") | tag!("∃")) >> (QSymbol::Exists)));
named!(quantifier<&str, QSymbol>, alt!(forall_quantifier | exists_quantifier));
named!(binder<&str, Expr>, do_parse!(space >> symbol: quantifier >> space >> name: variable >> space >> tag!(",") >> space >> body: expr >> (Expr::Quantifier { symbol, name, body: Box::new(body) })));

named!(binop<&str, BSymbol>, alt!(do_parse!(alt!(tag!("->") | tag!("→")) >> (BSymbol::Implies)) | do_parse!(tag!("+") >> (BSymbol::Plus)) | do_parse!(tag!("*") >> (BSymbol::Mult))));
named!(binopterm<&str, Expr>, do_parse!(left: paren_expr >> space >> symbol: binop >> space >> right: paren_expr >> (Expr::Binop { symbol, left: Box::new(left), right: Box::new(right) })));

named!(andrepr<&str, ASymbol>, do_parse!(alt!(tag!("&") | tag!("∧") | tag!("/\\")) >> (ASymbol::And)));
named!(orrepr<&str, ASymbol>, do_parse!(alt!(tag!("|") | tag!("∨") | tag!("\\/")) >> (ASymbol::Or)));
named!(biconrepr<&str, ASymbol>, do_parse!(alt!(tag!("<->") | tag!("↔")) >> (ASymbol::Bicon)));
named!(equivrepr<&str, ASymbol>, do_parse!(alt!(tag!("===") | tag!("≡")) >> (ASymbol::Equiv)));

named!(assoctermaux<&str, (Vec<Expr>, Vec<ASymbol>)>, alt!(
    do_parse!(space >> e: paren_expr >> space >> sym: alt!(andrepr | orrepr | biconrepr | equivrepr) >> space >> rec: assoctermaux >> ({ let (mut es, mut syms) = rec; es.push(e); syms.push(sym); (es, syms) })) |
    do_parse!(e: paren_expr >> (vec![e], vec![]))
    ));

/// assocterm is implemented as a function instead of using nom's macros because
/// enforcing that all the symbols are the same is more easily done with iterators.
/// This check is what rules out `(a /\ b \/ c)` without further parenthesization.
fn assocterm(s: &str) -> nom::IResult<&str, Expr> {
    let (rest, (mut exprs, syms)) = assoctermaux(s)?;
    assert_eq!(exprs.len(), syms.len()+1);
    if exprs.len() == 1 {
        return custom_error(rest, 0);
    }
    let symbol = syms[0].clone();
    if !syms.iter().all(|x| x == &symbol) {
        return custom_error(rest, 0);
    }
    exprs.reverse();
    Ok((rest, Expr::AssocBinop { symbol, exprs }))
}

// paren_expr is a factoring of expr that eliminates left-recursion, which parser combinators have trouble with
named!(paren_expr<&str, Expr>, alt!(contradiction | tautology | predicate | notterm | binder | do_parse!(space >> tag!("(") >> space >> e: expr >> space >> tag!(")") >> space >> (e))));
named!(expr<&str, Expr>, alt!(assocterm | binopterm | paren_expr));
named!(main<&str, Expr>, do_parse!(e: expr >> tag!("\n") >> (e)));

#[test]
fn test_parser() {
    use super::freevars;
    println!("{:?}", predicate("a(   b, c)"));
    println!("{:?}", predicate("s(s(s(s(s(z)))))"));
    println!("{:?}", expr("a & b & c(x,y)\n"));
    println!("{:?}", expr("forall a, (b & c)\n"));
    let e = expr("exists x, (Tet(x) & SameCol(x, b)) -> ~forall x, (Tet(x) -> LeftOf(x, b))\n");
    let fv = e.clone().map(|x| freevars(&x.1));
    println!("{:?} {:?}", e, fv);
    let e = expr("forall a, forall b, ((forall x, in(x,a) <-> in(x,b)) -> eq(a,b))\n");
    let fv = e.clone().map(|x| freevars(&x.1));
    assert_eq!(fv, Ok(["eq", "in"].iter().map(|x| String::from(*x)).collect()));
    println!("{:?} {:?}", e, fv);
    named!(f<&str, Vec<&str>>, many1!(tag!("a")));
    println!("{:?}", f("aa\n"));
}
