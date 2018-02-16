package edu.rpi.aris.proof;

import com.sun.istack.internal.NotNull;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Objects;

public class Expression {

    private Operator operator = null;
    private String polishRep;
    private String functionOperator = null;
    private boolean isFunctional = false;
    private boolean isLiteral = false;
    private Expression[] expressions = null;

    public Expression(String expr) throws ParseException {
        init(expr);
    }

    //this constructor does not support functional operators
    public Expression(@NotNull Expression[] exprs, Operator opr) throws ParseException {
        Objects.requireNonNull(exprs);
        if (operator == null) {
            if (exprs.length != 1)
                throw new IllegalArgumentException("Must give exactly one expression if null operator");
            init(exprs[0].toString());
        } else if (operator.isUnary && exprs.length != 1) {
            throw new IllegalArgumentException("Must give exactly 1 Expression for unary operator");
        } else if (!operator.canGeneralize && !operator.isUnary && exprs.length != 2) {
            throw new IllegalArgumentException("Cannot create generalized " + operator.name());
        } else
            init(SentenceUtil.toPolish(expressions, opr.rep));
    }

    private void init(String expr) throws ParseException {
        polishRep = expr;
        expr = SentenceUtil.removeParen(expr);
        if (!expr.contains(" ")) {
            polishRep = expr;
            isLiteral = true;
            expressions = new Expression[0];
            return;
        }
        String oprStr = expr.substring(0, expr.indexOf(' '));
        operator = Operator.getOperator(oprStr);
        if (operator == null) {
            functionOperator = oprStr;
            isFunctional = true;
        }
        expr = expr.substring(expr.indexOf(' ') + 1);
        ArrayList<String> strExp = new ArrayList<>();
        char[] charExp = expr.toCharArray();
        int parenDepth = 0;
        int start = 0;
        for (int i = 0; i < charExp.length; ++i) {
            char c = charExp[i];
            if (c == SentenceUtil.OP)
                parenDepth++;
            else if (c == SentenceUtil.CP)
                parenDepth--;
            else if (c == ' ' && parenDepth == 0) {
                strExp.add(expr.substring(start, i));
                start = i + 1;
            }
        }
        strExp.add(expr.substring(start));
        if (operator != null) {
            if (operator.isUnary && strExp.size() != 1)
                throw new ParseException("Multiple expressions given for unary operator", -1);
            else if (!operator.canGeneralize && !operator.isUnary && strExp.size() != 2)
                throw new ParseException("Cannot create generalized " + operator.name(), -1);
        }
        expressions = new Expression[strExp.size()];
        for (int i = 0; i < strExp.size(); ++i)
            expressions[i] = new Expression(strExp.get(i));
    }

    public Operator getOperator() {
        return operator;
    }

    public String getFunctionOperator() {
        return functionOperator;
    }

    public boolean isFunctional() {
        return isFunctional;
    }

    public boolean isLiteral() {
        return isLiteral;
    }

    public Expression[] getExpressions() {
        return expressions;
    }

    public boolean hasSubExpression(Expression exp) {
        for (Expression e : expressions)
            if (e.equals(exp))
                return true;
        return false;
    }

    private boolean sameFunOpr(String opr1, String opr2) {
        return opr1 == null && opr2 == null || opr1 != null && opr2 != null && opr1.equals(opr2);
    }

    public boolean startsWith(Expression exp) {
        if (exp.operator != operator || !sameFunOpr(functionOperator, exp.functionOperator))
            return false;
        if (exp.expressions.length > expressions.length)
            return false;
        for (int i = 0; i < exp.expressions.length; i++)
            if (!exp.expressions[i].equals(expressions[i]))
                return false;
        return true;
    }

    public boolean endsWith(Expression exp) {
        if (exp.operator != operator || !sameFunOpr(functionOperator, exp.functionOperator))
            return false;
        if (exp.expressions.length > expressions.length)
            return false;
        for (int i = 1; i <= exp.expressions.length; i++)
            if (!exp.expressions[exp.expressions.length - i].equals(expressions[expressions.length - i]))
                return false;
        return true;
    }

    public Expression negate() throws ParseException {
        return new Expression(new Expression[]{this}, Operator.NOT);
    }

    @Override
    public String toString() {
        return polishRep;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Expression && polishRep.equals(((Expression) obj).polishRep);
    }

    @Override
    public int hashCode() {
        return polishRep.hashCode();
    }
}
