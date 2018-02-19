package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Claim;
import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Operator;
import edu.rpi.aris.proof.Premise;

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
    protected int requiredPremises(Claim claim) {
        return 2;
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
        Expression p1 = premises[0].getPremis();
        Expression p2 = premises[1].getPremis();
        if (p1.getOperator() == Operator.CONDITIONAL && p2.getOperator() == Operator.CONDITIONAL)
            return check(p1, p2, conclusion) == null ? null : check(p2, p1, conclusion);
        else if (p1.getOperator() == Operator.CONDITIONAL)
            return check(p2, p1, conclusion);
        else
            return check(p1, p2, conclusion);
    }

    private String check(Expression p, Expression conditional, Expression conc) {
        if (conditional.getOperator() != Operator.CONDITIONAL)
            return "Expression is not a conditional expression";
        if (conditional.getExpressions().length != 2)
            return "Generalized Implication is not allowed";
        if (!conditional.getExpressions()[0].equals(p) || !conditional.getExpressions()[1].equals(conc))
            return "Invalid application of Modus Ponens";
        return null;
    }

}
