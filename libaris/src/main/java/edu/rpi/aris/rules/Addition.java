package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Operator;
import edu.rpi.aris.proof.Premise;
import edu.rpi.aris.proof.SentenceUtil;

import java.text.ParseException;
import java.util.ArrayList;

public class Addition extends Rule {

    Addition() {
    }

    @Override
    public String getName() {
        return "Addition (" + getSimpleName() + ")";
    }

    @Override
    public String getSimpleName() {
        return "∨ Intro";
    }

    @Override
    public Type[] getRuleType() {
        return new Type[]{Type.INFERENCE, Type.INTRO};
    }

    @Override
    public boolean canAutoFill() {
        return true;
    }

    @Override
    public ArrayList<String> getAutoFill(Premise[] premises) {
        if (premises[0].isSubproof())
            return null;
        String str = premises[0].getPremise().toLogicString();
        if (premises[0].getPremise().getOperator() == Operator.OR) {
            try {
                str = SentenceUtil.removeParen(str);
            } catch (ParseException e) {
                e.printStackTrace();
                return null;
            }
        }
        ArrayList<String> list = new ArrayList<>();
        list.add(str + " " + Operator.OR.rep + " ");
        return list;
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
        if (conclusion.getOperator() != Operator.OR) {
            if (conclusion.equals(premise)) {//reiteration
                return null;
            } else {
                return "The conclusion is not a disjunction and is not a reiteration of the premise";
            }
        }
        if (!conclusion.hasSubExpression(premise)) {
            if (premise.getOperator() == Operator.OR) {
                for (Expression disjunct: premise.getExpressions()) {
                    if (!conclusion.hasSubExpression(disjunct)) {
                        return "disjunct \"" + disjunct.toLogicString() + "\" in the premise is not a disjunct in the conlusion";
                    }
                }
            } else {
                return "the premise is not a disjunct in the conclusion";
            }
        }
        return null;
    }
}
