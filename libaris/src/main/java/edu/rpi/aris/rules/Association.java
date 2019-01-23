package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Operator;
import edu.rpi.aris.proof.Premise;

import java.util.ArrayList;

public class Association extends Rule{

    Association(){

    }

    @Override
    public String getName() {
        return "Association";
    }

    @Override
    public String getSimpleName() {
        return "Assoc";
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

        try{
            premise = new Expression(premise.getExpressions(), premise.getOperator(), premise.getParent(), premise.getParentVariables());
        }catch(Exception e){
            System.out.println(e);
        }

        remove_assoc(premise);
        remove_assoc(conclusion);
        System.out.println("Premise: " + premise);
        System.out.println("Conclusion: " + conclusion);

        return null;
    }

    private void remove_assoc(Expression expr){
        if(expr.getNumExpressions() == 0) return;

        for(int i = 0; i < expr.getNumExpressions(); i++){
            remove_assoc(expr.getExpressions()[i]);
        }

        Operator nec_op = expr.getOperator();
        for(int i = 0; i < expr.getNumExpressions(); i++){
            if(expr.getExpressions()[i].getNumExpressions() > 0 && expr.getExpressions()[i].getOperator().equals(nec_op)){
                Expression expanded = null;

                Expression[] feed = new Expression[expr.getExpressions()[i].getNumExpressions() + expr.getNumExpressions() - 1];
                for(int j = 0; j < expr.getExpressions()[i].getNumExpressions(); j++){
                    feed[j] = expr.getExpressions()[i].getExpressions()[j];
                }

                int start = expr.getExpressions()[i].getNumExpressions();
                int count = 0;
                for(int j = 0; j < expr.getNumExpressions(); j++){
                    if(i == j) continue;

                    feed[start + count] = expr.getExpressions()[j];
                    count++;
                }

                try{
                    expanded = new Expression(feed, expr.getOperator(), expr.getParent(), expr.getParentVariables());
                }catch(Exception e){
                    System.out.println(e);
                }

                expr.set(expanded);
            }
        }
    }

    private Expression[] get_lits(Expression expr){
        Expression ret[] = new Expression[0];
//        if(expr.getNumExpressions() == 0) return ret;
        for(Expression sub_expr : expr.getExpressions()){
            if(sub_expr.getNumExpressions() == 0){
                Expression temp[] = new Expression[ret.length + 1];
                System.arraycopy(ret,0,temp,0, ret.length);
                temp[temp.length-1] = sub_expr;
                ret = temp;
            }else{
                Expression temp[] = get_lits(sub_expr);
                Expression temp_copy[] = new Expression[temp.length + ret.length];
                for(int i = 0; i < ret.length; i++){
                    temp_copy[i] = ret[i];
                }
                for(int i = 0; i < temp.length; i++){
                    temp_copy[ret.length + i] = temp[i];
                }
                ret = temp_copy;
            }
        }
        return ret;
    }

    private Operator[] get_ops(Expression expr){

        Operator ret[];
        if(expr.getNumExpressions() - 1 >= 0) {
            ret =new Operator[expr.getNumExpressions() - 1];
        }else ret = new Operator[0];

//        if(expr.getOperator() != null) ret[0] = expr.getOperator();
        for(int i = 0; i < expr.getNumExpressions() - 1; i++){
            ret[i] = expr.getOperator();
        }
        for(Expression sub_expr : expr.getExpressions()){
            Operator temp[] = get_ops(sub_expr);
//            System.arraycopy(ret,0, temp, temp.length-1, temp.length);
            Operator temp_ret[] = new Operator[temp.length + ret.length];
            for(int i = 0; i < ret.length; i++){
                temp_ret[i] = ret[i];
            }
            for(int i = 0; i < temp.length; i++){
                temp_ret[ret.length + i] = temp[i];
            }
            ret = temp_ret;
        }

        return ret;
    }


}
