package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Operator;
import edu.rpi.aris.proof.Premise;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

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

        ArrayList<Expression> conclusionList = new ArrayList<>();
        for (Expression expr: conclusion.getExpressions()) {
            conclusionList.add(expr);
        }
        Set<Expression> premiseSetUsed = new HashSet<>(premiseSet);
        Expression premiseDisjuncts[] = premises[found].getPremise().getExpressions();
        for (Expression disjunct: premiseDisjuncts) {
            Expression works = null;
            for (Expression premise: premiseSet) {
                if (disjunct.equals(premise.getExpressions()[0])) {
                    premiseSetUsed.remove(premise);
                    if (conclusion.hasSubExpression(premise.getExpressions()[1])) {
                        conclusionList.remove(premise.getExpressions()[1]);
                        if (works == null) {
                            works = premise;
                        }
                        else {
                            return "The premises \"" + premise.toLogicString() + "\" and \"" + works.toLogicString() + "\" both match the pattern \"" + disjunct.toLogicString() + " → _\", however only 1 premise is allowed to match that pattern";
                        }
                    } else {
                        return "For the premise \"" + premise.toLogicString() + "\" the consequent is not a disjunct in the conclusion despite the antecedent being in the premise that is a disjunction";
                    }
                }
            }
            if (works == null) {
                if (conclusion.hasSubExpression(disjunct)) {
                    conclusionList.remove(disjunct);
                } else {
                    return "\"" + disjunct.toLogicString() + "\" does not appear as the antecedent in any premise and is not carried over as a disjunct in the conclusion";
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
        if (conclusionList.size() == 1) {
            return "The disjunct in the conclusion \"" + conclusionList.get(0).toLogicString() + "\" is either a duplicate or is not a disjunct in the premise that is a disjunction and is not the consequent of a premise whose antecedent is in the premise that is a disjunction";
        } else if (conclusionList.size() > 0) {
            String ret = "These disjuncts in the conclusion {";
            for (Expression expr: conclusionList) {
                ret += "\"" + expr.toLogicString() + "\", ";
            }
            ret = ret.substring(0, ret.length()-2);
            ret += "} are either duplicates or are not disjuncts in the premise that is a disjunction and are not the consequents of a premise whose antecedent is in the premise that is a disjunction";
            return ret;
        }

        return null;
    }
}
