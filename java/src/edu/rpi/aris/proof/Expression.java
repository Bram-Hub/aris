package edu.rpi.aris.proof;

import java.util.ArrayList;

public class Expression {

    private Operator operator = null;
    private String polishRep;
    private String functionOperator = null;
    private boolean isFuntional = false;
    private boolean isLiteral = false;
    private Expression[] expressions = null;

    public Expression(String expr) {
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
            isFuntional = true;
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
        if (strExp.size() > 1) {
            expressions = new Expression[strExp.size()];
            for (int i = 0; i < strExp.size(); ++i)
                expressions[i] = new Expression(strExp.get(i));
        }
    }

    public Operator getOperator() {
        return operator;
    }

    public String getFunctionOperator() {
        return functionOperator;
    }

    public boolean isFuntional() {
        return isFuntional;
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
