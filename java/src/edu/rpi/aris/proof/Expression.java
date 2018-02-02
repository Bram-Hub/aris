package edu.rpi.aris.proof;

import com.sun.istack.internal.NotNull;

import java.util.ArrayList;
import java.util.Objects;

public class Expression {

    private Operator operator = null;
    private String polishRep;
    private String functionOperator = null;
    private boolean isFunctional = false;
    private boolean isLiteral = false;
    private Expression[] expressions = null;

    public Expression(String expr) {
        init(expr);
    }

    //this constructor does not support functional operators
    public Expression(@NotNull Expression[] exprs, Operator opr) {
        Objects.requireNonNull(exprs);
        if (operator == null) {
            if (exprs.length != 1)
                throw new IllegalArgumentException("Must give exactly one expression if null operator");
            init(exprs[0].toString());
        } else if (operator.isUnary && exprs.length != 1) {
            throw new IllegalArgumentException("Must give exactly 1 Expression for unary operator");
        } else
            init(SentenceUtil.toPolish(expressions, opr.rep));
    }

//    public static String fromRegexString(String regexString) {
//        return regexString.replace(REGEX_SPACE, ' ');
//    }

    private void init(String expr) {
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

    @Override
    public String toString() {
        return polishRep;
    }

//    public String toRegexString() {
//        if (isLiteral) {
//            return polishRep;
//        }
//        String opr = isFunctional ? functionOperator : operator.rep;
//        StringBuilder sb = new StringBuilder();
//        sb.append(SentenceUtil.OP).append(opr).append(" ");
//        for (int i = 0; i < expressions.length; ++i)
//            sb.append(expressions[i].toString().replace(' ', REGEX_SPACE)).append(i + 1 == expressions.length ? "" : " ");
//        return sb.append(SentenceUtil.CP).toString();
//    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Expression && polishRep.equals(((Expression) obj).polishRep);
    }

    @Override
    public int hashCode() {
        return polishRep.hashCode();
    }
}
