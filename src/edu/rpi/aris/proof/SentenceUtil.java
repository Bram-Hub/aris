package edu.rpi.aris.proof;


import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SentenceUtil {

    public static final Pattern VARIABLE_PATTERN = Pattern.compile("[a-z][A-Za-z0-9]*");
    public static final Pattern QUANTIFIER_PATTERN;

    static {
        StringBuilder quantifierPattern = new StringBuilder();
        for (Operator o : Operator.QUANTIFIER_OPERATOR) {
            quantifierPattern.append(o.logic);
        }
        String q = quantifierPattern.toString();
        quantifierPattern.insert(0, "[");
        quantifierPattern.append("] *").append(VARIABLE_PATTERN.pattern()).append("(?=[ (").append(q).append("])");
        QUANTIFIER_PATTERN = Pattern.compile(quantifierPattern.toString());
    }

    public static final char OP = '(';
    public static final char CP = ')';

    public static int checkParen(String expr) {
        if (expr.startsWith(Character.toString(OP)) && !expr.endsWith(Character.toString(CP)))
            return 0;
        int opCount = 0;
        int cpCount = 0;
        char[] chars = expr.toCharArray();
        for (int i = 0; i < chars.length; ++i) {
            char c = chars[i];
            if (c == OP)
                opCount++;
            else if (c == CP) {
                cpCount++;
                if (cpCount < 0)
                    return i;
            }
        }
        return opCount - cpCount == 0 ? -1 : findParenMismatch(expr, opCount, cpCount);
    }

    private static int findParenMismatch(String expr, int opCount, int cpCount) {
        char P = cpCount > opCount ? CP : OP;
        int target = cpCount > opCount ? opCount : cpCount;
        int count = 0;
        for (int i = 0; i < expr.length(); ++i) {
            if (expr.charAt(i) == P) {
                if (count >= target)
                    return i;
                count++;
            }
        }
        return -1;
    }

    public static String removeWhitespace(String expr) {
        return expr.replaceAll("\\s", "");
    }

    public static String removeParen(String expr) throws ParseException {
        if (expr.startsWith(Character.toString(OP))) {
            boolean rmParen = true;
            int count = 0;
            for (int i = 0; i < expr.length(); i++) {
                if (expr.charAt(i) == OP)
                    count++;
                if (expr.charAt(i) == CP)
                    count--;
                if (count == 0 && i < expr.length() - 1) {
                    rmParen = false;
                    break;
                }
            }
            if (rmParen) {
                if (count == 0)
                    return removeParen(expr.substring(0, expr.length() - 1).substring(1));
                else
                    throw new ParseException("Unbalanced parentheses in expression", -1);
            }
        }
        return expr;
    }

    public static Expression toExpression(String expr) throws ParseException {
        return new Expression(toPolishNotation(expr));
    }

    public static String toPolishNotation(String expr) throws ParseException {
        int i;

        if((i = checkParen(expr)) != -1)
            throw new ParseException("Unbalanced parentheses in expression", i);
        return toPolish(removeParen(removeWhitespace(expr)), findQuantifiers(expr));
    }

    private static LinkedList<String> findQuantifiers(String expr) {
        LinkedList<String> quantifiers = new LinkedList<>();
        Matcher m = QUANTIFIER_PATTERN.matcher(expr);
        while (m.find())
            quantifiers.add(m.group().replace(" ", ""));
        return quantifiers;
    }

    private static String toPolish(String expr, LinkedList<String> quantifiers) throws ParseException {
        if (expr.length() == 0)
            throw new ParseException("Empty expression found in sentence", -1);
        int parenDepth = 0;
        Operator oper = null;
        ArrayList<String> exprs = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < expr.length(); ++i) {
            char c = expr.charAt(i);
            Operator tmpOpr;
            if (c == OP)
                parenDepth++;
            else if (c == CP)
                parenDepth--;
            else if (parenDepth == 0 && (tmpOpr = getBoolOpr(c)) != null) {
                if (oper == null)
                    oper = tmpOpr;
                if (tmpOpr == oper) {
                    if (start == i)
                        throw new ParseException("Binary operator needs to connect 2 expressions", i);
                    exprs.add(expr.substring(start, i));
                    start = i + 1;
                } else
                    throw new ParseException("Invalid operator in generalized " + oper.name().toLowerCase(), i);
            }
        }
        exprs.add(expr.substring(start));
        if (oper != null) {
            for (int i = 0; i < exprs.size(); ++i) {
                String exp = exprs.get(i);
                if (exp.length() == 0)
                    throw new ParseException("Binary connective missing expression", -1);
                exp = toPolish(removeParen(exp), quantifiers);
                exprs.set(i, exp);
            }
            return OP + oper.rep + " " + join(exprs) + CP;
        } else {
            String exp = expr;
            Operator opr;
            if ((opr = getUnaryOpr(exp.charAt(0))) != null) {
                if (opr.isQuantifier) {
                    String quantifier = quantifiers.pollFirst();
                    if (quantifier == null)
                        throw new ParseException("Malformed quantifier in expression", -1);
                    exp = exp.substring(quantifier.length());
                    exp = toPolish(removeParen(exp), quantifiers);
                    exp = OP + quantifier + " " + exp + CP;
                } else {
                    exp = exp.substring(1);
                    exp = toPolish(removeParen(exp), quantifiers);
                    exp = OP + opr.rep + " " + exp + CP;
                }
            } else if (exp.charAt(0) != OP) {
                int argStart = -1;
                String[] args = null;
                String fun = null;
                for (int i = 0; i < exp.length(); ++i) {
                    char c = exp.charAt(i);
                    if (c == OP) {
                        if (argStart != -1)
                            throw new ParseException("Functions can only contain comma separated literals", i);
                        fun = exp.substring(0, i);
                        argStart = i + 1;
                    } else if (c == CP) {
                        if (argStart == -1)
                            throw new ParseException("No matching open parentheses for closing parentheses", i);
                        if(exp.substring(i).length() > 1)
                            throw new ParseException("Invalid function definition", -1);
                        if(exp.substring(argStart, i).startsWith(","))
                            throw new ParseException("Missing first function parameter", -1);
                        if(exp.substring(argStart, i).endsWith(","))
                            throw new ParseException("Missing last function parameter", -1);
                        args = exp.substring(argStart, i).split(",");
                    }
                }
                if (argStart != -1) {
                    if (args == null)
                        throw new ParseException("Failed to parse function", 0);
                    exp = OP + fun + " " + join(args) + CP;
                }
            }
            return exp;
        }
    }

    public static String toPolish(Expression[] exprs, String opr) {
        Objects.requireNonNull(exprs);
        Objects.requireNonNull(opr);
        ArrayList<String> polish = Arrays.stream(exprs).map(Expression::toString).collect(Collectors.toCollection(ArrayList::new));
        return OP + opr + " " + join(polish) + CP;
    }

    public static String join(ArrayList<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); ++i) {
            sb.append(list.get(i));
            if (i < list.size() - 1)
                sb.append(" ");
        }
        return sb.toString();
    }

    public static String join(String[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; ++i) {
            sb.append(arr[i]);
            if (i < arr.length - 1)
                sb.append(" ");
        }
        return sb.toString();
    }

    private static Operator getBoolOpr(char c) {
        for (Operator opr : Operator.BINARY_OPERATOR)
            if (c == opr.logic)
                return opr;
        return null;
    }

    private static Operator getUnaryOpr(char c) {
        for (Operator opr : Operator.UNARY_OPERATOR)
            if (c == opr.logic)
                return opr;
        return null;
    }

}
