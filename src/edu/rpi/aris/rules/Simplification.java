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
    protected int requiredPremises(Claim claim) {
        return 1;
    }

    @Override
    protected int subproofPremises(Claim claim) {
        return 0;
    }

    @Override
    protected String verifyClaim(Expression conclusion, Premise[] premises) {
        Expression premise = premises[0].getPremis();
        if (premise.getOperator() != Operator.AND)
            return "Simplification requires premise to be a conjunction";
        if (premise.hasSubExpression(conclusion))
            return null;
        if (conclusion.getOperator() != Operator.AND)
            return "Conclusion is not a conjunct in the premise and conclusion is not a conjunction";
        for (Expression e : conclusion.getExpressions())
            if (!premise.hasSubExpression(e))
                return "Conclusion is not a conjunct in the premise and contains a conjunct not present in the premise";
        return null;
    }
}
