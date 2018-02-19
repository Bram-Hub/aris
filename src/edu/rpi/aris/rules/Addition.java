package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Claim;
import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Operator;
import edu.rpi.aris.proof.Premise;

public class Addition extends Rule {

    Addition() {
    }

    @Override
    public String getName() {
        return "Addition (" + getSimpleName() + ")";
    }

    @Override
    public String getSimpleName() {
        return "∨ Intro";
    }

    @Override
    protected int requiredPremises(Claim claim) {
        return 1;
    }

    @Override
    protected boolean canGeneralizePremises() {
        return false;
    }

    @Override
    protected int subproofPremises(Claim claim) {
        return 0;
    }

    @Override
    protected String verifyClaim(Expression conclusion, Premise[] premises) {
        Expression premise = premises[0].getPremise();
        if (conclusion.getOperator() != Operator.OR)
            return "The conclusion must be a disjunction";
        if (!conclusion.hasSubExpression(premise))
            return "the premises is not a disjunct in the conclusion";
        return null;
    }
}
