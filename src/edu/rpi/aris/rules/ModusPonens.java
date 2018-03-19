package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Operator;
import edu.rpi.aris.proof.Premise;

import java.util.ArrayList;

public class ModusPonens extends Rule {

    ModusPonens() {
    }

    @Override
    public String getName() {
        return "Modus Ponens (" + getSimpleName() + ")";
    }

    @Override
    public String getSimpleName() {
        return "→ Elim";
    }

    @Override
    public Type[] getRuleType() {
        return new Type[] {Type.INFERENCE, Type.ELIM};
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
        return false;
    }

    @Override
    protected int subProofPremises() {
        return 0;
    }

    @Override
    protected String verifyClaim(Expression conclusion, Premise[] premises) {
        Expression p1 = premises[0].getPremise();
        Expression p2 = premises[1].getPremise();
        if ((p1.getOperator() != Operator.CONDITIONAL) && (p2.getOperator() != Operator.CONDITIONAL)) {
            return "Neither of the premises are a conditional";
        }
        else if (p1.getOperator() == Operator.CONDITIONAL) {
            if (!p1.getExpressions()[0].equals(p2) || !p1.getExpressions()[1].equals(conclusion)) {
                if (p2.getOperator() == Operator.CONDITIONAL) {
                    if (!p2.getExpressions()[0].equals(p1) || !p2.getExpressions()[1].equals(conclusion)) {
                        //generic error message
                        return "Invalid application of Modus Ponens";
                    }
                } else {//specifc to p1 as conditional
                    return "\"" + p1.toLogicString() + "\" is not the same as \"" + p2.toLogicString() + " → " + conclusion.toLogicString() + ")\"";
                }
            }
        }
        if (p2.getOperator() == Operator.CONDITIONAL) {
            if (!p2.getExpressions()[0].equals(p1) || !p2.getExpressions()[1].equals(conclusion)) {
                //specifc to p2 as conditional
                return "\"" + p2.toLogicString() + "\" is not the same as \"(" + p1.toLogicString() + " → " + conclusion.toLogicString() + ")\"";
            }
        }
        return null;
    }
}
