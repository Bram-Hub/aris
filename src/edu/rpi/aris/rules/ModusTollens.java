package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Claim;
import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Operator;
import edu.rpi.aris.proof.Premise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sun.rmi.server.InactiveGroupException;

import java.text.ParseException;

public class ModusTollens extends Rule {

    private static final Logger logger = LogManager.getLogger(ModusTollens.class);

    ModusTollens() {
    }

    @Override
    public String getName() {
        return "Modus Tollens";
    }

    @Override
    public String getSimpleName() {
        return "MT";
    }

    @Override
    public Type[] getRuleType() {
        return new Type[] {Type.INFERENCE};
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
    protected int subProofPremises(Claim claim) {
        return 0;
    }

    @Override
    protected String verifyClaim(Expression conclusion, Premise[] premises) {
        Expression p1 = premises[0].getPremise();
        Expression p2 = premises[1].getPremise();
        try {
            if (p1.getOperator() == Operator.CONDITIONAL && p2.getOperator() == Operator.CONDITIONAL)
                return check(p1, p2, conclusion) == null ? null : check(p2, p1, conclusion);
            else if (p1.getOperator() == Operator.CONDITIONAL)
                return check(p2, p1, conclusion);
            else
                return check(p1, p2, conclusion);
        } catch (ParseException e) {
            logger.error("Parse error when checking Modus Tollens", e);
            e.printStackTrace();
            return "Parse error when checking Modus Tollens";
        }
    }

    private String check(Expression p, Expression conditional, Expression conc) throws ParseException {
        if (conditional.getOperator() != Operator.CONDITIONAL)
            return "Expression is not a conditional expression";
        if (conditional.getExpressions().length != 2)
            return "Generalized Implication is not allowed";
        if (!conditional.getExpressions()[1].negate().equals(p) || !conditional.getExpressions()[0].negate().equals(conc))
            return "Invalid application of Modus Tollens";
        return null;
    }
}
