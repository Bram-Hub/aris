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
        if (conclusion.getOperator() != Operator.AND) {
            return "The conclusion is not a conjunction";
        }
        for (int i = 0; i < premises.length; ++i) {
            if (!conclusion.hasSubExpressionwithoutDNs(premises[i].getPremise())){
                return "The premise \"" + premises[i].getPremise().toLogicStringwithoutDNs() + "\" is not a conjunct in the conclusion";
            }
        }
        for (Expression e : conclusion.getExpressions()) {
            boolean found = false;
            for (int i = 0; i < premises.length; ++i) {
                if (premises[i].getPremise().equalswithoutDNs(e)){
                    found = true;
                }
            }
            if (!found) {
                return "The conjunct \"" + e.toLogicStringwithoutDNs() + "\" in the conclusion is not a premise";
            }
        }
        return null;
    }
}
