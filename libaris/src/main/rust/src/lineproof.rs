use super::*;
use std::ops::Range;
use std::fmt::{Display, Formatter};

pub trait DisplayIndented {
    fn display_indented(&self, fmt: &mut Formatter, indent: usize, linecount: &mut usize) -> Result<(), std::fmt::Error>;
}

#[derive(Clone, Debug)]
pub struct Proof<L> {
    premises: Vec<Expr>,
    lines: Vec<L>,
}

impl<L: DisplayIndented> DisplayIndented for Proof<L> {
    fn display_indented(&self, fmt: &mut Formatter, indent: usize, linecount: &mut usize) -> std::result::Result<(), std::fmt::Error> {
        for premise in self.premises.iter() {
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

impl<L: DisplayIndented> Display for Proof<L> {
    fn fmt(&self, fmt: &mut Formatter) -> std::result::Result<(), std::fmt::Error> { self.display_indented(fmt, 1, &mut 1) }
}

#[derive(Clone, Debug)]
pub enum Line {
    Direct(Expr, Rule, Vec<Range<usize>>),
    Subproof(Proof<Line>),
}

impl DisplayIndented for Line {
    fn display_indented(&self, fmt: &mut Formatter, indent: usize, linecount: &mut usize) -> std::result::Result<(), std::fmt::Error> {
        match self {
            Line::Direct(expr, rule, deps) => {
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
        premises: vec![p("A"), p("B")],
        lines: vec![
            Line::Direct(p("A & B"), Rule::AndIntro, vec![1..1, 2..2]),
            Line::Subproof(Proof {
                premises: vec![p("C")],
                lines: vec![Line::Direct(p("A & B"), Rule::Reit, vec![3..3])],
            }),
            Line::Direct(p("C -> (A & B)"), Rule::ImpIntro, vec![4..5]),
        ],
    };
    println!("{:?}\n{}", proof1, proof1);
}
