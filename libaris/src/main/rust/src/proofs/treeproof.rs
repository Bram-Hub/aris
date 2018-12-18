use super::*;

#[derive(Clone, Debug)]
pub struct Proof<T, U> {
    pub premises: Vec<(T, Expr)>,
    pub lines: Vec<Line<T, U>>,
}

#[derive(Clone, Debug)]
pub enum Line<T, U> {
    Direct(T, Justification<Range<usize>>),
    Subproof(U, Proof<T, U>),
}


impl<A, B> /* Bifunctor for */ Proof<A, B> {
    pub fn bimap<C, D, F: FnMut(A) -> C, G: FnMut(B) -> D>(mut self, f: &mut F, g: &mut G) -> Proof<C, D> {
        Proof {
            premises: self.premises.drain(..).map(|(data, premise)| (f(data), premise)).collect(),
            lines: self.lines.drain(..).map(|line| line.bimap(f, g)).collect(),
        }
    }
}

impl<A, B> /* Bifunctor for */ Line<A, B> {
    pub fn bimap<C, D, F: FnMut(A) -> C, G: FnMut(B) -> D>(self, f: &mut F, g: &mut G) -> Line<C, D> {
        match self {
            Line::Direct(data, just) => Line::Direct(f(data), just),
            Line::Subproof(data, sub) => Line::Subproof(g(data), sub.bimap(f, g)),
        }
    }
}

pub enum PremiseOrLine<T> { Premise(T, Expr), Line(T, Justification<Range<usize>>) }
impl<T> PremiseOrLine<T> {
    fn get_expr(&self) -> &Expr {
        match self {
            PremiseOrLine::Premise(_, ref e) => e,
            PremiseOrLine::Line(_, Justification(ref e, _, _)) => e,
        }
    }
}

impl Display for Proof<(),()> {
    fn fmt(&self, fmt: &mut Formatter) -> std::result::Result<(), std::fmt::Error> { self.display_indented(fmt, 1, &mut 1) }
}

impl DisplayIndented for Proof<(),()> {
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

impl DisplayIndented for Line<(), ()> {
    fn display_indented(&self, fmt: &mut Formatter, indent: usize, linecount: &mut usize) -> std::result::Result<(), std::fmt::Error> {
        match self {
            Line::Direct(_, Justification(expr, rule, deps)) => {
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
            Line::Subproof(_, prf) => (*prf).display_indented(fmt, indent+1, linecount),
        }
    }
}

pub fn decorate_line_and_indent<T, U>(prf: Proof<T, U>) -> Proof<(LineAndIndent, T), U> {
    fn aux<T, U>(mut prf: Proof<T, U>, li: &mut LineAndIndent) -> Proof<(LineAndIndent, T), U> {
        let mut result = Proof { premises: vec![], lines: vec![] };
        for (data, premise) in prf.premises.drain(..) {
            result.premises.push(((li.clone(), data), premise));
            li.line += 1;
        }
        for line in prf.lines.drain(..) {
            match line {
                Line::Direct(data, just) => { result.lines.push(Line::Direct((li.clone(), data), just)); li.line += 1; },
                Line::Subproof(data, sub) => { li.indent += 1; result.lines.push(Line::Subproof(data, aux(sub, li))); li.indent -= 1; },
            }
        }
        result
    }
    aux(prf, &mut LineAndIndent { line: 1, indent: 1 })
}

#[derive(Clone, Copy, Debug)]
pub struct SubproofSize(usize);
pub fn decorate_subproof_sizes<T, U>(prf: Proof<T, U>) -> Proof<T, (SubproofSize, U)> {
    fn aux<T, U>(mut prf: Proof<T, U>) -> (usize, Proof<T, (SubproofSize, U)>) {
        let mut result = Proof { premises: prf.premises, lines: vec![] };
        let mut size = 0;
        for line in prf.lines.drain(..) {
            match line {
                Line::Direct(data, just) => { result.lines.push(Line::Direct(data, just)); size += 1; },
                Line::Subproof(data, sub) => { let (sz, sp) = aux(sub); size += sz; result.lines.push(Line::Subproof((SubproofSize(sz), data), sp)); }
            }
        }
        (size, result)
    }
    aux(prf).1
}

// This is O(n) lookup for LineAndIndent line lookups, TODO: O(log(n)) line lookups via SubproofSize decorations
pub fn lookup_by_decoration<T: Clone, F: Fn(&T) -> bool, U>(prf: &Proof<T, U>, f: &F) -> Option<PremiseOrLine<T>> {
    for (data, premise) in prf.premises.iter() {
        if f(data) {
            return Some(PremiseOrLine::Premise(data.clone(), premise.clone()));
        }
    }
    for line in prf.lines.iter() {
        match line {
            Line::Direct(data, just) => {
                if f(data) {
                    return Some(PremiseOrLine::Line(data.clone(), just.clone()));
                }
            },
            Line::Subproof(_, sub) => {
                if let Some(ret) = lookup_by_decoration(sub, f) {
                    return Some(ret);
                }
            }
        }
    }
    None
}

pub fn check_rule_at_line(prf: &Proof<LineAndIndent, ()>, i: usize) -> Result<(), ProofCheckError> {
    if let Some(pol) = lookup_by_decoration(prf, &|li| li.line == i) {
        match pol {
            PremiseOrLine::Premise(_, _) => Ok(()), // Premises are always valid
            PremiseOrLine::Line(li, just) => { check_rule(prf, li, just) }
        }
    } else {
        Err(ProofCheckError::LineDoesNotExist(i))
    }
}
fn check_rule(prf: &Proof<LineAndIndent, ()>, li: LineAndIndent, Justification(expr, rule, deps): Justification<Range<usize>>) -> Result<(), ProofCheckError> {
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
            let Range { start, end: _ } = deps[0];
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
