//! This file builds the headless version of Aris,
//! meant for verifying proofs submitted on Submitty.

use aris::expr::Expr;
use aris::proofs::lined_proof::LinedProof;
use aris::proofs::xml_interop::proof_from_xml;
use aris::proofs::{Justification, PjRef, Proof};
use aris::rules::ProofCheckError;

use std::collections::HashSet;
use std::env;
use std::fmt::Debug;
use std::fs::File;
use std::path::Path;

use frunk_core::coproduct::Coproduct;
use frunk_core::Hlist;

type ValidateError<P> = (PjRef<P>, ProofCheckError<PjRef<P>, <P as Proof>::SubproofReference>);

fn validate_recursive<P: Proof>(proof: &P, line: PjRef<P>) -> Result<(), ValidateError<P>>
where
    PjRef<P>: Debug,
    P::SubproofReference: Debug,
{
    use Coproduct::{Inl, Inr};
    use ProofCheckError::*;
    let mut q = vec![line];

    // lookup returns either expr or Justification. if it returns the expr, it's done.
    // otherwise,
    while let Some(r) = q.pop() {
        //println!("q: {:?} {:?}", r, q);
        proof.verify_line(&r).map_err(|e| (r.clone(), e))?;

        let line = proof.lookup_pj(&r);
        //println!("line: {:?}", line);

        match line {
            None => {
                return Err((r.clone(), LineDoesNotExist(r)));
            }
            Some(Inl(_)) => {}
            Some(Inr(Inl(Justification(_, _, deps, sdeps)))) => {
                q.extend(deps);

                for sdep in sdeps.iter() {
                    let sub = proof.lookup_subproof_or_die(sdep).map_err(|e| (r.clone(), e))?;
                    q.extend(sub.direct_lines().into_iter().map(Coproduct::inject));
                }
            }
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

    type P = aris::proofs::pooledproof::PooledProof<Hlist![Expr]>;

    let (i_prf, i_meta) = proof_from_xml::<P, _>(&instructor_file).unwrap();
    let (s_prf, _) = proof_from_xml::<P, _>(&student_file).unwrap();

    let instructor_premises = i_prf.premises();
    let student_premises = s_prf.premises();

    // Adds the premises into two sets to compare them
    let instructor_set = instructor_premises.into_iter().map(|r| i_prf.lookup_premise(&r)).collect::<Option<HashSet<Expr>>>().expect("Instructor set creation failed");
    let student_set = student_premises.into_iter().map(|r| s_prf.lookup_premise(&r)).collect::<Option<HashSet<Expr>>>().expect("Student set creation failed");

    if instructor_set != student_set {
        return Err("Premises do not match!".into());
    }

    // Gets the top level lines
    let _ = i_prf.direct_lines();
    let student_lines = s_prf.direct_lines();

    // Verify that the goals are in the student lines and that the instructor's conclusion line matches some student's conclusion, and that the student's conclusion checks out using DFS.
    for i_goal in i_meta.goals {
        if let Some(i) = student_lines.iter().find(|i| s_prf.lookup_expr(&Coproduct::inject(**i)).as_ref() == Some(&i_goal)) {
            match validate_recursive(&s_prf, Coproduct::inject(*i)) {
                Ok(()) => {}
                Err((r, e)) => {
                    return {
                        // Create a lined proof to get line numbers from line reference via linear search
                        let s_prf_with_lines = LinedProof::from_proof(s_prf.clone());
                        let (index, _) = s_prf_with_lines.lines.iter().enumerate().find(|(_, rl)| rl.reference == r).expect("Failed to find line number for building error message (BAD!!)");
                        eprintln!("{}", s_prf);
                        Err(format!("validate_recursive failed for line {}: {}", index + 1, e))
                    };
                }
            }
        } else {
            return Err(format!("Goal {} is not in student proof.", i_goal.clone()));
        }
    }

    Ok(())
}
