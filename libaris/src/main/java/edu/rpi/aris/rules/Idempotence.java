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
        return new Type[] {Type.EQUIVALENCE};
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

        if(premise.equals(conclusion)) return "Conclusion not changed from premise before rule application";

        if(premise.getNumExpressions() < 2) return "No place for Idempotence to be Applied";
        Expression premise_removed_id;
        Expression conclusion_removed_id;
        try{
            premise_removed_id = new Expression(premise.getExpressions(), premise.getOperator(), premise.getParent(), premise.getParentVariables());
        }catch(Exception e){
            System.out.println(e.toString());
            return "Couldn't read premise or conclusion";
        }

        Expression temp  =remove_ID(premise_removed_id);
//        fix_ret(premise_removed_id);
//        remove_ID(conclusion);

        System.out.println(premise_removed_id);
        System.out.println(conclusion);

        if(!conclusion.equals(premise_removed_id)) return "Not Valid application of Idempotence";
//        try{
//            boolean equals;
//            equals = premise_removed_id.equals(conclusion);
//            if(!equals) return "Conclusions does not equal premise with removed idempotence";
//        }catch(Exception e){
//            return "Something went wrong";
//        }


        return null;
    }

    private Expression remove_ID(Expression expr){
        if(expr.getNumExpressions() == 0) return expr;

        boolean found = false;
        for(int i = 0; i < expr.getNumExpressions(); i++){
            Expression queried = expr.getExpressions()[i];
            Expression[] feed = new Expression[1];
            feed[0] = expr.getExpressions()[i];
            for(int j = 0; j < expr.getNumExpressions(); j++){
                if(i == j) continue;

                if(queried.equals(expr.getExpressions()[j]) && (expr.getOperator().equals(Operator.AND) || expr.getOperator().equals(Operator.OR))){
                    if(expr.getNumExpressions() == 2){
                        try {
                            Expression temp = new Expression(feed, null, expr.getParent(), expr.getParentVariables());
                            System.out.println("Current value: " + expr);
                            System.out.println("Will be Changed to: " + temp);
                            expr.set(temp);
                            fix_ret(expr);
                            System.out.println("Changed?: " + expr);
                        }catch(Exception e) {
                            System.out.println(e);
                        }
                    }else{
                        try {
                            expr = new Expression(feed, expr.getOperator(), expr.getParent(), expr.getParentVariables());
                            System.out.println("Current value: " + expr);
//                            System.out.println("Will be Changed to: " + temp);
//                            expr.set(temp);
                            expr.set(expr);
                            fix_ret(expr);
                            System.out.println("Changed?: " + expr);
                        }catch(Exception e){
                            System.out.println(e);
                        }
                    }
                    found = true;
                    break;
                }
                Expression[] temp = new Expression[feed.length+1];
                if(j < i){
                    System.arraycopy(feed, 0, temp, 1, feed.length);
                    temp[0] = expr.getExpressions()[j];
                }
                else {
                    System.arraycopy(feed, 0, temp, 0, feed.length);
                    temp[temp.length-1] = expr.getExpressions()[j];
                }

                feed = temp;
            }

            if(expr.getNumExpressions() > 0)
                expr.getExpressions()[i] = remove_ID(expr.getExpressions()[i]);
            if(found) break;
        }

//        for(int i = 0; i < )

        fix_ret(expr);
        System.out.println("Final version: " + expr);
        return expr;
    }

    private void fix_ret(Expression express){
        for(int i = 0; i < express.getNumExpressions(); i++){
            fix_ret(express.getExpressions()[i]);
        }
        express.setPolish();
    }
}
