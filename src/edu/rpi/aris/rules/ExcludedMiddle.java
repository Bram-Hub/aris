package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Claim;
import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Operator;
import edu.rpi.aris.proof.Premise;

import java.text.ParseException;

public class ExcludedMiddle  extends Rule {

    ExcludedMiddle() {
    }

    @Override
    public String getName() {
        return "Law of Excluded Middle";
    }

    @Override
    public String getSimpleName() {
        return "LEM";
    }

    @Override
    protected int requiredPremises(Claim claim) {
        return 0;
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
        if (conclusion.getOperator() != Operator.OR)
            return "The conclusion must be a disjunction";
        if (conclusion.getNumExpressions() != 2)
            return "The conclusion must have only 2 disjuncts";
        Expression expressions[] = conclusion.getExpressions();
        try {
            if (!expressions[0].equals(expressions[1].negate()) && !expressions[1].equals(expressions[0].negate()))
                return "The 2 disjuncts of the conclusion are not negations of each other";
        }
        catch(ParseException p) {
            return p.getMessage();
        }
        return null;
    }
}