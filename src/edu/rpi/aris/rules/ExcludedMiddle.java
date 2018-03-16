package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Operator;
import edu.rpi.aris.proof.Premise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.ParseException;
import java.util.ArrayList;

public class ExcludedMiddle  extends Rule {

    private static final Logger logger = LogManager.getLogger(ExcludedMiddle.class);

    ExcludedMiddle() {
    }

    @Override
    public String getName() {
        return "Law of Excluded Middle";
    }

    @Override
    public String getSimpleName() {
        return "LEM";
    }

    @Override
    public Type[] getRuleType() {
        return new Type[] {Type.INFERENCE};
    }

    @Override
    public boolean canAutoFill() {
        return false;
    }

    @Override
    public ArrayList<String> getAutoFill(Premise[] premises) {
        return null;
    }

    @Override
    protected int requiredPremises() {
        return 0;
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
        if (conclusion.getOperator() != Operator.OR)
            return "The conclusion is not a disjunction";
        if (conclusion.getNumExpressions() != 2)
            return "The conclusion must have only 2 disjuncts";
        Expression expressions[] = conclusion.getExpressions();
        try {
            if (!expressions[0].negate().equalswithoutDNs(expressions[1]))
                return "\"" + expressions[0].toLogicStringwithoutDNs() + "\" is not the negation of \"" + expressions[1].toLogicStringwithoutDNs() + "\"";
        }
        catch(ParseException e) {
            logger.error("Parse error when checking Law of Excluded Middle", e);
            e.printStackTrace();
            return "Parse error when checking Law of Excluded Middle";
        }
        return null;
    }
}