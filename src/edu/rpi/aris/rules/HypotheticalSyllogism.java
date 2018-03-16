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
        boolean found  = false;
        for (int i = 0; i < premises.length; ++i) {
            Expression premise = premises[i].getPremise();
            if (premise.getOperator() != Operator.CONDITIONAL) {
                return "The premise \"" + premise.toLogicStringwithoutDNs() + "\" is not an implication";
            }
            if (premise.getExpressions()[0].equalswithoutDNs(currentExpr)) {
                currentExpr = premise.getExpressions()[1];
                found = true;
            }
            else {
                premiseSet.add(premise.withoutDNs());
            }
        }
        if (!found && !currentExpr.equalswithoutDNs(endExpr)) {
            return "The proof is missing a premise matching the pattern \"" + currentExpr.toLogicStringwithoutDNs() + "→ _\"";
        }
        while (!currentExpr.equalswithoutDNs(endExpr)) {
            found = false;
            for (Expression premise : premiseSet) {
                if (premise.getExpressions()[0].equalswithoutDNs(currentExpr)) {
                    currentExpr = premise.getExpressions()[1];
                    premiseSet.remove(premise);
                    found = true;
                    break;
                }
            }
            if (!found) {
                return "The proof is missing a premise matching the pattern \"" + currentExpr.toLogicStringwithoutDNs() + "→ _\"";
            }
        }
        if (premiseSet.size() == 1) {
            return "\"" + premiseSet.iterator().next().toLogicStringwithoutDNs() + "\" is not used to get to the conclusion";
        }
        else if (premiseSet.size() > 0) {
            String ret = "The premises {";
            for (Expression expr: premiseSet) {
                ret += "\"" + expr.toLogicStringwithoutDNs() + "\", ";
            }
            ret = ret.substring(0, ret.length()-2);
            ret += "} are not used to get to the conclusion";
            return ret;
        }
        return null;
    }
}
