use super::*;
use frunk::coproduct::{Coproduct, CNil};
//use std::rc::{Rc, Weak};

#[derive(Clone, PartialEq, Eq, Hash)]
pub struct LineDep(pub usize);
#[derive(Clone, PartialEq, Eq, Hash)]
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
pub struct TreeProof<T, U>(pub TreeSubproof<T, U>);

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct TreeSubproof<T, U> {
    //pub root: Weak<TreeProof<T, U>>,
    pub premises: Vec<(T, Expr)>,
    pub lines: Vec<Line<T, U>>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Line<T, U> {
    Direct(T, Justification<Expr, Coproduct<LineDep, Coproduct<LineDep, CNil>>, SubproofDep>),
    Subproof(U, TreeSubproof<T, U>),
}

impl<T: Clone, U: Clone> TreeSubproof<T, U> {
    fn count_lines(&self) -> usize {
        let prf = decorate_subproof_sizes(TreeProof(self.clone()));
        prf.0.premises.len() + prf.0.lines.iter().map(|line| if let Line::Subproof((SubproofSize(n), _), _) = line { *n } else { 1 }).sum::<usize>()
    }
    fn lookup_line(&self, i: usize) -> Option<Coprod!(Expr, Justification<Expr, Coprod!(LineDep, LineDep), SubproofDep>)> {
        if i < self.premises.len() {
            return self.premises.get(i).map(|x| Coproduct::inject(x.1.clone()));
        }
        let mut j = self.premises.len();
        for line in self.lines.iter() {
            match line {
                Line::Direct(_, just) => { if j == i { return Some(Coproduct::inject(just.clone())); } else { j += 1; } },
                Line::Subproof(_, sub) => { if let Some(x) = sub.lookup_line(i-j) { return Some(x); } else { j += sub.count_lines(); } }
            }
        }
        assert!(j >= i);
        None
    }
}

impl<T: Clone+Default, U: Clone+Default> Proof for TreeProof<T, U> {
    type PremiseReference = LineDep;
    type JustificationReference = LineDep;
    type SubproofReference = SubproofDep;
    type Subproof = Self;
    fn new() -> Self { TreeProof(TreeSubproof { premises: vec![], lines: vec![] }) }
    fn top_level_proof(&self) -> &Self { self }
    fn lookup_premise(&self, LineDep(line): &Self::PremiseReference) -> Option<Expr> {
        self.0.lookup_line(line-1).and_then(|x| x.fold(hlist![|e| Some(e), |_| None]))
    }
    fn lookup_step(&self, LineDep(line): &Self::JustificationReference) -> Option<Justification<Expr, PJRef<Self>, Self::SubproofReference>> {
        self.0.lookup_line(line-1).and_then(|x| x.fold(hlist![|_| None, |j| Some(j)]))
    }
    fn lookup_subproof(&self, SubproofDep(_): &Self::SubproofReference) -> Option<Self::Subproof> {
        None // TODO: implement
    }
    /*unsafe fn lookup_subproof_mut(&mut self, _: Self::SubproofReference) -> Option<&mut Self::Subproof> {
        None // TODO: implement
    }*/
    fn with_mut_premise<A, F: FnOnce(&mut Expr) -> A>(&mut self, _: &Self::PremiseReference, _: F) -> Option<A> {
        unimplemented!()
    }
    fn with_mut_step<A, F: FnOnce(&mut Justification<Expr, PJRef<Self>, Self::SubproofReference>) -> A>(&mut self, _: &Self::JustificationReference, _: F) -> Option<A> {
        unimplemented!()
    }
    fn with_mut_subproof<A, F: FnOnce(&mut Self::Subproof) -> A>(&mut self, _: &Self::SubproofReference, _: F) -> Option<A> {
        unimplemented!();
    }
    fn add_premise(&mut self, e: Expr) -> Self::PremiseReference { self.0.premises.push((Default::default(), e)); let i = self.0.premises.len(); LineDep(i) }
    fn add_subproof(&mut self) -> Self::SubproofReference { let i = self.0.count_lines(); self.0.lines.push(Line::Subproof(Default::default(), TreeSubproof { premises: vec![], lines: vec![] })); let j = self.0.count_lines(); SubproofDep((i+1)..j) }
    fn add_step(&mut self, just: Justification<Expr, PJRef<Self>, Self::SubproofReference>) -> Self::JustificationReference { self.0.lines.push(Line::Direct(Default::default(), just)); let i = self.0.count_lines(); LineDep(i) }
    fn add_premise_relative(&mut self, _: Expr, _: &Self::PremiseReference, _: bool) -> Self::PremiseReference { unimplemented!() }
    fn add_subproof_relative(&mut self, _: &Self::JustificationReference, _: bool) -> Self::SubproofReference { unimplemented!() }
    fn add_step_relative(&mut self, _: Justification<Expr, PJRef<Self>, Self::SubproofReference>, _: &Self::JustificationReference, _: bool) -> Self::JustificationReference { unimplemented!() }
    fn remove_line(&mut self, _: &PJRef<Self>) { unimplemented!() }
    fn remove_subproof(&mut self, _: &Self::SubproofReference) { unimplemented!() }
    fn premises(&self) -> Vec<Self::PremiseReference> {
        //let prf = decorate_references(self.clone());
        //let res = vec![];
        unimplemented!();
    }
    fn lines(&self) -> Vec<Coprod!(Self::JustificationReference, Self::SubproofReference)> { unimplemented!() }
    fn parent_of_line(&self, _: &Coprod!(Self::PremiseReference, Self::JustificationReference, Self::SubproofReference)) -> Option<Self::SubproofReference> { unimplemented!() }
    fn verify_line(&self, x: &PJRef<Self>) -> Result<(), ProofCheckError<PJRef<Self>, Self::SubproofReference>> {
        let i = x.clone().fold(hlist![|LineDep(i)| i, |LineDep(i)| i]);
        let prf = decorate_line_and_indent(self.clone()).bimap(&mut |(x,_)| x, &mut |_| ());
        check_rule_at_line(&prf, i)
    }
}


impl<A, B> /* Bifunctor for */ TreeProof<A, B> {
    pub fn bimap<C, D, F: FnMut(A) -> C, G: FnMut(B) -> D>(self, f: &mut F, g: &mut G) -> TreeProof<C, D> {
        TreeProof(self.0.bimap(f, g))
    }
}
impl<A, B> /* Bifunctor for */ TreeSubproof<A, B> {
    pub fn bimap<C, D, F: FnMut(A) -> C, G: FnMut(B) -> D>(mut self, f: &mut F, g: &mut G) -> TreeSubproof<C, D> {
        TreeSubproof {
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

pub enum PremiseOrLine<T> { Premise(T, Expr), Line(T, Justification<Expr, Coprod!(LineDep, LineDep), SubproofDep>) }
impl<T> PremiseOrLine<T> {
    fn get_expr(&self) -> &Expr {
        match self {
            PremiseOrLine::Premise(_, ref e) => e,
            PremiseOrLine::Line(_, Justification(ref e, _, _, _)) => e,
        }
    }
}

impl Display for TreeProof<(),()> {
    fn fmt(&self, fmt: &mut Formatter) -> std::result::Result<(), std::fmt::Error> { self.0.fmt(fmt) }
}
impl Display for TreeSubproof<(),()> {
    fn fmt(&self, fmt: &mut Formatter) -> std::result::Result<(), std::fmt::Error> { self.display_indented(fmt, 1, &mut 1) }
}

impl DisplayIndented for TreeSubproof<(),()> {
    fn display_indented(&self, fmt: &mut Formatter, indent: usize, linecount: &mut usize) -> std::result::Result<(), std::fmt::Error> {
        for (_, premise) in self.premises.iter() {
            write!(fmt, "{}:\t", linecount)?;
            for _ in 0..indent { write!(fmt, "| ")?; }
            write!(fmt, "{}\n", premise)?;
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
    fn aux<T, U>(mut prf: TreeSubproof<T, U>, li: &mut LineAndIndent) -> TreeSubproof<(LineAndIndent, T), U> {
        let mut result = TreeSubproof { premises: vec![], lines: vec![] };
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
    TreeProof(aux(prf.0, &mut LineAndIndent { line: 1, indent: 1 }))
}

#[derive(Clone, Copy, Debug)]
pub struct SubproofSize(usize);
pub fn decorate_subproof_sizes<T, U>(prf: TreeProof<T, U>) -> TreeProof<T, (SubproofSize, U)> {
    fn aux<T, U>(mut prf: TreeSubproof<T, U>) -> (usize, TreeSubproof<T, (SubproofSize, U)>) {
        let mut size = prf.premises.len();
        let mut result = TreeSubproof { premises: prf.premises, lines: vec![] };
        for line in prf.lines.drain(..) {
            match line {
                Line::Direct(data, just) => { result.lines.push(Line::Direct(data, just)); size += 1; },
                Line::Subproof(data, sub) => { let (sz, sp) = aux(sub); size += sz; result.lines.push(Line::Subproof((SubproofSize(sz), data), sp)); }
            }
        }
        (size, result)
    }
    TreeProof(aux(prf.0).1)
}

// This is O(n) lookup for LineAndIndent line lookups, TODO: O(log(n)) line lookups via SubproofSize decorations
pub fn lookup_by_decoration<T: Clone, F: Fn(&T) -> bool, U>(prf: &TreeSubproof<T, U>, f: &F) -> Option<PremiseOrLine<T>> {
    for (data, premise) in prf.premises.iter() {
        if f(data) {
            return Some(PremiseOrLine::Premise(data.clone(), premise.clone()));
        }
    }
    for line in prf.lines.iter() {
        match line {
            Line::Direct(data, just) => {
                if f(&*data) {
                    return Some(PremiseOrLine::Line(data.clone(), just.clone()));
                }
            },
            Line::Subproof(_, sub) => {
                if let Some(ret) = lookup_by_decoration(&sub, f) {
                    return Some(ret);
                }
            }
        }
    }
    None
}

pub fn check_rule_at_line(prf: &TreeProof<LineAndIndent, ()>, i: usize) -> Result<(), ProofCheckError<Coprod!(LineDep, LineDep), SubproofDep>> {
    if let Some(pol) = lookup_by_decoration(&prf.0, &|li| li.line == i) {
        match pol {
            PremiseOrLine::Premise(_, _) => Ok(()), // Premises are always valid
            PremiseOrLine::Line(li, just) => { check_rule(prf, li, just) }
        }
    } else {
        Err(ProofCheckError::LineDoesNotExist(Coproduct::Inl(LineDep(i))))
    }
}

fn check_rule(prf: &TreeProof<LineAndIndent, ()>, li: LineAndIndent, Justification(expr, rule, deps, sdeps): Justification<Expr, Coprod!(LineDep, LineDep), SubproofDep>) -> Result<(), ProofCheckError<Coprod!(LineDep, LineDep), SubproofDep>> {
    use ProofCheckError::*;
    for x in deps.iter() {
        let i = x.clone().fold(hlist![|LineDep(i)| i, |LineDep(i)| i]);
        if i >= li.line {
            return Err(ReferencesLaterLine(Coproduct::Inr(Coproduct::Inl(LineDep(li.line))), Coproduct::Inl(x.clone())))
        }
    }
    for &SubproofDep(Range { start, end }) in sdeps.iter() {
        assert!(start <= end); // this should be enforced by the GUI, and hence not a user-facing error message
        if end >= li.line {
            return Err(ReferencesLaterLine(Coproduct::Inr(Coproduct::Inl(LineDep(li.line))), Coproduct::inject(SubproofDep(Range { start, end }))))
        }
    }
    rule.check(&prf.clone().bimap(&mut |_| (), &mut |_| ()), expr, deps, sdeps)
}
