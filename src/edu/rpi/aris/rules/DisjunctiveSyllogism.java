package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Operator;
import edu.rpi.aris.proof.Premise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

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
        if (!(conclusion.getNumExpressions() <= 1) && !(conclusion.getOperator() != Operator.OR)) {
            return "The conclusion must be either a disjunction or a literal";
        }

        int found = -1;
        Set<Expression> premiseSet = new HashSet<>();
        for (int i = 0; i < premises.length; ++i) {
            Expression e = premises[i].getPremise();
            if (e.getOperator() == Operator.OR) {
                found = i;
            }
            else {
                premiseSet.add(e.withoutDNs());
            }
        }
        if (found < 0) {
            return "None of the premises are disjunctions";
        }

        Set<Expression> premiseSetUsed = new HashSet<>(premiseSet);
        Expression premiseDisjuncts[] = premises[found].getPremise().getExpressions();
        try {
            for (Expression disjunct: premiseDisjuncts) {
                boolean works = false;
                if (!conclusion.hasSubExpressionwithoutDNs(disjunct) && !conclusion.equalswithoutDNs(disjunct)) {
                    for (Expression premise: premiseSet) {
                        if (premise.negate().equalswithoutDNs(disjunct)) {
                            premiseSetUsed.remove(premise);
                            works = true;
                            break;
                        }
                    }
                }
                else {
                    works = true;
                }
                if (!works) {
                    return "\"" + disjunct.toLogicStringwithoutDNs() + "\" is not a disjunct in the conclusion and the negation of it does not appear as a premise";
                }
            }
            if (premiseSetUsed.size() == 1) {
                return "\"" + premiseSetUsed.iterator().next().toLogicStringwithoutDNs() + "\" is not a disjunct in the premise that is a disjunction";
            }
            else if (premiseSetUsed.size() > 0) {
                String ret = "The premises {";
                for (Expression expr: premiseSetUsed) {
                    ret += "\"" + expr.toLogicStringwithoutDNs() + "\", ";
                }
                ret = ret.substring(0, ret.length()-2);
                ret += "} are not disjuncts in the premise that is a disjunction";
                return ret;
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