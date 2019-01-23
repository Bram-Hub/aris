package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Claim;
import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Premise;

import java.util.ArrayList;

public abstract class Rule {

    Rule() {
    }

    public String verifyClaim(Claim claim) {
        if (canGeneralizePremises()) {
            if (claim.getPremises().length < requiredPremises())
                return "Rule " + getName() + " requires at least " + requiredPremises() + " premises";
        } else {
            if (claim.getPremises().length != requiredPremises())
                return "Rule " + getName() + " requires exactly " + requiredPremises() + " premises";
        }
        int spPremises = 0;
        for (Premise p : claim.getPremises())
            if (p.isSubproof())
                spPremises++;
        if (canGeneralizePremises()) {
            if (spPremises < subProofPremises())
                return "Rule " + getName() + " requires at least " + subProofPremises() + " subproof(s) as premises";
        } else {
            if (spPremises != subProofPremises())
                return "Rule " + getName() + " requires exactly " + subProofPremises() + " subproof(s) as premises";
        }
        return verifyClaim(claim.getConclusion(), claim.getPremises());
    }

    public ArrayList<String> getAutoFillCandidates(Premise[] premises) {
        if (premises == null)
            return null;
        if (!canGeneralizePremises() && premises.length != requiredPremises())
            return null;
        int spPremises = 0;
        for (Premise p : premises)
            if (p.isSubproof())
                spPremises++;
        if (!canGeneralizePremises() && spPremises != subProofPremises())
            return null;
        return getAutoFill(premises);
    }

    public abstract String getName();

    public abstract String getSimpleName();

    public abstract Type[] getRuleType();

    public abstract boolean canAutoFill();

    protected abstract ArrayList<String> getAutoFill(Premise[] premises);

    protected abstract int requiredPremises();

    public abstract boolean canGeneralizePremises();

    protected abstract int subProofPremises();

    protected abstract String verifyClaim(Expression conclusion, Premise[] premises);

    public enum Type {

        INTRO("Introduction"),
        ELIM("Elimination"),
        EQUIVALENCE("Equivalence"),
        INFERENCE("Inference"),
        PREDICATE("Predicate");

        public final String name;

        Type(String name) {
            this.name = name;
        }

    }

}
