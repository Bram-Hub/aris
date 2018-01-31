package edu.rpi.aris.proof;

import java.text.ParseException;
import java.util.ArrayList;

public class SentenceUtil {

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
                if (count > target)
                    return i;
                count++;
            }
        }
        return -1;
    }

    public static String removeWhitespace(String expr) {
        return expr.replaceAll("\\s", "");
    }

    public static String removeParen(String expr) {
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
            if (rmParen)
                return removeParen(expr.substring(0, expr.length() - 1).substring(1));
        }
        return expr;
    }

    public static String toPolishNotation(String expr) throws ParseException {
        return toPolish(removeParen(removeWhitespace(expr)));
    }

    private static String toPolish(String expr) throws ParseException {
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
                        throw new ParseException("Boolean operator needs to connect 2 expressions", i);
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
                Operator opr;
                if ((opr = getUnaryOpr(exp.charAt(0))) != null)
                    exp = exp.substring(1);
                exp = toPolish(removeParen(exp));
                if (opr != null)
                    exp = OP + opr.rep + " " + exp + CP;
                exprs.set(i, exp);
            }
            return OP + oper.rep + " " + join(exprs) + CP;
        } else {
            String exp = expr;
            Operator opr;
            if ((opr = getUnaryOpr(exp.charAt(0))) != null) {
                exp = exp.substring(1);
                exp = toPolish(removeParen(exp));
                exp = OP + opr.rep + " " + exp + CP;
            } else if (exp.charAt(0) != OP) {
                int argStart = -1;
                String[] args = null;
                String fun = null;
                for (int i = 0; i < exp.length(); ++i) {
                    char c = exp.charAt(i);
                    if (c == OP) {
                        if (argStart != -1)
                            throw new ParseException("Missing closing parentheses in function", i);
                        fun = exp.substring(0, i);
                        argStart = i + 1;
                    } else if (c == CP) {
                        if (argStart == -1)
                            throw new ParseException("No matching open parentheses for closing parentheses", i);
                        if (args != null)
                            throw new ParseException("Double closing parentheses found", i);
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

    public static Operator getBoolOpr(char c) {
        for (Operator opr : Operator.BINARY_OPER)
            if (c == opr.logic)
                return opr;
        return null;
    }

    private static Operator getUnaryOpr(char c) {
        for (Operator opr : Operator.UNARY_OPER)
            if (c == opr.logic)
                return opr;
        return null;
    }

}
