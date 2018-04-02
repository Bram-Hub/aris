package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Operator;
import edu.rpi.aris.proof.Premise;

import java.util.ArrayList;

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
        return 1;
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
        if (conclusion.getOperator() != Operator.AND) {
            if (premises.length == 1) {
                if (conclusion.equals(premises[0].getPremise())) {//reiteration
                    return null;
                } else {
                    return "There is only 1 premise and the conclusion is not a reiteration of that premise";
                }
            } else {
                return "The conclusion is not a conjunction";
            }
        }
        for (int i = 0; i < premises.length; ++i) {
            if (!conclusion.hasSubExpression(premises[i].getPremise())){
                return "The premise \"" + premises[i].getPremise().toLogicString() + "\" is not a conjunct in the conclusion";
            }
        }
        for (Expression e : conclusion.getExpressions()) {
            boolean found = false;
            for (int i = 0; i < premises.length; ++i) {
                if (premises[i].getPremise().equals(e)){
                    found = true;
                }
            }
            if (!found) {
                return "The conjunct \"" + e.toLogicString() + "\" in the conclusion is not a premise";
            }
        }
        return null;
    }
}
