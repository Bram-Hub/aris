use super::*;

#[derive(Clone, PartialEq, Eq)]
pub struct LineDep(pub usize);
#[derive(Clone, PartialEq, Eq)]
pub struct SubproofDep(pub Range<usize>);

impl std::fmt::Debug for LineDep {
    fn fmt(&self, fmt: &mut Formatter) -> std::result::Result<(), std::fmt::Error> {
        let &LineDep(i) = self;
        write!(fmt, "{}", i)
    }
}
impl std::fmt::Debug for SubproofDep {
    fn fmt(&self, fmt: &mut Formatter) -> std::result::Result<(), std::fmt::Error> {
        let &SubproofDep(Range { start, end }) = self;
        write!(fmt, "{}-{}", start, end)
    }
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct TreeProof<T, U> {
    pub premises: Vec<(T, Expr)>,
    pub lines: Vec<Line<T, U>>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Line<T, U> {
    Direct(T, Justification<Expr, LineDep, SubproofDep>),
    Subproof(U, TreeProof<T, U>),
}

impl TreeProof<(), ()> {
    fn count_lines(&self) -> usize {
        let prf = decorate_subproof_sizes(self.clone());
        prf.premises.len() + prf.lines.iter().map(|line| if let Line::Subproof((SubproofSize(n), ()), _) = line { *n } else { 1 }).sum::<usize>()
    }
    fn lookup_line(&self, i: usize) -> Option<Coprod!(Expr, Justification<Expr, LineDep, SubproofDep>)> {
        if i < self.premises.len() {
            return self.premises.get(i).map(|x| Coproduct::inject(x.1.clone()));
        }
        let mut j = self.premises.len();
        for line in self.lines.iter() {
            match line {
                Line::Direct((), just) => { if j == i { return Some(Coproduct::inject(just.clone())); } else { j += 1; } },
                Line::Subproof((), sub) => { if let Some(x) = sub.lookup_line(i-j) { return Some(x); } else { j += sub.count_lines(); } }
            }
        }
        assert!(j >= i);
        None
    }
}

impl Proof for TreeProof<(), ()> {
    type Reference = LineDep;
    type SubproofReference = SubproofDep;
    fn new() -> Self { TreeProof { premises: vec![], lines: vec![] } }
    fn lookup(&self, LineDep(line): Self::Reference) -> Option<Coprod!(Expr, Justification<Expr, Self::Reference, Self::SubproofReference>)> {
        self.lookup_line(line-1)
    }
    fn lookup_subproof(&self, SubproofDep(Range { start, end }): Self::SubproofReference) -> Option<Self> {
        None // TODO: implement
    }
    fn add_premise(&mut self, e: Expr) -> Self::Reference { self.premises.push(((), e)); let i = self.premises.len(); LineDep(i) }
    fn add_subproof(&mut self, sub: Self) -> Self::SubproofReference { let i = self.count_lines(); self.lines.push(Line::Subproof((), sub)); let j = self.count_lines(); SubproofDep((i+1)..j) }
    fn add_step(&mut self, just: Justification<Expr, Self::Reference, Self::SubproofReference>) -> Self::Reference { self.lines.push(Line::Direct((), just)); let i = self.count_lines(); LineDep(i) }
    fn premises(&self) -> Vec<Self::Reference> { unimplemented!() }
    fn lines(&self) -> Vec<Coprod!(Self::Reference, Self::SubproofReference)> { unimplemented!() }
}


impl<A, B> /* Bifunctor for */ TreeProof<A, B> {
    pub fn bimap<C, D, F: FnMut(A) -> C, G: FnMut(B) -> D>(mut self, f: &mut F, g: &mut G) -> TreeProof<C, D> {
        TreeProof {
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

pub enum PremiseOrLine<T> { Premise(T, Expr), Line(T, Justification<Expr, LineDep, SubproofDep>) }
impl<T> PremiseOrLine<T> {
    fn get_expr(&self) -> &Expr {
        match self {
            PremiseOrLine::Premise(_, ref e) => e,
            PremiseOrLine::Line(_, Justification(ref e, _, _, _)) => e,
        }
    }
}

impl Display for TreeProof<(),()> {
    fn fmt(&self, fmt: &mut Formatter) -> std::result::Result<(), std::fmt::Error> { self.display_indented(fmt, 1, &mut 1) }
}

impl DisplayIndented for TreeProof<(),()> {
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
            Line::Direct(_, just) => just.display_indented(fmt, indent, linecount),
            Line::Subproof(_, prf) => (*prf).display_indented(fmt, indent+1, linecount),
        }
    }
}

pub fn decorate_line_and_indent<T, U>(prf: TreeProof<T, U>) -> TreeProof<(LineAndIndent, T), U> {
    fn aux<T, U>(mut prf: TreeProof<T, U>, li: &mut LineAndIndent) -> TreeProof<(LineAndIndent, T), U> {
        let mut result = TreeProof { premises: vec![], lines: vec![] };
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
pub fn decorate_subproof_sizes<T, U>(prf: TreeProof<T, U>) -> TreeProof<T, (SubproofSize, U)> {
    fn aux<T, U>(mut prf: TreeProof<T, U>) -> (usize, TreeProof<T, (SubproofSize, U)>) {
        let mut size = prf.premises.len();
        let mut result = TreeProof { premises: prf.premises, lines: vec![] };
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
pub fn lookup_by_decoration<T: Clone, F: Fn(&T) -> bool, U>(prf: &TreeProof<T, U>, f: &F) -> Option<PremiseOrLine<T>> {
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

pub fn check_rule_at_line(prf: &TreeProof<LineAndIndent, ()>, i: usize) -> Result<(), ProofCheckError<LineDep, SubproofDep>> {
    if let Some(pol) = lookup_by_decoration(prf, &|li| li.line == i) {
        match pol {
            PremiseOrLine::Premise(_, _) => Ok(()), // Premises are always valid
            PremiseOrLine::Line(li, just) => { check_rule(prf, li, just) }
        }
    } else {
        Err(ProofCheckError::LineDoesNotExist(LineDep(i)))
    }
}

fn check_rule(prf: &TreeProof<LineAndIndent, ()>, li: LineAndIndent, Justification(expr, rule, deps, sdeps): Justification<Expr, LineDep, SubproofDep>) -> Result<(), ProofCheckError<LineDep, SubproofDep>> {
    use ProofCheckError::*;
    for &LineDep(i) in deps.iter() {
        if i >= li.line {
            return Err(ReferencesLaterLine(li, i))
        }
    }
    for &SubproofDep(Range { start, end }) in sdeps.iter() {
        assert!(start <= end); // this should be enforced by the GUI, and hence not a user-facing error message
        if end >= li.line {
            return Err(ReferencesLaterLine(li, end))
        }
    }
    rule.check(&prf.clone().bimap(&mut |_| (), &mut |_| ()), expr, deps, sdeps)
}
