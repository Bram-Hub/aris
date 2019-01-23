package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Operator;
import edu.rpi.aris.proof.Premise;

import java.util.ArrayList;

public class Distribution extends Rule{

    Distribution(){

    }

    @Override
    public String getName() {
        return "Distribution";
    }

    @Override
    public String getSimpleName() {
        return "Dist";
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
//        Expression temp = premises[0].getPremise();
//
//        Expression premise;
//        Expression conc;
//        try{
//            premise = new Expression(temp.getExpressions(), temp.getOperator(), temp.getParent(), temp.getParentVariables());
//            if(conc.getNumExpressions() == 0)
//                conc = new Expression(conclusion.getExpressions(), null, conclusion.getParent(), conclusion.getParentVariables());
//            else conc = new Expression(conclusion.getExpressions(), conclusion.getOperator(), conclusion.getParent(), conclusion.getParentVariables());
//        }catch(Exception e){
//            System.out.println(e);
//            return "Failed";
//        }
//
//        Expression[] possibilities = expand_Distribution(premise);
        return null;
    }

    private Expression[] expand_Distribution(Expression expr){
        if(!expr.getOperator().equals(Operator.AND) && !expr.getOperator().equals(Operator.OR)){
            for(int i = 0; i < expr.getNumExpressions(); i++){
                expand_Distribution(expr.getExpressions()[i]);
            }
        }

        Expression[] ret = new Expression[0];

        Operator opp_oper;
        if(expr.getOperator().equals(Operator.AND)) opp_oper= Operator.OR;
        else opp_oper = Operator.AND;
        for(int i = 0; i < expr.getNumExpressions(); i++){
//            if(i != 0 && expr.getExpressions()[i].getOperator().equals(opp_oper)){
//
//            }
        }
        return ret;
    }

}

