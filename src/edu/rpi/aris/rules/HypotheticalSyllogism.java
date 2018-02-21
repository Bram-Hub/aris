package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Operator;
import edu.rpi.aris.proof.Premise;

import java.util.ArrayList;

public class HypotheticalSyllogism extends Rule {

    HypotheticalSyllogism() {
    }

    @Override
    public String getName() {
        return "Hypothetical Syllogism";
    }

    @Override
    public String getSimpleName() {
        return "HS";
    }

    @Override
    public Type[] getRuleType() {
        return new Type[] {Type.INFERENCE};
    }

    @Override
    public boolean canAutoFill() {
        return true;
    }

    @Override
    public ArrayList<String> getAutoFill(Premise[] premises) {
        //TODO
        return null;
    }

    @Override
    protected int requiredPremises() {
        return 2;
    }

    @Override
    public boolean canGeneralizePremises() {
        return true;
    }

    @Override
    protected int subProofPremises() {
        return 0;
    }

    @Override
    protected String verifyClaim(Expression conclusion, Premise[] premises) {
        Expression p1 = premises[0].getPremise();
        Expression p2 = premises[1].getPremise();
        if (p1.getOperator() != Operator.CONDITIONAL || p2.getOperator() != Operator.CONDITIONAL)
            return "Both Premises must be implications";
        if (conclusion.getOperator() != Operator.CONDITIONAL)
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
