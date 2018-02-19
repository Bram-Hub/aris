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
        if (spPremises != subproofPremises(claim))
            return "Rule " + getName() + " requires exactly " + subproofPremises(claim) + " subproof(s) as premises";
        return verifyClaim(claim.getConclusion(), claim.getPremises());
    }

    public abstract String getName();

    public abstract String getSimpleName();

    protected abstract int requiredPremises(Claim claim);

    protected abstract boolean canGeneralizePremises();

    protected abstract int subproofPremises(Claim claim);

    protected abstract String verifyClaim(Expression conclusion, Premise[] premises);

}
