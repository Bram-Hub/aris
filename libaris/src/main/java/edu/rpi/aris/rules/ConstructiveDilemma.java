package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Operator;
import edu.rpi.aris.proof.Premise;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class ConstructiveDilemma extends Rule {

    ConstructiveDilemma() {
    }

    @Override
    public String getName() {
        return "Constructive Dilemma";
    }

    @Override
    public String getSimpleName() {
        return "CD";
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
        if (conclusion.getOperator() != Operator.OR) {
            return "The conclusion is not a dijunction";
        }
        int found  = -1;
        Set<Expression> premiseSet = new HashSet<>();
        for (int i = 0; i < premises.length; ++i) {
            Expression premise = premises[i].getPremise();
            if (premise.getOperator() == Operator.CONDITIONAL) {
                if (!premiseSet.add(premise)) { //if using special equals function make sure premiseSet is using the same comparison
                    return "The statement \"" + premise.toLogicString() + "\" appears as a premise twice, please remove 1 of the references";
                }
            } else if (premise.getOperator() == Operator.OR) {
                if (found < 0) {
                    found = i;
                } else {
                    return "The premises \"" + premise.toLogicString() + "\" and \"" + premises[found].getPremise().toLogicString() + "\" are both disjunctions, however only 1 disjunction premise is allowed";
                }
            } else {
                return "The premise \"" + premise.toLogicString() + "\" is not an implication or a disjunction";
            }
        }
        if (found < 0) {
            return "None of the premises are disjunctions";
        }

        Set<Expression> premiseSetUsed = new HashSet<>(premiseSet);
        Expression[] premiseDisjuncts = premises[found].getPremise().getExpressions();
        Expression[] conclusions = conclusion.getExpressions();
        if (premiseDisjuncts.length != conclusions.length) {
            return "The conclusion with " + premiseDisjuncts.length + " disjuncts has a different number of disjuncts than the premise that has " + premiseDisjuncts.length + " disjuncts";
        }
        for (int i = 0; i < premiseDisjuncts.length; ++i) {
            Expression works = null;
            for (Expression premise: premiseSet) {
                if (premiseDisjuncts[i].equals(premise.getExpressions()[0])) {
                    premiseSetUsed.remove(premise);
                    if (conclusions[i].equals(premise.getExpressions()[1])) {
                        if (works == null) {
                            works = premise;
                        } else {
                            return "The premises \"" + premise.toLogicString() + "\" and \"" + works.toLogicString() + "\" both match the pattern \"" + premiseDisjuncts[i].toLogicString() + " → _\", however only 1 premise is allowed to match that pattern";
                        }
                    } else {
                        return "For the premise \"" + premise.toLogicString() + "\" the consequent is not a disjunct in the conclusion despite the antecedent being in the premise that is a disjunction";
                    }
                }
            }
            if (works == null) {
                if (!premiseDisjuncts[i].equals(conclusions[i])) {
                    return "\"" + premiseDisjuncts[i].toLogicString() + "\" does not appear as the antecedent in any premise and is not carried over as a disjunct in the conclusion";
                }
            }
        }

        if (premiseSetUsed.size() == 1) {
            return "For the premise \"" + premiseSetUsed.iterator().next().toLogicString() + "\" the antecedent is not a disjunct in the premise that is a disjunction or the consequent does not appear in the conclusion";
        } else if (premiseSetUsed.size() > 0) {
            String ret = "For the premises {";
            for (Expression expr: premiseSetUsed) {
                ret += "\"" + expr.toLogicString() + "\", ";
            }
            ret = ret.substring(0, ret.length()-2);
            ret += "} the antecedent is not a disjunct in the premise that is a disjunction or the consequent does not appear in the conclusion";
            return ret;
        }

        return null;
    }
}
