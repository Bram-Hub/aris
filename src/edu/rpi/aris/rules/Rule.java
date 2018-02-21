package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Claim;
import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Premise;

public abstract class Rule {

    Rule() {
    }

    public String verifyClaim(Claim claim) {
        if (claim.getPremises().length != requiredPremises(claim))
            return "Rule " + getName() + " requires exactly " + requiredPremises(claim) + " premises";
        int spPremises = 0;
        for (Premise p : claim.getPremises())
            if (p.isSubproof())
                spPremises++;
        if (spPremises != subProofPremises(claim))
            return "Rule " + getName() + " requires exactly " + subProofPremises(claim) + " subproof(s) as premises";
        return verifyClaim(claim.getConclusion(), claim.getPremises());
    }

    public abstract String getName();

    public abstract String getSimpleName();

    public abstract Type[] getRuleType();

    protected abstract int requiredPremises(Claim claim);

    protected abstract boolean canGeneralizePremises();

    protected abstract int subProofPremises(Claim claim);

    protected abstract String verifyClaim(Expression conclusion, Premise[] premises);

    public enum Type {
        INFERENCE,
        EQUIVALENCE,
        ELIM,
        INTRO
    }

}
