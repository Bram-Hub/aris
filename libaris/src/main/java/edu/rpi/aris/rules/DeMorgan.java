package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.ExpressionParseException;
import edu.rpi.aris.proof.Operator;
import edu.rpi.aris.proof.Premise;
import javafx.beans.binding.ObjectExpression;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

public class DeMorgan extends Rule {

    DeMorgan() {

    }

    @Override
    public String getName() {
        return "DeMorgans";
    }

    @Override
    public String getSimpleName() {
        return "DM";
    }

    @Override
    public Type[] getRuleType() {
        return new Type[]{Type.EQUIVALENCE};
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
        if(temp.getNumExpressions() == 0) return "Can't apply DeMorgans anywhere";
        Expression premise;
        Expression conc;
        try {
            premise=new Expression(temp.getExpressions(), temp.getOperator(), temp.getParent(), temp.getParentVariables());
            if(conclusion.getNumExpressions() != 0)
                conc = new Expression(conclusion.getExpressions(), conclusion.getOperator(),conclusion.getParent(),conclusion.getParentVariables());
            else conc = new Expression(conclusion.getExpressions(), null,conclusion.getParent(),conclusion.getParentVariables());
        }catch (Exception e){
            return "Failed";
        }
//        System.out.println("Before Demorgans: " + premise.toLogicString());
        apply_DM(premise);
//        fix_ret(premise);
//        System.out.println("Immediately AFter: " + premise);
        if(premise.getOperator().equals(Operator.NOT)){
            try{
                premise = new Expression(premise.getExpressions()[0].getExpressions(), premise.getExpressions()[0].getOperator(), premise.getParent(),premise.getParentVariables());
            }catch(Exception e){
                System.out.println("Line 80");
                System.out.println(e);
            }
        }
        remove_DN(premise);
        remove_DN(conc);
//        System.out.println("After Demorgans: " + premise + " " + premise.getOperator());
//        System.out.println("Conclusion: " + conclusion + " " + conclusion.getOperator());
        if(!premise.equals(conc)) return "Non-valid application of demorgan's";
        return null;
    }

    private boolean apply_DM(Expression prem){
        if(prem.getNumExpressions() == 1) {
            Expression temp = null;
            try{
                temp=new Expression(prem.getExpressions(), prem.getOperator(), prem.getParent(), prem.getParentVariables());
            }catch(Exception e) {
                System.out.println("Line 97");
                System.out.println(e);
            }
            prem.set(remove_DM(prem));
//            System.out.println("IN apply: " + prem);
            fix_ret(prem);
            if(temp == null){
                return true;
            }
            if(!temp.equals(prem)){
                return true;
            }
        }

        boolean stop = false;
        for(int i = 0; i < prem.getNumExpressions(); i++){
            if(stop) return true;
            Expression temp = null;
            try{
                temp=new Expression(prem.getExpressions(), prem.getOperator(), prem.getParent(), prem.getParentVariables());
            }catch(Exception e) {
                System.out.println(e);
            }
            prem.getExpressions()[i] = remove_DM(prem.getExpressions()[i]);
            fix_ret(prem);
            if(!temp.equals(prem)) return true;
            if(prem.getExpressions()[i].getNumExpressions() > 0) stop = apply_DM((prem.getExpressions()[i]));
        }

        return false;
    }

    private Expression remove_DM(Expression expr) {
        Expression ret = expr;
        if(ret.getOperator() == Operator.NOT && ret.getExpressions()[0].getNumExpressions() > 0){
            Operator op = ret.getExpressions()[0].getOperator();

            if(op == Operator.AND || op == Operator.OR){

                for(int i = 0;i < ret.getExpressions()[0].getNumExpressions(); i++){
                    try{
                        Expression new_expression = ret.getExpressions()[0].getExpressions()[i].negate();
                        ret.getExpressions()[0].getExpressions()[i].set(new_expression);
                    }catch(ExpressionParseException e){
                        System.out.print("Line 142");
                        System.out.println(e);
                    }
                }
                if(op.equals(Operator.AND)) {ret.getExpressions()[0].setOperator(Operator.OR);}
                else {ret.getExpressions()[0].setOperator(Operator.AND);}
                ret = ret.getExpressions()[0];
                fix_ret(ret);
            }
            else if(op == Operator.CONDITIONAL){
                Expression new_expression;
                try {
                    if(ret.getExpressions()[0].getExpressions()[1].getNumExpressions() > 0 && ret.getExpressions()[0].getExpressions()[1].getOperator().equals(Operator.NOT)){
                        Expression[] feed = new Expression[1];
                        feed[0] = ret.getExpressions()[0].getExpressions()[1];
                        new_expression = new Expression(feed, feed[0].getOperator(), feed[0].getParent(), feed[0].getParentVariables());
                    }else new_expression = ret.getExpressions()[0].getExpressions()[1].negate();
                    ret.getExpressions()[0].getExpressions()[1].set(new_expression);
                }catch (Exception e){
                    System.out.println("line 161");
                    System.out.println(e);
                }

                ret.getExpressions()[0].setOperator(Operator.AND);

                ret = ret.getExpressions()[0];

                fix_ret(ret);
            }
            else if(op == Operator.NOT){
                ret = ret.getExpressions()[0].getExpressions()[0];
                fix_ret(ret);
            }
        }

//        System.out.println("Ret: " + ret);
        return ret;
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
                System.out.println("Line 190");
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