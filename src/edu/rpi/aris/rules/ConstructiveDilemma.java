package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Claim;
import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Operator;
import edu.rpi.aris.proof.Premise;

public class ConstructiveDilemma extends Rule {

    ConstructiveDilemma() {
    }

    @Override
    public String getName() {
        return "Constructive Dilemma";
    }

    @Override
    public String getSimpleName() {
        return "CD";
    }

    @Override
    protected int requiredPremises(Claim claim) {
        return 3;
    }

    @Override
    protected boolean canGeneralizePremises() {
        return true;
    }

    @Override
    protected int subproofPremises(Claim claim) {
        return 0;
    }

    @Override
    protected String verifyClaim(Expression conclusion, Premise[] premises) {
        Expression p1 = premises[0].getPremise();
        Expression p2 = premises[0].getPremise();
        Expression p3 = premises[0].getPremise();
        return null;
    }
}
