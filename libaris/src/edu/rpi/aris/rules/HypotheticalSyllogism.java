package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Operator;
import edu.rpi.aris.proof.Premise;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

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
        return 0;
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
        if (conclusion.getOperator() != Operator.CONDITIONAL) {
            return "The conclusion is not an implication";
        }
        Set<Expression> premiseSet = new HashSet<>();
        Expression currentExpr = conclusion.getExpressions()[0];
        Expression endExpr = conclusion.getExpressions()[1];
        if (currentExpr.equals(endExpr)) {
            if (premises.length == 0) {
                return null;
            } else {
                return "The conclusion \"" + conclusion.toLogicString() + "\" requires 0 premises, you have " + premises.length;
            }
        }
        int found  = -1;
        for (int i = 0; i < premises.length; ++i) {
            Expression premise = premises[i].getPremise();
            if (premise.getOperator() != Operator.CONDITIONAL) {
                return "The premise \"" + premise.toLogicString() + "\" is not an implication";
            }
            if (premise.getExpressions()[0].equals(currentExpr)) {
                if (found < 0) {
                    found = i;
                } else {
                    return "The premises \"" + premise.toLogicString() + "\" and \"" + premises[found].getPremise().toLogicString() + "\" both match the pattern \"" + currentExpr.toLogicString() + " → _\", however only 1 premise is allowed to match that pattern";
                }
            } else {
                if (!premiseSet.add(premise)) {//if using special equals function make sure premiseSet is using the same comparison
                    return "The statement \"" + premise.toLogicString() + "\" appears as a premise twice, please remove 1 of the references";
                }
            }
        }
        if (found < 0) {
            return "The proof is missing a premise matching the pattern \"" + currentExpr.toLogicString() + " → _\"";
        } else {
            currentExpr = premises[found].getPremise().getExpressions()[1];
        }
        while (!currentExpr.equals(endExpr)) {
            Expression works = null;
            for (Expression premise : premiseSet) {
                if (premise.getExpressions()[0].equals(currentExpr)) {
                    if (works == null) {
                        works = premise;
                    } else {
                        return "The premises \"" + premise.toLogicString() + "\" and \"" + works.toLogicString() + "\" both match the pattern \"" + currentExpr.toLogicString() + " → _\", however only 1 premise is allowed to match that pattern";
                    }
                }
            }
            if (works == null) {
                return "The proof is missing a premise matching the pattern \"" + currentExpr.toLogicString() + " → _\"";
            } else {
                premiseSet.remove(works);
                currentExpr = works.getExpressions()[1];
            }
        }
        if (premiseSet.size() == 1) {
            return "\"" + premiseSet.iterator().next().toLogicString() + "\" is not used to get to the conclusion";
        } else if (premiseSet.size() > 0) {
            String ret = "The premises {";
            for (Expression expr: premiseSet) {
                ret += "\"" + expr.toLogicString() + "\", ";
            }
            ret = ret.substring(0, ret.length()-2);
            ret += "} are not used to get to the conclusion";
            return ret;
        }
        return null;
    }
}
