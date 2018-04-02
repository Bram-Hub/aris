package edu.rpi.aris.proof;

import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class Expression {

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
        if (opr == null) {
            if (expressions.length != 1)
                throw new IllegalArgumentException("Must give exactly one expression if null operator");
            init(expressions[0].toString());
        } else if (opr.isType(Operator.Type.UNARY) && expressions.length != 1) {
            throw new IllegalArgumentException("Must give exactly 1 Expression for unary operator");
        } else if (!(opr.isType(Operator.Type.GENERALIZABLE_LOGIC) || opr.isType(Operator.Type.GENERALIZABLE_MATH)) && !opr.isType(Operator.Type.UNARY) && expressions.length != 2) {
            throw new IllegalArgumentException("Cannot create generalized " + opr.name());
        } else {
            init(SentenceUtil.toPolish(expressions, opr.rep));
        }
    }

    private void init
            (String expr) throws ExpressionParseException {
        polishRep = expr;
        expr = SentenceUtil.removeParen(expr);
        int parenOffset = (polishRep.length() - expr.length()) / 2;
        if (!expr.contains(" ")) {
            validateLiteral(expr, parenOffset);
            return;
        }
        String oprStr = expr.substring(0, expr.indexOf(' '));

        validateOperatorString(oprStr, parenOffset);

        //remove the operator from the expression
        expr = expr.substring(expr.indexOf(' ') + 1);

        ArrayList<String> strExp = SentenceUtil.depthAwareSplit(expr);
        validateSubExpressionCount(strExp, oprStr, parenOffset);

        expressions = new Expression[strExp.size()];
        String[] vars = parentVariables;
        if (operator != null && operator.isType(Operator.Type.QUANTIFIER)) {
            vars = Arrays.copyOf(parentVariables, parentVariables.length + 1);
            vars[parentVariables.length] = quantifierVar;
        }
        for (int i = 0; i < strExp.size(); ++i) {
            int errorOffset = parenOffset + oprStr.length() + (i > 0 ? strExp.subList(0, i).stream().mapToInt(s -> s.length() + 1).sum() : 0) + 1;
            expressions[i] = validateChildExpression(strExp.get(i), vars, errorOffset);
        }
    }

    private Expression validateChildExpression(String strExp, String[] vars, int errorOffset) throws ExpressionParseException {
        Expression exp;
        try {
            exp = new Expression(strExp, this, vars);
        } catch (ExpressionParseException e) {
            throw new ExpressionParseException(e.getMessage() + (operator != null && operator.isType(Operator.Type.QUANTIFIER) ? " Make sure you are not missing a space after your quantifiers" : ""), errorOffset + e.getErrorOffset(), e.getErrorLength());
        }
        int offset = (strExp.startsWith(String.valueOf(SentenceUtil.OP)) ? 1 : 0) + (exp.isLiteral ? 0 : (exp.isFunctional ? exp.functionOperator.length() : (exp.operator.rep.length() + (exp.quantifierVar == null ? 0 : exp.quantifierVar.length()) + 1)));
        int length = strExp.length() - (strExp.startsWith(String.valueOf(SentenceUtil.OP)) ? 1 : 0) - offset;
        offset += errorOffset;
        if (isFunctional) {
            if (!SentenceUtil.CONSTANT_PATTERN.matcher(exp.polishRep).matches() && !(SentenceUtil.VARIABLE_PATTERN.matcher(exp.polishRep).matches() && ArrayUtils.contains(vars, exp.polishRep)))
                throw new ExpressionParseException("Function must only contain introduced variables or constants", offset, length);
        } else if (operator.isType(Operator.Type.MATH)) {
            if (!exp.isLiteral && !exp.operator.isType(Operator.Type.MATH) && !exp.isFunctional)
                throw new ExpressionParseException("Unexpected subexpression in math expression", offset, length);
        } else if (operator.isType(Operator.Type.SET)) {
            if (!exp.isLiteral)
                throw new ExpressionParseException("Set operations can only join constants and variables", offset, length);
        } else if (operator.isType(Operator.Type.EQUIVALENCE)) {
            if (!exp.isLiteral && !exp.isFunctional && !exp.operator.isType(Operator.Type.MATH))
                throw new ExpressionParseException("Equivalence operators can only join functional and math operators", offset, length);
        }
        return exp;
    }

    private void validateSubExpressionCount(ArrayList<String> strExp, String oprStr, int errorOffset) throws ExpressionParseException {
        if (operator != null) {
            if (operator.isType(Operator.Type.UNARY) && strExp.size() != 1)
                throw new ExpressionParseException("Multiple expressions given for unary operator", errorOffset + oprStr.length() + 1 + strExp.get(0).length() + 1, strExp.get(1).length());
            else if (!(operator.isType(Operator.Type.GENERALIZABLE_LOGIC) || operator.isType(Operator.Type.GENERALIZABLE_MATH)) && !operator.isType(Operator.Type.UNARY) && strExp.size() != 2)
                throw new ExpressionParseException("Cannot create generalized " + operator.name().toLowerCase(), errorOffset + 2, polishRep.length() - errorOffset * 2 - 2);
            else if ((operator.isType(Operator.Type.GENERALIZABLE_LOGIC) || operator.isType(Operator.Type.GENERALIZABLE_MATH)) && strExp.size() < 2)
                throw new ExpressionParseException("Must have at least 2 parameters for operator " + operator.name().toLowerCase(), errorOffset + 2, polishRep.length() - errorOffset * 2 - 2);
        }
    }

    private void validateOperatorString(String oprStr, int errorOffset) throws ExpressionParseException {
        operator = Operator.getOperator(oprStr);
        if (operator == null) {
            if (!SentenceUtil.FUNCTION_PATTERN.matcher(oprStr).matches())
                throw new ExpressionParseException("Invalid function name: " + oprStr, errorOffset, oprStr.length());
            functionOperator = oprStr;
            isFunctional = true;
        } else if (operator.isType(Operator.Type.QUANTIFIER)) {
            String varStr = oprStr.replaceFirst(operator.rep, "");
            if (!SentenceUtil.VARIABLE_PATTERN.matcher(varStr).matches())
                throw new ExpressionParseException("Invalid quantifier variable: " + varStr, errorOffset + 1, varStr.length());
            if (ArrayUtils.contains(parentVariables, varStr))
                throw new ExpressionParseException("Quantifier variable introduced twice in expression: " + varStr, errorOffset + 1, varStr.length());
            quantifierVar = varStr;
        } else if (operator.isType(Operator.Type.MATH)) {
            if (parent == null)
                throw new ExpressionParseException("Math expression cannot be top level expression", errorOffset, oprStr.length());
            else if (parent.operator == null || (!parent.operator.isType(Operator.Type.MATH) && !parent.operator.isType(Operator.Type.EQUIVALENCE)))
                throw new ExpressionParseException("Math expressions can only be joined with other math operators and equivalence operators", errorOffset, oprStr.length());
        } else if (operator.isType(Operator.Type.EQUIVALENCE)) {
            if (parent != null)
                throw new ExpressionParseException("Equivalence expression must be top level expression", errorOffset, oprStr.length());
        }
    }

    private void validateLiteral(String expr, int errorOffset) throws ExpressionParseException {
        isLiteral = true;
        expressions = new Expression[0];
        if (expr.length() == 0)
            throw new ExpressionParseException("No expression given", -1, 0);
        if (parent != null && (parent.isFunctional || parent.operator.isVariableOperator())) {
            if (!SentenceUtil.CONSTANT_PATTERN.matcher(expr).matches() && !(ArrayUtils.contains(parentVariables, expr) && SentenceUtil.VARIABLE_PATTERN.matcher(expr).matches()))
                throw new ExpressionParseException("Invalid expression \"" + expr + "\" must be a constant or an introduced variable", errorOffset, expr.length());
        } else {
            if (!SentenceUtil.LITERAL_PATTERN.matcher(expr).matches())
                throw new ExpressionParseException("Invalid literal in expression \"" + expr + "\"", errorOffset, expr.length());
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

    public boolean hasSubExpressionwithoutDNs(Expression exp) throws ExpressionParseException {
        for (Expression e : expressions)
            if (e.equalswithoutDNs(exp))
                return true;
        return false;
    }

    public boolean hasSubExpressionFullPower(Expression exp) throws ExpressionParseException {
        for (Expression e : expressions)
            if (e.equalsFullPower(exp))
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
        return new Expression(new Expression[]{this}, Operator.NOT, parent, parentVariables);
    }

    //equals with commutativity for all and associativity for generalized premises
    public boolean equalsFullPower(Expression expr) throws ExpressionParseException {
        if ((operator == expr.operator) && (getNumExpressions() == expr.getNumExpressions())) {
            if (operator == null) {
                return this.equalswithoutDNs(expr);
            } else if (operator.isType(Operator.Type.GENERALIZABLE_LOGIC) || operator.isType(Operator.Type.GENERALIZABLE_MATH)) {
                ArrayList<Expression> expressions = new ArrayList<>(Arrays.asList(getExpressions()));
                for (Expression subExpr : getExpressions()) {
                    boolean found = false;
                    for (int i = 0; i < expressions.size(); ++i) {
                        if (subExpr.equalsFullPower(expressions.get(i))) {
                            expressions.remove(i);
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        return false;
                    }
                }
                return true;
            } else {
                boolean ret = true;
                for (int i = 0; i < getNumExpressions(); ++i) {
                    ret = (ret && getExpressions()[i].equalsFullPower(expr.getExpressions()[i]));
                }
                return ret;
            }
        } else {
            return false;
        }
    }

    //recursively removes double negations
    public Expression withoutDNs() throws ExpressionParseException {
        if (operator == Operator.NOT) {
            Expression expr = expressions[0];
            if (expr.operator == Operator.NOT) {
                return expr.expressions[0].withoutDNs();
            } else {
                if (expr.getNumExpressions() > 0) {
                    Expression subExprs[] = new Expression[expr.getNumExpressions()];
                    for (int i = 0; i < expr.getNumExpressions(); ++i) {
                        subExprs[i] = expr.getExpressions()[i].withoutDNs();
                    }
                    return new Expression(new Expression[]{new Expression(subExprs, expr.operator, expr.parent, expr.parentVariables)}, Operator.NOT, parent, parentVariables);
                } else {
                    return new Expression(new Expression[]{expr}, Operator.NOT, parent, parentVariables);
                }
            }
        }
        if (getNumExpressions() > 0) {
            Expression subExprs[] = new Expression[getNumExpressions()];
            for (int i = 0; i < getNumExpressions(); ++i) {
                subExprs[i] = getExpressions()[i].withoutDNs();
            }
            return new Expression(subExprs, operator, parent, parentVariables);
        } else {
            return this;
        }
    }

    //non-recursive implementation
    /*public Expression withoutDNs() {
        if (operator == Operator.NOT) {
            Expression expr = expressions[0];
            if (expr.operator == Operator.NOT) {
                return expr.expressions[0].withoutDNs();
            }
        }
        return this;
    }*/

    //equals with outermost double negations removed from both sides
    public boolean equalswithoutDNs(Expression expr) throws ExpressionParseException {
        return this.withoutDNs().equals(expr.withoutDNs());
    }
    public String toLogicStringwithoutDNs() throws ExpressionParseException {
        return this.withoutDNs().toLogicString();
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
        if (operator.isType(Operator.Type.UNARY))
            return operator.rep + (operator.isType(Operator.Type.QUANTIFIER) ? quantifierVar + " " : "") + expressions[0].toLogicString();
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < expressions.length; ++i) {
            sb.append(expressions[i].toLogicString());
            if (i < expressions.length - 1)
                sb.append(" ").append(operator.rep).append(" ");
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
