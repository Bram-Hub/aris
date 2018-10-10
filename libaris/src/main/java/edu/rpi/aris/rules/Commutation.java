package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Operator;
import edu.rpi.aris.proof.Premise;

import java.util.ArrayList;

public class Commutation extends Rule{


    Commutation(){

    }

    @Override
    public String getName() {
        return "Commutation";
    }

    @Override
    public String getSimpleName() {
        return "Comm";
    }

    @Override
    public Rule.Type[] getRuleType() {
        return new Rule.Type[] {Rule.Type.INFERENCE};
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
        if(premise.getNumExpressions() < 2) return "Improper Application of rule";
        boolean equal = false;
        try{
            equal = conclusion.equalsFullPower(premise);
        }catch(Exception e){
            return "Couldn't read Premise";
        }
        if(!equal) return "Incorrect application of Commutation";

        for (Expression e : conclusion.getExpressions()) {
            boolean found = false;
            for (Expression sub_prem : premise.getExpressions()) {
                System.out.println("Fiding " + e.toLogicString() + "in premise: " + sub_prem.toLogicString());
                try{
                    found = sub_prem.equalsFullPower(e);
                    if (found) break;
                }catch(Exception e1){
                    return "Couldn't Read Conclusion";
                }
            }
            if(!found){
                return "The expression \"" + e.toLogicString() + "\" in the conclusion is not a premise";
            }
        }


        return null;
    }

    private String checkexp(Expression premise, Expression conclusion){
        Expression[] prem_exp = new Expression[0];
        prem_exp = get_exp(premise, prem_exp);
        Expression[] con_exp = new Expression[0];
        con_exp = get_exp(conclusion,con_exp);
        if(con_exp.length != prem_exp.length){
            return "Missing or Extra expression";
        }
        for(int i = 0; i < prem_exp.length; i++){
            for(int j = 0; j < con_exp.length; j++){
                if(prem_exp[i].equals(con_exp[j])){
                    break;
                }else if(j == con_exp.length - 1){
                    return "Couldn't find " + prem_exp[i].toLogicString() + " in conclusion";
                }
            }
        }
        return "";
    }

    private Expression[] get_exp(Expression express,Expression[] ret){
        if(express.getExpressions().length == 0) return ret;
        for(int i = 0; i < express.getExpressions().length; i++){
            if(express.getExpressions()[i].getExpressions().length == 0){
                Expression[] temp = new Expression[ret.length + 1];
                for(int j = 0; j < ret.length; j++){
                    temp[j] = ret[j];
                }
                temp[ret.length] = express.getExpressions()[i];
                ret = temp;
            }else{
                ret = get_exp(express.getExpressions()[i], ret);
            }
        }


        return ret;
    }

    private String checkops(Expression premise, Expression conclusion){
        Operator[] prem_ops = new Operator[0];
        prem_ops = get_ops(premise,prem_ops);
        Operator[] con_ops = new Operator[0];
        con_ops = get_ops(conclusion, con_ops);

        if(con_ops.length != prem_ops.length){
            return "Missing or extra operator";
        }
        for(int i = 0; i < prem_ops.length; i++){
            for(int j = 0; j < con_ops.length; j++){
                if(prem_ops[i].equals(con_ops[i])){
                    break;
                }else if(j == con_ops.length - 1){
                    return "Couldn't find operator \"" + prem_ops[i] + "\" in concusion";
                }
            }
        }

        return "";
    }

    private Operator[] get_ops(Expression express, Operator[] ret){
        if(express.getOperator() == null) return ret;
        else{
            Operator[] temp = new Operator[ret.length + 1];
            for(int i = 0 ;i < ret.length; i++){
                temp[i] = ret[i];
            }
            temp[ret.length] = express.getOperator();
            ret = temp;
        }
        for(int i = 0; i < express.getExpressions().length; i++){
            ret = get_ops(express.getExpressions()[i], ret);
        }

        return ret;
    }




}
