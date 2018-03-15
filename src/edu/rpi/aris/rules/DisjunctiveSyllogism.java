package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Operator;
import edu.rpi.aris.proof.Premise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.ParseException;
import java.util.ArrayList;

public class DisjunctiveSyllogism extends Rule {

    private static final Logger logger = LogManager.getLogger(DisjunctiveSyllogism.class);

    DisjunctiveSyllogism() {
    }

    @Override
    public String getName() {
        return "Disjunctive Syllogism (" + getSimpleName() + ")";
    }

    @Override
    public String getSimpleName() {
        return "∨ Elim";
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
        return 1;
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
        if (!(conclusion.getNumExpressions() <= 1) && !(conclusion.getOperator() != Operator.OR))
            return "The conclusion must be either a disjunction or a literal";
        int found = -1;
        for (int i = 0; i < premises.length; ++i) {
            Expression e = premises[i].getPremise();
            if (e.getOperator() == Operator.OR) {
                found = i;
            }
        }
        if (found < 0) {
            return "One of the premises must be a disjunction";
        }

        Expression premiseDisjuncts[] = premises[found].getPremise().getExpressions();
        try {
            for (Expression disjunct: premiseDisjuncts) {
                boolean failed = false;
                if (!conclusion.hasSubExpression(disjunct) && !conclusion.equals(disjunct)) {
                    boolean works = false;
                    for (int i = 0; i < premises.length; ++i) {
                        if (i != found) {
                            if (premises[i].getPremise().negate().equals(disjunct) || disjunct.negate().equals(premises[i].getPremise())) {
                                works = true;
                                break;
                            }
                        }
                    }
                    if (!works) {
                        failed = true;
                    }
                }
                if (failed) {
                    return "\"" + disjunct.toString() + "\" is not a disjunct in the conclusion and the negation of it does not appear as a premise";
                }
            }
        }
        catch(ParseException e) {
            logger.error("Parse error when checking Disjunctive Syllogism", e);
            e.printStackTrace();
            return "Parse error when checking Disjunctive Syllogism";
        }
        return null;
    }
}