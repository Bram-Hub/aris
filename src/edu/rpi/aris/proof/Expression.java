package edu.rpi.aris.proof;

import org.apache.commons.lang.ArrayUtils;

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

    public Expression(String expr) throws ExpressionParseException {
        this(expr, null, new String[0]);
    }

    public Expression(String expr, Expression parent, String[] parentVariables) throws ExpressionParseException {
        Objects.requireNonNull(parentVariables);
        this.parent = parent;
        this.parentVariables = parentVariables;
        init(expr);
    }

    //this constructor does not support functional operators
    public Expression(Expression[] expressions, Operator opr, Expression parent, String[] parentVariables) throws ExpressionParseException {
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

    private void init(String expr) throws ExpressionParseException {
        polishRep = expr;
        expr = SentenceUtil.removeParen(expr);
        int parenOffset = (polishRep.length() - expr.length()) / 2;
        if (!expr.contains(" ")) {
            isLiteral = true;
            expressions = new Expression[0];
            if ((parent == null || !parent.isFunctional) && !LITERAL_PATTERN.matcher(expr).matches()) {
                if (expr.length() == 0)
                    throw new ExpressionParseException("No expression given", -1, 0);
                if (Character.isDigit(expr.charAt(0)))
                    throw new ExpressionParseException("Literal cannot start with a number", parenOffset, 1);
                Matcher matcher = NOT_LITERAL_PATTERN.matcher(expr);
                if (matcher.find()) {
                    String symbol = matcher.group();
                    int start = matcher.start();
                    int end = matcher.end();
                    throw new ExpressionParseException("Unknown symbol in expression: \"" + symbol + "\"", start + parenOffset, end - start);
                }
                throw new ExpressionParseException("Not a literal: \"" + expr + "\"", parenOffset, expr.length());
            } else if (parent != null && parent.isFunctional && !SentenceUtil.CONSTANT_PATTERN.matcher(expr).matches() && !(SentenceUtil.VARIABLE_PATTERN.matcher(expr).matches() && (expr.equals(quantifierVar) || ArrayUtils.contains(parentVariables, expr)))) {
                throw new ExpressionParseException("Invalid function parameter: \"" + expr + "\" must be a constant or an introduced variable.", parenOffset, expr.length());
            }
            return;
        }
        String oprStr = expr.substring(0, expr.indexOf(' '));
        operator = Operator.getOperator(oprStr);
        if (operator == null) {
            if (!FUNCTION_PATTERN.matcher(oprStr).matches())
                throw new ExpressionParseException("Invalid function name: " + oprStr, parenOffset, oprStr.length());
            functionOperator = oprStr;
            isFunctional = true;
        } else if (operator.isQuantifier) {
            String varStr = oprStr.replaceFirst(operator.rep, "");
            if (!SentenceUtil.VARIABLE_PATTERN.matcher(varStr).matches())
                throw new ExpressionParseException("Invalid quantifier variable: " + varStr, parenOffset + 1, varStr.length());
            if (ArrayUtils.contains(parentVariables, varStr))
                throw new ExpressionParseException("Quantifier variable introduced twice in expression: " + varStr, parenOffset + 1, varStr.length());
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
                throw new ExpressionParseException("Multiple expressions given for unary operator", parenOffset + oprStr.length() + 1 + strExp.get(0).length() + 1, strExp.get(1).length());
            else if (!operator.canGeneralize && !operator.isUnary && strExp.size() != 2)
                throw new ExpressionParseException("Cannot create generalized " + operator.name(), parenOffset, polishRep.length() - parenOffset * 2);
        }
        expressions = new Expression[strExp.size()];
        String[] vars = parentVariables;
        if (operator != null && operator.isQuantifier) {
            vars = Arrays.copyOf(parentVariables, parentVariables.length + 1);
            vars[parentVariables.length] = quantifierVar;
        }
        for (int i = 0; i < strExp.size(); ++i) {
            Expression exp;
            int errorOffset = parenOffset + oprStr.length() + (i > 0 ? strExp.subList(0, i).stream().mapToInt(s -> s.length() + 1).sum() : 0) + 1;
            try {
                exp = new Expression(strExp.get(i), this, vars);
            } catch (ExpressionParseException e) {
                throw new ExpressionParseException(e.getMessage() + (operator != null && operator.isQuantifier ? " Make sure you are not missing a space after your quantifiers" : ""), errorOffset + e.getErrorOffset(), e.getErrorLength());
            }
            if (isFunctional && !SentenceUtil.CONSTANT_PATTERN.matcher(exp.polishRep).matches() && !(SentenceUtil.VARIABLE_PATTERN.matcher(exp.polishRep).matches() && (exp.polishRep.equals(quantifierVar) || ArrayUtils.contains(parentVariables, exp.polishRep))))
                throw new ExpressionParseException("Function must only contain introduced variables or constants", errorOffset, strExp.get(i).length());
            if (exp.isLiteral && !isFunctional && (exp.polishRep.equals(quantifierVar) || ArrayUtils.contains(parentVariables, exp.polishRep)))
                throw new ExpressionParseException("Invalid quantifier expression", errorOffset, strExp.get(i).length());
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

    public Expression negate() throws ExpressionParseException {
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
            return operator.logic + (operator.isQuantifier ? quantifierVar + " " : "") + expressions[0].toLogicString();
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
