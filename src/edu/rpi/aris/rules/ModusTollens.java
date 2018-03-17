package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Operator;
import edu.rpi.aris.proof.Premise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.ParseException;
import java.util.ArrayList;

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
        try {
            if ((p1.getOperator() != Operator.CONDITIONAL) && (p2.getOperator() != Operator.CONDITIONAL)) {
                return "Neither of the premises are a conditional";
            }
            else if (p1.getOperator() == Operator.CONDITIONAL) {
                if (!p1.getExpressions()[1].negate().equalswithoutDNs(p2) || !p1.getExpressions()[0].negate().equalswithoutDNs(conclusion)) {
                    if (p2.getOperator() == Operator.CONDITIONAL) {
                        if (!p2.getExpressions()[1].negate().equalswithoutDNs(p1) || !p2.getExpressions()[0].negate().equalswithoutDNs(conclusion)) {
                            //generic error message
                            return "Invalid application of Modus Tollens";
                        }
                    } else {//specifc to p1 as conditional
                        return "\"" + p1.toLogicStringwithoutDNs() + "\" is not the same as \"(" + conclusion.negate().toLogicStringwithoutDNs() + " → " + p2.negate().toLogicStringwithoutDNs() + ")\"";
                    }
                }
            }
            if (p2.getOperator() == Operator.CONDITIONAL) {
                if (!p2.getExpressions()[1].negate().equalswithoutDNs(p1) || !p2.getExpressions()[0].negate().equalswithoutDNs(conclusion)) {
                    //specifc to p2 as conditional
                    return "\"" + p2.toLogicStringwithoutDNs() + "\" is not the same as \"(" + conclusion.negate().toLogicStringwithoutDNs() + " → " + p1.negate().toLogicStringwithoutDNs() + ")\"";
                }
            }
        } catch (ParseException e) {
            logger.error("Parse error when checking Modus Tollens", e);
            e.printStackTrace();
            return "Parse error when checking Modus Tollens";
        }
        return null;
    }
}
