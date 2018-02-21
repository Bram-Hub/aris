package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Claim;
import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Operator;
import edu.rpi.aris.proof.Premise;

public class Simplification extends Rule {

    Simplification() {
    }

    @Override
    public String getName() {
        return "Simplification (" + getSimpleName() + ")";
    }

    @Override
    public String getSimpleName() {
        return "∧ Elim";
    }

    @Override
    public Type[] getRuleType() {
        return new Type[]{Type.INFERENCE, Type.ELIM};
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
    protected int subProofPremises(Claim claim) {
        return 0;
    }

    @Override
    protected String verifyClaim(Expression conclusion, Premise[] premises) {
        Expression premise = premises[0].getPremise();
        if (premise.getOperator() != Operator.AND)
            return "The premise must be a conjunction";
        if (premise.hasSubExpression(conclusion))
            return null;
        if (conclusion.getOperator() != Operator.AND)
            return "The Conclusion is not a conjunct in the premise or a conjunction";
        for (Expression e : conclusion.getExpressions())
            if (!premise.hasSubExpression(e))
                return "The Conclusion is not a conjunct in the premise and contains a conjunct not present in the premise";
        return null;
    }
}
