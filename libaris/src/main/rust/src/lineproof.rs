use super::*;
use std::ops::Range;
use std::fmt::{Display, Formatter};

pub trait DisplayIndented {
    fn display_indented(&self, fmt: &mut Formatter, indent: usize, linecount: &mut usize) -> Result<(), std::fmt::Error>;
}

#[derive(Clone, Debug)]
pub struct Proof<T> {
    premises: Vec<(T, Expr)>,
    lines: Vec<Line<T>>,
}

#[derive(Clone, Debug)]
pub enum Line<T> {
    Direct(T, Expr, Rule, Vec<Range<usize>>),
    Subproof(Proof<T>),
}

#[derive(Clone, Copy, Debug)]
pub enum Rule {
    AndIntro, AndElim,
    OrIntro, OrElim,
    ImpIntro, ImpElim,
    NotIntro, NotElim,
    BotIntro, BotElim,
    ForallIntro, ForallElim,
    ExistsIntro, ExistsElim,
    Reit,
}

impl Rule {
    fn get_depcount(&self) -> Option<(usize, usize)> /* (lines, subproofs) */ {
        use Rule::*;
        match self {
            Reit | AndElim | OrIntro | NotElim | BotElim | ExistsIntro => Some((1, 0)),
            BotIntro | ImpElim | ForallElim => Some((2, 0)),
            NotIntro | ImpIntro | ForallIntro => Some((0, 1)),
            ExistsElim => Some((1, 1)),
            AndIntro | OrElim => None, // AndIntro and OrElim can have arbitrarily many conjuncts/disjuncts in one application

        }
    }
}

pub enum PremiseOrLine<T> { Premise(T, Expr), Line(T, Expr, Rule, Vec<Range<usize>>) }
impl<T> PremiseOrLine<T> {
    fn get_expr(&self) -> &Expr {
        match self {
            PremiseOrLine::Premise(_, ref e) => e,
            PremiseOrLine::Line(_, ref e, _, _) => e,
        }
    }
}

impl Display for Proof<()> {
    fn fmt(&self, fmt: &mut Formatter) -> std::result::Result<(), std::fmt::Error> { self.display_indented(fmt, 1, &mut 1) }
}

impl DisplayIndented for Proof<()> {
    fn display_indented(&self, fmt: &mut Formatter, indent: usize, linecount: &mut usize) -> std::result::Result<(), std::fmt::Error> {
        for (_, premise) in self.premises.iter() {
            write!(fmt, "{}:\t", linecount)?;
            for _ in 0..indent { write!(fmt, "| ")?; }
            write!(fmt, "{:?}\n", premise)?; // TODO Display for Expr
            *linecount += 1;
        }
        write!(fmt, "\t")?;
        for _ in 0..indent { write!(fmt, "| ")?; }
        for _ in 0..10 { write!(fmt, "-")?; }
        write!(fmt, "\n")?;
        for line in self.lines.iter() {
            line.display_indented(fmt, indent, linecount)?;
        }
        Ok(())
    }
}

impl DisplayIndented for Line<()> {
    fn display_indented(&self, fmt: &mut Formatter, indent: usize, linecount: &mut usize) -> std::result::Result<(), std::fmt::Error> {
        match self {
            Line::Direct(_, expr, rule, deps) => {
                write!(fmt, "{}:\t", linecount)?;
                *linecount += 1;
                for _ in 0..indent { write!(fmt, "| ")?; }
                write!(fmt, "{:?}; {:?}; ", expr, rule)?;
                for (i, &Range { start, end }) in deps.iter().enumerate() {
                    if start == end {
                        write!(fmt, "{}", start)?;
                    } else {
                        write!(fmt, "{}-{}", start, end)?;
                    }
                    if i != deps.len()-1 { write!(fmt, ", ")?; }
                }
                write!(fmt, "\n")
            }
            Line::Subproof(prf) => (*prf).display_indented(fmt, indent+1, linecount),
        }
    }
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct LineAndIndent { line: usize, indent: usize }

pub fn decorate_line_and_indent<T>(prf: Proof<T>) -> Proof<(LineAndIndent, T)> {
    fn aux<T>(mut prf: Proof<T>, li: &mut LineAndIndent) -> Proof<(LineAndIndent, T)> {
        let mut result = Proof { premises: vec![], lines: vec![] };
        for (data, premise) in prf.premises.drain(..) {
            result.premises.push(((li.clone(), data), premise));
            li.line += 1;
        }
        for line in prf.lines.drain(..) {
            match line {
                Line::Direct(data, expr, rule, deps) => { result.lines.push(Line::Direct((li.clone(), data), expr, rule, deps)); li.line += 1; },
                Line::Subproof(sub) => { li.indent += 1; result.lines.push(Line::Subproof(aux(sub, li))); li.indent -= 1; },
            }
        }
        result
    }
    aux(prf, &mut LineAndIndent { line: 1, indent: 1 })
}

pub fn map_decoration<T, U, F: FnMut(T) -> U>(mut prf: Proof<T>, f: &mut F) -> Proof<U> {
    let mut result = Proof { premises: vec![], lines: vec![] };
    for (data, premise) in prf.premises.drain(..) { result.premises.push((f(data), premise)); }
    for line in prf.lines.drain(..) {
        match line {
            Line::Direct(data, expr, rule, deps) => result.lines.push(Line::Direct(f(data), expr, rule, deps)),
            Line::Subproof(sub) => result.lines.push(Line::Subproof(map_decoration(sub, f))),
        }
    }
    result
}

fn lookup_by_decoration<T: Clone, F: Fn(&T) -> bool>(prf: &Proof<T>, f: &F) -> Option<PremiseOrLine<T>> {
    for (data, premise) in prf.premises.iter() {
        if f(data) {
            return Some(PremiseOrLine::Premise(data.clone(), premise.clone()));
        }
    }
    for line in prf.lines.iter() {
        match line {
            Line::Direct(data, expr, rule, deps) => {
                if f(data) {
                    return Some(PremiseOrLine::Line(data.clone(), expr.clone(), *rule, deps.clone()));
                }
            },
            Line::Subproof(sub) => {
                if let Some(ret) = lookup_by_decoration(sub, f) {
                    return Some(ret);
                }
            }
        }
    }
    None
}

#[derive(Debug, PartialEq, Eq)]
pub enum ProofCheckError {
    LineDoesNotExist(usize),
    ReferencesLaterLine(LineAndIndent, usize),
    IncorrectDepCount(Vec<Range<usize>>, usize, usize),
    DepOfWrongForm(String),
    DoesNotOccur(Expr, Expr),
}

pub fn check_rule_at_line(prf: &Proof<LineAndIndent>, i: usize) -> Result<(), ProofCheckError> {
    if let Some(pol) = lookup_by_decoration(prf, &|li| li.line == i) {
        match pol {
            PremiseOrLine::Premise(_, _) => Ok(()), // Premises are always valid
            PremiseOrLine::Line(li, expr, rule, deps) => { check_rule(prf, li, expr, rule, deps) }
        }
    } else {
        Err(ProofCheckError::LineDoesNotExist(i))
    }
}
fn check_rule(prf: &Proof<LineAndIndent>, li: LineAndIndent, expr: Expr, rule: Rule, deps: Vec<Range<usize>>) -> Result<(), ProofCheckError> {
    use ProofCheckError::*;
    for &Range { start, end } in deps.iter() {
        assert!(start <= end); // this should be enforced by the GUI, and hence not a user-facing error message
        if end >= li.line {
            return Err(ReferencesLaterLine(li, end))
        }
    }
    if let Some((directs, subs)) = rule.get_depcount() {
        let (mut d, mut s) = (0, 0);
        for &Range { start, end } in deps.iter() {
            if start == end { d += 1 } else { s += 1 }
        }
        if d != directs || s != subs {
            return Err(IncorrectDepCount(deps, directs, subs));
        }
    }
    match rule {
        Rule::AndIntro => unimplemented!(),
        Rule::AndElim => {
            let Range { start, end } = deps[0];
            if let Some(prem) = lookup_by_decoration(prf, &|li| li.line == start) {
                if let Expr::AssocBinop { symbol: ASymbol::And, exprs } = prem.get_expr() {
                    for e in exprs.iter() {
                        if e == &expr {
                            return Ok(());
                        }
                    }
                    return Err(DoesNotOccur(expr, prem.get_expr().clone()));
                } else {
                    return Err(DepOfWrongForm("expected an and-expression".into()));
                }
            } else {
                return Err(LineDoesNotExist(start))
            }
        },
        Rule::OrIntro => unimplemented!(),
        Rule::OrElim => unimplemented!(),
        Rule::ImpIntro => unimplemented!(),
        Rule::ImpElim => unimplemented!(),
        Rule::NotIntro => unimplemented!(),
        Rule::NotElim => unimplemented!(),
        Rule::BotIntro => unimplemented!(),
        Rule::BotElim => unimplemented!(),
        Rule::ForallIntro => unimplemented!(),
        Rule::ForallElim => unimplemented!(),
        Rule::ExistsIntro => unimplemented!(),
        Rule::ExistsElim => unimplemented!(),
        Rule::Reit => unimplemented!(),
    }
}

#[test]
fn test_andelim() {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let prf = Proof {
        premises: vec![
            ((), p("A & B & C & D")), // 1
            ((), p("E | F")) // 2
        ],
        lines: vec![
            Line::Direct((), p("A"), Rule::AndElim, vec![1..1]), // 3
            Line::Direct((), p("E"), Rule::AndElim, vec![1..1]), // 4
            Line::Direct((), p("A"), Rule::AndElim, vec![1..1, 1..1]), // 5
            Line::Direct((), p("A"), Rule::AndElim, vec![2..2]), // 6
        ],
    };
    println!("{}", prf);
    let prf = map_decoration(decorate_line_and_indent(prf), &mut |(li, ())| li);
    use ProofCheckError::*;
    assert_eq!(check_rule_at_line(&prf, 3), Ok(()));
    assert_eq!(check_rule_at_line(&prf, 4), Err(DoesNotOccur(p("E"), p("A & B & C & D"))));
    assert_eq!(check_rule_at_line(&prf, 5), Err(IncorrectDepCount(vec![1..1, 1..1], 1, 0)));
    assert!(if let Err(DepOfWrongForm(_)) = check_rule_at_line(&prf, 6) { true } else { false });
}

#[test]
fn demo_prettyprinting() {
    let p = |s: &str| { let t = format!("{}\n", s); parser::main(&t).unwrap().1 };
    let proof1 = Proof {
        premises: vec![((),p("A")), ((),p("B"))],
        lines: vec![
            Line::Direct((), p("A & B"), Rule::AndIntro, vec![1..1, 2..2]),
            Line::Subproof(Proof {
                premises: vec![((),p("C"))],
                lines: vec![Line::Direct((), p("A & B"), Rule::Reit, vec![3..3])],
            }),
            Line::Direct((), p("C -> (A & B)"), Rule::ImpIntro, vec![4..5]),
        ],
    };
    let proof2 = decorate_line_and_indent(proof1.clone());
    println!("{:?}\n{}\n{:?}", proof1, proof1, proof2);
}
