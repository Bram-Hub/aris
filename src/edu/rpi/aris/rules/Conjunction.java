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
        return "Conjunction (" + getSimpleName() + ")";
    }

    @Override
    public String getSimpleName() {
        return "∧ Intro";
    }

    @Override
    public Type[] getRuleType() {
        return new Type[] {Type.INFERENCE, Type.INTRO};
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
    protected int subProofPremises(Claim claim) {
        return 0;
    }

    @Override
    protected String verifyClaim(Expression conclusion, Premise[] premises) {
        Expression p1 = premises[0].getPremise();
        Expression p2 = premises[1].getPremise();
        if (conclusion.getOperator() != Operator.AND)
            return "The conclusion must be a conjunction";
        if (!conclusion.hasSubExpression(p1) || !conclusion.hasSubExpression(p2))
            return "One of the premises is not in the conclusion";
        for (Expression e : conclusion.getExpressions())
            if (!p1.equals(e) && !p2.equals(e))
                return "One of the conjuncts in the conclusion is not a premise";
        return null;
    }
}
