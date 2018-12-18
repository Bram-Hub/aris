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

impl Display for Proof<()> {
    fn fmt(&self, fmt: &mut Formatter) -> std::result::Result<(), std::fmt::Error> { self.display_indented(fmt, 1, &mut 1) }
}

#[derive(Clone, Debug)]
pub enum Line<T> {
    Direct(T, Expr, Rule, Vec<Range<usize>>),
    Subproof(Proof<T>),
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

#[derive(Clone, Debug)]
pub struct LineAndIndent { line: usize, indent: usize }

fn decorate_line_and_indent<T>(prf: Proof<T>) -> Proof<(LineAndIndent, T)> {
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

fn map_decoration<T, U, F: FnMut(T) -> U>(mut prf: Proof<T>, f: &mut F) -> Proof<U> {
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

#[derive(Clone, Copy, Debug)]
pub enum Rule {
    AndIntro, AndElim,
    OrIntro, OrElim,
    ImpIntro, ImpElim,
    NotIntro, NotElim,
    ForallIntro, ForallElim,
    ExistsIntro, ExistsElim,
    Reit,
}

#[test]
fn demo() {
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
