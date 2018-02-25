package edu.rpi.aris.proof;

import org.apache.commons.lang.ArrayUtils;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Expression {

    private static final Pattern LITERAL_PATTERN = Pattern.compile("[A-Z][A-Za-z0-9]*|⊥");
    private static final Pattern NOT_LITERAL_PATTERN = Pattern.compile("[^A-Za-z0-9⊥]");
    private static final Pattern FUNCTION_PATTERN = Pattern.compile("[A-Z][A-Za-z0-9]*");
    private Operator operator = null;
    private String polishRep;
    private String functionOperator = null;
    private String quantifierVar = null;
    private boolean isFunctional = false;
    private boolean isLiteral = false;
    private Expression[] expressions = null;
    private Expression parent = null;
    private String[] parentVariables;

    public Expression(String expr) throws ParseException {
        this(expr, null, new String[0]);
    }

    public Expression(String expr, Expression parent, String[] parentVariables) throws ParseException {
        Objects.requireNonNull(parentVariables);
        this.parent = parent;
        this.parentVariables = parentVariables;
        init(expr);
    }

    //this constructor does not support functional operators
    public Expression(Expression[] expressions, Operator opr, Expression parent, String[] parentVariables) throws ParseException {
        Objects.requireNonNull(expressions);
        Objects.requireNonNull(parentVariables);
        this.parent = parent;
        this.parentVariables = parentVariables;
        if (operator == null) {
            if (expressions.length != 1)
                throw new IllegalArgumentException("Must give exactly one expression if null operator");
            init(expressions[0].toString());
        } else if (operator.isUnary && expressions.length != 1) {
            throw new IllegalArgumentException("Must give exactly 1 Expression for unary operator");
        } else if (!operator.canGeneralize && !operator.isUnary && expressions.length != 2) {
            throw new IllegalArgumentException("Cannot create generalized " + operator.name());
        } else
            init(SentenceUtil.toPolish(this.expressions, opr.rep));
    }

    private void init(String expr) throws ParseException {
        polishRep = expr;
        expr = SentenceUtil.removeParen(expr);
        if (!expr.contains(" ")) {
            if ((parent == null || !parent.isFunctional) && !LITERAL_PATTERN.matcher(expr).matches()) {
                if (expr.length() == 0)
                    throw new ParseException("No expression given", -1);
                if (Character.isDigit(expr.charAt(0)))
                    throw new ParseException("Literal cannot start with a number", -1);
                Matcher matcher = NOT_LITERAL_PATTERN.matcher(expr);
                if (matcher.find()) {
                    String symbol = matcher.group();
                    throw new ParseException("Unknown symbol in expression: " + symbol, -1);
                }
                throw new ParseException("Not a literal: " + expr, -1);
            } else if(parent != null && parent.isFunctional && !SentenceUtil.VARIABLE_PATTERN.matcher(expr).matches()) {
                throw new ParseException("Invalid function variable: " + expr, -1);
            }
            polishRep = expr;
            isLiteral = true;
            expressions = new Expression[0];
            return;
        }
        String oprStr = expr.substring(0, expr.indexOf(' '));
        operator = Operator.getOperator(oprStr);
        if (operator == null) {
            if (!FUNCTION_PATTERN.matcher(oprStr).matches())
                throw new ParseException("Invalid function name: " + oprStr, -1);
            functionOperator = oprStr;
            isFunctional = true;
        } else if (operator.isQuantifier) {
            String varStr = oprStr.replaceFirst(operator.rep, "");
            if (!SentenceUtil.VARIABLE_PATTERN.matcher(varStr).matches())
                throw new ParseException("Invalid quantifier variable: " + varStr, -1);
            if (ArrayUtils.contains(parentVariables, varStr))
                throw new ParseException("Quantifier variable introduced twice in expression: " + varStr, -1);
            quantifierVar = varStr;
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
        String[] vars = parentVariables;
        if (operator != null && operator.isQuantifier) {
            vars = Arrays.copyOf(parentVariables, parentVariables.length + 1);
            vars[parentVariables.length] = quantifierVar;
        }
        for (int i = 0; i < strExp.size(); ++i) {
            Expression exp = new Expression(strExp.get(i), this, vars);
            if (isFunctional && !SentenceUtil.VARIABLE_PATTERN.matcher(exp.polishRep).matches())
                throw new ParseException("Function must only contain variables", -1);
            if (exp.isLiteral && !isFunctional && (exp.polishRep.equals(quantifierVar) || ArrayUtils.contains(parentVariables, exp.polishRep)))
                throw new ParseException("Invalid quantifier expression", -1);
            expressions[i] = exp;
        }
    }

    public Operator getOperator() {
        return operator;
    }

    public String getFunctionOperator() {
        return functionOperator;
    }

    public String getQuantifierVariable() {
        return quantifierVar;
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

    public int getNumExpressions() {
        return expressions.length;
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
        return new Expression(new Expression[]{this}, Operator.NOT, this.parent, parentVariables);
    }

    public String toLogicString() {
        if (isLiteral)
            return polishRep;
        if (isFunctional) {
            StringBuilder sb = new StringBuilder(functionOperator);
            sb.append("(");
            for (int i = 0; i < expressions.length; ++i) {
                sb.append(expressions[i].toLogicString());
                if (i < expressions.length - 1)
                    sb.append(", ");
            }
            return sb.append(")").toString();
        }
        if (operator.isUnary)
            return operator.logic + expressions[0].toLogicString();
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < expressions.length; ++i) {
            sb.append(expressions[i].toLogicString());
            if (i < expressions.length - 1)
                sb.append(" ").append(operator.logic).append(" ");
        }
        return sb.append(")").toString();
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
