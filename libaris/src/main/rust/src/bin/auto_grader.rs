extern crate libaris;

// This file builds the headless version of Aris,
// meant for verifying proofs submitted on Submitty.
#[macro_use] extern crate frunk;
use std::env;
use std::path::Path;
use std::fs::File;
use std::collections::HashSet;
use std::fmt::Debug;

use libaris::rules::ProofCheckError;
use libaris::proofs::{Proof, Justification};
use libaris::expression::Expr;
use libaris::proofs::xml_interop::proof_from_xml;
use libaris::proofs::lined_proof::LinedProof;

fn validate_recursive<P: Proof>(proof: &P, line: P::Reference) -> Result<(), (P::Reference, ProofCheckError<P::Reference, P::SubproofReference>)>
where P::Reference:Debug, P::SubproofReference:Debug {
    use ProofCheckError::*;
    use frunk::Coproduct::{Inl, Inr};
    let mut q = vec![line];

    // lookup returns either expr or Justification. if it returns the expr, it's done.
    // otherwise, 
    while let Some(r) = q.pop() {
        //println!("q: {:?} {:?}", r, q);
        proof.verify_line(&r).map_err(|e| (r.clone(), e))?;

        let line = proof.lookup(r.clone());
        //println!("line: {:?}", line);

        match line {
            None => { return Err((r.clone(), LineDoesNotExist(r.clone()))); },
            Some(Inl(_)) => {},
            Some(Inr(Inl(Justification(_, _, deps, sdeps)))) => {
                q.extend(deps);

                for sdep in sdeps.iter() {
                    let sub = proof.lookup_subproof_or_die(sdep.clone()).map_err(|e| (r.clone(), e))?;
                    q.extend(sub.direct_lines());
                }
            },
            Some(Inr(Inr(void))) => match void {},
        }
    }

    Ok(())
}

// Takes 2 files as args:
// First one is instructor assignment
//   Should have 1 top level proof w/ an arbitrary number of assumptions, only 1 step
// Second one is student assignment
//
// Assert that the assumptions are the same, that the step(goal) appears at the top level of the
// student assignment and that the goal is valid in the student proof all the way to the premises.

fn main() -> Result<(), String> {
    let args: Vec<_> = env::args().collect();

    if args.len() != 3 {
        return Err(format!("Usage: {} <instructor assignment> <student assignment>", args[0]));
    }

    let instructor_path = Path::new(&args[1]);
    let student_path = Path::new(&args[2]);

    let instructor_file = File::open(&instructor_path).expect("Could not open instructor file");
    let student_file = File::open(&student_path).expect("Could not open student file");

    type P = libaris::proofs::pooledproof::PooledProof<Hlist![Expr]>;

    let (i_prf, i_meta) = proof_from_xml::<P, _>(&instructor_file).unwrap();
    let (s_prf, s_meta) = proof_from_xml::<P, _>(&student_file).unwrap();

    let instructor_premises = i_prf.premises();
    let student_premises = s_prf.premises();

    // Adds the premises into two sets to compare them
    let instructor_set = instructor_premises.into_iter().map(|r| i_prf.lookup_expr(r)).collect::<Option<HashSet<Expr>>>().expect("Instructor set creation failed");
    let student_set = student_premises.into_iter().map(|r| s_prf.lookup_expr(r)).collect::<Option<HashSet<Expr>>>().expect("Student set creation failed");

    if instructor_set != student_set {
        return Err("Premises do not match!".into());
    }

    // Gets the top level lines
    let instructor_lines = i_prf.direct_lines();
    let student_lines = s_prf.direct_lines();

    // Verify that the goals are in the student lines and that the instructor's conclusion line matches some student's conclusion, and that the student's conclusion checks out using DFS.
    for i_goal in i_meta.goals {
        if let Some(i) = student_lines.iter().find(|i| s_prf.lookup_expr(*i.clone()).as_ref() == Some(&i_goal)) {
            match validate_recursive(&s_prf, *i) {
                Ok(()) => {},
                Err((r, e)) => return {
                    // Create a lined proof to get line numbers from line reference via linear search
                    let s_prf_with_lines = LinedProof::from_proof(s_prf.clone());
                    let (index, _) = s_prf_with_lines.lines.iter().enumerate().find(|(i, rl)| rl.reference == r)
                        .expect("Failed to find line number for building error message (BAD!!)");
                    println!("{}", s_prf);
                    Err(format!("validate_recursive failed for line {}: {}", index + 1, e))
                },
            }
        }

        else {
            return Err(format!("Goal {} is not in student proof.", i_goal.clone()));
        }
    }

    Ok(())
}

