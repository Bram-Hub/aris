package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Operator;
import edu.rpi.aris.proof.Premise;

import java.util.ArrayList;


public class Idempotence extends Rule{

    Idempotence(){

    }

    @Override
    public String getName() {
        return "Idempotence";
    }

    @Override
    public String getSimpleName() {
        return "Idem";
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

        Expression premise_removed_id;
        try{
            premise_removed_id = premise.withoutID();
        }catch(Exception e){
            System.out.println(e.toString());
            return "Couldn't read premise";
        }

        System.out.println(premise_removed_id.toLogicString());
        System.out.println(conclusion.toLogicString());
        try{
            boolean equals;
            equals = premise_removed_id.equals(conclusion);
            if(!equals) return "Conclusions does not equal premise with removed idempotence";
        }catch(Exception e){
            return "Something went wrong";
        }


        return null;
    }
}
