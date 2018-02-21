package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Operator;
import edu.rpi.aris.proof.Premise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    public boolean canAutoFill() {
        return true;
    }

    @Override
    public ArrayList<String> getAutoFill(Premise[] premises) {
        if(premises[0].isSubproof() || premises[0].getPremise().getOperator() != Operator.AND)
            return null;
        return Arrays.stream(premises[0].getPremise().getExpressions()).map(Expression::toLogicString).collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    protected int requiredPremises() {
        return 1;
    }

    @Override
    public boolean canGeneralizePremises() {
        return false;
    }

    @Override
    protected int subProofPremises() {
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
