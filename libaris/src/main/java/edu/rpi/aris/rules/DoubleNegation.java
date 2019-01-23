package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.ExpressionParseException;
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
        Expression temp = premises[0].getPremise();

        Expression premise;
        Expression conc;
        try {
            premise=new Expression(temp.getExpressions(), temp.getOperator(), temp.getParent(), temp.getParentVariables());
            conc = new Expression(conclusion.getExpressions(), conclusion.getOperator(),conclusion.getParent(),conclusion.getParentVariables());
        }catch (Exception e){
            return "Failed";
        }

        if(premise.getNumExpressions() == 0) return "No Double Negation to be performed";

        remove_DN(premise);
        remove_DN(conc);

        if(!premise.equals(conc)) return "Improper Removal of Double Negations";
        return null;
    }

    private void remove_DN(Expression expr){
        if(expr.getNumExpressions() == 0) return;
        if(expr.getOperator().equals(Operator.NOT) && expr.getExpressions()[0].getNumExpressions() > 0 && expr.getExpressions()[0].getOperator().equals(Operator.NOT)){
            Expression new_expr = null;
            try{
                Expression[] feed = new Expression[1];
                if(expr.getExpressions()[0].getExpressions()[0].getNumExpressions() == 0)
                    feed[0] = expr.getExpressions()[0].getExpressions()[0];
                else feed = expr.getExpressions()[0].getExpressions()[0].getExpressions();
                new_expr = new Expression(feed, expr.getExpressions()[0].getExpressions()[0].getOperator(), expr.getExpressions()[0].getExpressions()[0].getParent(), expr.getExpressions()[0].getExpressions()[0].getParentVariables());
            }catch(Exception e){
                System.out.println(e);
            }
            expr.set(new_expr);
            fix_ret(expr);
        }
        for(int i = 0; i < expr.getNumExpressions(); i++){
            remove_DN(expr.getExpressions()[i]);
        }
        fix_ret(expr);
    }

    private void fix_ret(Expression express){
        for(int i = 0; i < express.getNumExpressions(); i++){
            fix_ret(express.getExpressions()[i]);
        }
        express.setPolish();
    }








}