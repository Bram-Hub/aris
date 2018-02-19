package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Claim;
import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Operator;
import edu.rpi.aris.proof.Premise;

public class Conjunction extends Rule {

    Conjunction() {
    }

    @Override
    public String getName() {
        return "Conjunction(" + getSimpleName() + ")";
    }

    @Override
    public String getSimpleName() {
        return "∧ Intro";
    }

    @Override
    protected int requiredPremises(Claim claim) {
        return 2;
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
        Expression p1 = premises[0].getPremis();
        Expression p2 = premises[1].getPremis();
        if (p1.getOperator() != Operator.CONDITIONAL || p2.getOperator() != Operator.CONDITIONAL)
            return "Both Premises must be implications";
        if (conclusion.getOperator() != Operator.AND)
            return "The conclusion must be an implication";
        if (p1.getExpressions()[0].equals(p2.getExpressions()[1])) {
            Expression p = p1;
            p1 = p2;
            p2 = p;
        }
        if (p1.getExpressions()[1].equals(p2.getExpressions()[0]) && (!p1.getExpressions()[0].equals(conclusion.getExpressions()[0]) || !p2.getExpressions()[1].equals(conclusion.getExpressions()[1])))
            return "Invalid application of Hypothetical Syllogism";
        return null;
    }
}
