use varisat::{CnfFormula, Lit, Var, solver::Solver, checker::{ProofProcessor, CheckedProofStep}, ExtendFormula};
use crate::proofs::{Proof, pooledproof::PooledProof, Justification};
use crate::expression::{Expr, ASymbol, BSymbol, USymbol};
use crate::rules::{RuleM, Rule};
use std::collections::HashMap;
use std::fmt::{Debug, Display};

#[derive(Debug)]
struct SatProofBuilder<P: Proof>{
    proof: P,
    main_subproof: P::SubproofReference,
    next_premise_insertion: P::Reference,
    cnf_premise: P::Reference,
    premises: Vec<P::Reference>,
    cnf_conclusion: P::Reference,
    clause_id2ref: HashMap<u64, P::Reference>,
}
impl<P: Proof> SatProofBuilder<P> {
    fn new() -> SatProofBuilder<P> {
        let mut proof = P::new();
        let empty_and = Expr::AssocBinop { symbol: ASymbol::And, exprs: vec![] };
        let main_subproof = proof.add_subproof();
        let cnf_premise = proof.with_mut_subproof(&main_subproof, |sub| {
            sub.add_premise(empty_and.clone())
        }).unwrap();
        let cnf_conclusion = proof.add_step(Justification(
            Expr::Unop { symbol: USymbol::Not , operand: Box::new(empty_and) },
            RuleM::NotIntro, vec![], vec![main_subproof.clone()]));
        SatProofBuilder {
            proof,
            main_subproof,
            next_premise_insertion: cnf_premise.clone(),
            cnf_premise,
            cnf_conclusion,
            premises: vec![],
            clause_id2ref: HashMap::new(),
        }
    }

    fn lit_to_expr(&self, lit: &Lit) -> Expr {
        let pos = Expr::Var { name: format!("x{}", lit.index()) };
        if lit.is_positive() {
            pos
        } else {
            Expr::Unop { symbol: USymbol::Not, operand: Box::new(pos) }
        }
    }

    fn clause_to_expr(&self, clause: &[Lit]) -> Expr {
        let mut exprs = vec![];
        for lit in clause {
            exprs.push(self.lit_to_expr(lit));
        }
        if exprs.len() > 0 {
            Expr::AssocBinop { symbol: ASymbol::Or, exprs }
        } else {
            Expr::Contradiction
        }
    }

    fn add_clause(&mut self, id: u64, clause: &[Lit], rule: Rule, deps: Vec<P::Reference>, is_premise: bool) {
        let clause_expr = self.clause_to_expr(clause);
        let next_premise_clone = self.next_premise_insertion.clone();
        let just = Justification(clause_expr, rule, deps, vec![]);
        let r = self.proof.with_mut_subproof(&self.main_subproof, move |sub| {
            if is_premise {
                sub.add_step_relative(just, next_premise_clone, true)
            } else {
                sub.add_step(just)
            }
        }).unwrap();
        self.clause_id2ref.insert(id, r.clone());
        if is_premise {
            self.next_premise_insertion = r.clone();
        }
    }
}
impl<P: Proof+Debug+Display> ProofProcessor for SatProofBuilder<P> where P::Subproof: Debug, P::Reference: Debug, P::SubproofReference: Debug {
    fn process_step(&mut self, step: &CheckedProofStep) -> Result<(), failure::Error> {
        use self::CheckedProofStep::*;
        println!("{:?}", step);
        println!("{}", self.proof);
        // println!("{:?}", self);
        match step {
            AddClause { id, clause } => {
                let premise_clone = self.cnf_premise.clone();
                self.add_clause(*id, clause, RuleM::AndElim, vec![premise_clone], true);
                
            },
            AtClause { id, redundant:_, clause, propagations } => {
                let deps = propagations.iter().map(|p| self.clause_id2ref[p].clone()).collect::<Vec<_>>();
                self.add_clause(*id, clause, RuleM::AsymmetricTautology, deps, false);
            }
            _ => {},
        };
        Ok(())
    }
}

// Test case
// (a) && (~a || b) && (~b)
#[test]
fn test_generate_proof() {
    let mut cnf = CnfFormula::new();
    let a = Var::from_index(0);
    let b = Var::from_index(1);
    let a0 = Lit::from_var(a, false);
    let b0 = Lit::from_var(b, false);
    let a1 = Lit::from_var(a, true);
    let b1 = Lit::from_var(b, true);
    cnf.add_clause(&[a1]);
    cnf.add_clause(&[a0, b1]);
    cnf.add_clause(&[b0]);
    println!("{:?}",cnf);
    let mut foo = SatProofBuilder::<PooledProof<Hlist![Expr]>>::new();
    {
        let mut s = Solver::new();
        s.add_proof_processor(&mut foo);
        s.add_formula(&cnf);
        let r = s.solve();
        println!("{:?}", r);
    }
    println!("{}", foo.proof);
}
