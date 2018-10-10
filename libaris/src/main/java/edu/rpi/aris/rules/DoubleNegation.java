package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Operator;
import edu.rpi.aris.proof.Premise;

import java.lang.reflect.Array;
import java.util.ArrayList;

public class DoubleNegation extends Rule{

    DoubleNegation(){

    }

    @Override
    public String getName() {
        return "Double Negation";
    }

    @Override
    public String getSimpleName() {
        return "Double Neg";
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
        Expression premise = premises[0].getPremise();
        if(premise.getNumExpressions() == 0) return "No Double Negation to be performed";
        Expression premise_removed_dns;
        try{
            System.out.println(premise.withoutDNs().toLogicString());
            premise_removed_dns = premise.withoutDNs();
        }catch(Exception e){
            return "Error reading premise";
        }

        try{
            if(conclusion.equalswithoutDNs(premise_removed_dns)) return "Conclusion does not equal premise without double negations";
        }catch(Exception e){
            return "Problem Found";
        }

        for (Expression e : conclusion.getExpressions()) {
            boolean found = false;
            for (int i = 0; i < premises.length; ++i) {
                System.out.println("Fiding " + e.toLogicString() + "in premise: " + premise_removed_dns.toLogicString());
                if (premise_removed_dns.hasSubExpression(e)){
                    found = true;
                }
            }
            if (!found) {
                return "The conjunct \"" + e.toLogicString() + "\" in the conclusion is not a premise";
            }
        }


        return null;
    }





}