package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Operator;
import edu.rpi.aris.proof.Premise;

import java.util.ArrayList;

public class Absorption extends Rule{
    Absorption(){

    }

    @Override
    public String getName() {
        return "Absorption";
    }

    @Override
    public String getSimpleName() {
        return "Absorp";
    }

    @Override
    public Rule.Type[] getRuleType() {
        return new Rule.Type[] {Rule.Type.EQUIVALENCE};
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
        Expression conc;
        try{
            premise = premise.copy();
            conc = conclusion.copy();
        }catch(Exception e){
            System.out.println(e);
            return "Failed";
        }

        collapse(premise);

        System.out.println("Collapsed Premise: " + premise);
        System.out.println("Conclusion: " + conc);

        if(!conc.equals(premise)) return "Invalid application of Absorption";
        return null;
    }

    private boolean collapse(Expression e){
        if(e.getNumExpressions() == 0) return false;

        if(!e.getOperator().equals(Operator.AND) && !e.getOperator().equals(Operator.OR)){
            for(int i = 0; i < e.getNumExpressions(); i++){
                if(collapse(e.getExpressions()[i])){
                    fix_ret(e);
                    return true;
                }
            }
            return false;
        }

        Operator opp_op;
        if(e.getOperator().equals(Operator.AND)) opp_op = Operator.OR;
        else opp_op = Operator.AND;

        for(int i = 0; i < e.getNumExpressions(); i++){
            if(i != 0 && e.getExpressions()[i].getOperator().equals(opp_op)){
                if(right_form(e.getExpressions()[i-1], e.getExpressions()[i])){
                    Expression[] feed = new Expression[e.getNumExpressions()-1];
                    int count = 0;
                    for(int j = 0; j < e.getNumExpressions(); j++){
                        if(i == j) continue;
                        feed[j + count] = e.getExpressions()[j];
                        count++;
                    }
                    Expression new_exp=null;
                    try{
                        if(feed.length > 1)
                            new_exp = new Expression(feed, e.getOperator(), e.getParent(), e.getParentVariables());
                        else new_exp = new Expression(feed, null, e.getParent(), e.getParentVariables());
                    }catch(Exception ex){
                        System.out.println("Line 98");
                        System.out.println(ex);
                    }

                    e.set(new_exp);
                }else continue;

                return true;
            }
        }

        return false;
    }

    private boolean right_form(Expression e1, Expression e2){
        if(e2.getNumExpressions() > 2) return false;
        if(!e1.equals(e2.getExpressions()[0])) return false;
        return true;
    }

    private void fix_ret(Expression express){
        for(int i = 0; i < express.getNumExpressions(); i++){
            fix_ret(express.getExpressions()[i]);
        }
        express.setPolish();
    }
}
