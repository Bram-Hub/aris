package edu.rpi.aris.proof;

import edu.rpi.aris.assign.Problem;

public class ArisProofProblem implements Problem {

    private Proof proof;

    public ArisProofProblem(Proof proof) {
        this.proof = proof;
    }

    public Proof getProof() {
        return proof;
    }
}
