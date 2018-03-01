package edu.rpi.aris.proof;


import javafx.util.Pair;
import org.apache.commons.lang.StringUtils;

import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SentenceUtil {

    public static final Pattern VARIABLE_PATTERN = Pattern.compile("[t-z][A-Za-z0-9]*");
    public static final Pattern CONSTANT_PATTERN = Pattern.compile("[a-s][A-Za-z0-9]*|[0-9]+");
    public static final Pattern LITERAL_PATTERN = Pattern.compile("[A-Z][A-Za-z0-9]*|⊥");
    public static final Pattern FUNCTION_PATTERN = Pattern.compile("[A-Z][A-Za-z0-9]*");
    public static final char OP = '(';
    public static final char CP = ')';
    private static final Pattern QUANTIFIER_PATTERN;

    static {
        StringBuilder quantifierPattern = new StringBuilder();
        for (Operator o : Operator.OPERATOR_TYPES.get(Operator.Type.QUANTIFIER)) {
            quantifierPattern.append(o.logic);
        }
        String q = quantifierPattern.toString();
        quantifierPattern.insert(0, "[");
        quantifierPattern.append("] *").append(VARIABLE_PATTERN.pattern()).append("(?=[ (").append(q).append("])");
        QUANTIFIER_PATTERN = Pattern.compile(quantifierPattern.toString());
    }

    private static int checkParen(String expr) {
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

    private static String removeWhitespace(String expr) {
        return expr.replaceAll("\\s", "");
    }

    public static String removeParen(String expr) throws ExpressionParseException {
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
                    throw new ExpressionParseException("Unbalanced parentheses in expression", -1, 0);
            }
        }
        return expr;
    }

    public static Expression toExpression(String expr) throws ParseException {
        return new Expression(toPolishNotation(expr));
    }

    public static String toPolishNotation(String expr) throws ExpressionParseException {
        int parenLoc;
        if ((parenLoc = checkParen(expr)) != -1)
            throw new ExpressionParseException("Unbalanced parentheses in expression", parenLoc, 1);
        String noParen = removeParen(removeWhitespace(expr));
        try {
            return toPolish(noParen, findQuantifiers(expr));
        } catch (ExpressionParseException e) {
            int offset = e.getErrorOffset();
            int length = e.getErrorLength();
            if (offset == -1)
                throw e;
            int j = 0;
            for (int i = 0; i < e.getErrorOffset() + e.getErrorLength() && j < expr.length() && i < noParen.length(); ++i) {
                while (noParen.charAt(i) != expr.charAt(j)) {
                    if (i < e.getErrorOffset())
                        ++offset;
                    ++length;
                    ++j;
                }
                ++j;
            }
            throw new ExpressionParseException(e.getMessage(), offset, length);
        }
    }

    private static LinkedList<String> findQuantifiers(String expr) {
        LinkedList<String> quantifiers = new LinkedList<>();
        Matcher m = QUANTIFIER_PATTERN.matcher(expr);
        while (m.find())
            quantifiers.add(m.group().replace(" ", ""));
        return quantifiers;
    }

    /**
     * This function takes an expression in polish notation and converts it to a minimal expression in standard notation.
     * It also creates a mapping from the index of all relevant characters in the original polish notation to the index
     * of the same character in the generated standard notation.
     * <p>
     * This function is designed to work with a polish expression that is valid and minimal as generated by the function
     * {@link SentenceUtil#toPolishNotation(String)}
     *
     * @param polish the polish notation to map and convert to standard notation
     * @return a Pair containing the converted standard notation and the mapping or null if the conversion failed
     * @see SentenceUtil#toPolishNotation(String)
     */
    private static Pair<String, HashMap<Integer, Integer>> fromPolishNotation(String polish) {
        try {
            // a map for holding a character index map from the original polish notation to the standard notation generated
            //  by this function
            HashMap<Integer, Integer> parseMap = new HashMap<>();
            // calculate the offset from removing the parentheses
            int parenOffset = polish.length();
            polish = removeParen(polish);
            parenOffset = (parenOffset - polish.length()) / 2;

            // split the polish notation using a parentheses depth aware split
            ArrayList<String> split = depthAwareSplit(polish);

            // get the operator for the expression
            String oprStr = split.get(0);
            Operator opr = Operator.getOperator(oprStr);

            // an array of pairs to hold all of the converted polish sub expressions
            @SuppressWarnings("unchecked") Pair<String, HashMap<Integer, Integer>>[] convert = new Pair[split.size() - 1];
            // calculate the initial mapping offset for the first sub expression
            int po = parenOffset + split.get(0).length() + 1;
            // an array to keep track of the individual sub expression mapping offsets
            int[] polishOffsets = new int[split.size() - 1];
            // convert each sub expression from polish notation and store their mapping offsets with respect to the main expression
            for (int i = 1; i < split.size(); ++i) {
                // set the offset for the sub expression mapping
                polishOffsets[i - 1] = po;
                // convert the sub expression from polish notation
                convert[i - 1] = fromPolishNotation(split.get(i));
                // special case for polish expressions with the equivalence operator
                if ((opr != null && opr.isType(Operator.Type.EQUIVALENCE)) || Operator.containsType(Operator.Type.EQUIVALENCE, convert[i - 1].getKey())) {
                    //remove parentheses from the converted expression and shift the mapping accordingly
                    String str = convert[i - 1].getKey();
                    HashMap<Integer, Integer> map = convert[i - 1].getValue();
                    int pOffset = str.length();
                    str = removeParen(str);
                    if (str.length() != pOffset) {
                        pOffset = (pOffset - str.length()) / 2;
                        for (int j = 0; j < split.get(i).length(); ++j) {
                            Integer o = map.get(j);
                            if (o == null)
                                continue;
                            map.put(j, o - pOffset);
                        }
                    }
                    convert[i - 1] = new Pair<>(str, map);
                }
                // add the length of this polish sub expression to the polish offset
                po += split.get(i).length() + 1;
                // if there was an error with the sub expression conversion then we'll return null
                if (convert[i - 1] == null)
                    return null;
            }
            if (opr == null) {
                // if the operator is null then we either have a function or a literal
                if (convert.length > 0) {
                    // we have a function so lets do a direct mapping of the function name
                    for (int i = 0; i < oprStr.length(); ++i)
                        parseMap.put(i + parenOffset, i);
                    // Create a string builder to store our standardized expression string in
                    //  initialize it with the function name and an opening parentheses
                    StringBuilder str = new StringBuilder(oprStr + OP);
                    // Set the initial standard notation offset to be the initial length of the string builder
                    int offset = str.length();
                    // for each sub expression of this function
                    for (int j = 0; j < convert.length; ++j) {
                        Pair<String, HashMap<Integer, Integer>> p = convert[j];
                        // shift the mapping from the sub expression to this expression's mapping
                        for (int i = 0; i < p.getKey().length(); ++i) {
                            Integer o = p.getValue().get(i);
                            if (o != null) {
                                parseMap.put(i + polishOffsets[j], o + offset);
                            }
                        }
                        // increase the standard notation offset by the length of the last sub expression
                        offset += p.getKey().length();
                        // add the sub expression to our string builder
                        str.append(p.getKey());
                        // if this is not the last sub expression add a comma and increment the standard notation offset
                        if (convert[convert.length - 1] != p) {
                            str.append(",");
                            ++offset;
                        }
                    }
                    // add the closing parentheses to the end of our function
                    str.append(CP);
                    return new Pair<>(str.toString(), parseMap);
                } else {
                    // if we didn't convert anything then we have a literal so we can perform a direct mapping from
                    //  polish to standard notation with the parentheses offset
                    for (int i = 0; i < polish.length(); ++i)
                        parseMap.put(i + parenOffset, i);
                    return new Pair<>(polish, parseMap);
                }
            } else {
                if (opr.isType(Operator.Type.UNARY)) {
                    // We need to handle unary operators differently

                    // Create a string builder containing the operator string
                    StringBuilder sb = new StringBuilder(opr.isType(Operator.Type.QUANTIFIER) ? oprStr + " " : String.valueOf(opr.logic));
                    // Set the standard notation offset to be the operator string's length
                    int offset = sb.length();
                    // directly map the operator string from polish to standard notation
                    for (int i = 0; i < offset; ++i)
                        parseMap.put(i + parenOffset, i);
                    // shift the sub expression's map to this expression
                    for (int i = 0; i < split.get(1).length(); ++i) {
                        Integer o = convert[0].getValue().get(i);
                        if (o != null) {
                            parseMap.put(i + polishOffsets[0], o + offset);
                        }
                    }
                    // add out sub expression to the string
                    sb.append(convert[0].getKey());
                    return new Pair<>(sb.toString(), parseMap);
                }
                // if we get here then we have a binary operator on our hands

                // keep track of whether or not we've already mapped the operator
                boolean oprMapped = false;
                // set the offset to 1 since we are adding parentheses
                int offset = 1;
                // string builder to hold our output string
                StringBuilder sb = new StringBuilder(String.valueOf(OP));
                // for each sub expression we need to shift the mappings
                for (int j = 0; j < convert.length; j++) {
                    // get the converted sub expression
                    Pair<String, HashMap<Integer, Integer>> p = convert[j];
                    // shift the sub expression's mapping to this expression's mapping
                    for (int i = 0; i < split.get(j + 1).length(); ++i) {
                        Integer o = p.getValue().get(i);
                        if (o != null) {
                            parseMap.put(i + polishOffsets[j], o + offset);
                        }
                    }
                    // add the sub expression's length the the standard notation offset
                    offset += p.getKey().length();
                    // add the sub expression to our output string
                    sb.append(p.getKey());
                    // if we are not at the last sub expression
                    if (convert[convert.length - 1] != p) {
                        // map the operator if it hasn't been mapped
                        if (!oprMapped) {
                            parseMap.put(parenOffset, offset);
                            oprMapped = true;
                        }
                        // increment the offset and add the operator to our output
                        ++offset;
                        sb.append(opr.logic);
                    }
                }
                // add closing parentheses
                sb.append(CP);
                return new Pair<>(sb.toString(), parseMap);
            }
        } catch (Throwable e) {
            // if anything goes wrong we don't want to crash so just print the stack trace and return null
            e.printStackTrace();
            return null;
        }
    }

    public static ArrayList<String> depthAwareSplit(String expression) {
        ArrayList<String> strExp = new ArrayList<>();
        char[] charExp = expression.toCharArray();
        int parenDepth = 0;
        int start = 0;
        for (int i = 0; i < charExp.length; ++i) {
            char c = charExp[i];
            if (c == OP)
                parenDepth++;
            else if (c == CP)
                parenDepth--;
            else if (c == ' ' && parenDepth == 0) {
                strExp.add(expression.substring(start, i));
                start = i + 1;
            }
        }
        if (start < expression.length())
            strExp.add(expression.substring(start));
        strExp.add(expression.substring(start));
        return strExp;
    }

    private static HashMap<Integer, Integer> createParseMap(String polishNotation, String original) {
        try {
            Pair<String, HashMap<Integer, Integer>> converted = fromPolishNotation(polishNotation);
            if (converted == null)
                return null;
            String str = converted.getKey();
            HashMap<Integer, Integer> parseMap = converted.getValue();
            int parenOffset = str.length();
            str = removeParen(str);
            if (str.length() != parenOffset) {
                parenOffset = (parenOffset - str.length()) / 2;
                for (int i = 0; i < polishNotation.length(); ++i) {
                    Integer o = parseMap.get(i);
                    if (o == null)
                        continue;
                    parseMap.put(i, o - parenOffset);
                }
            }
            int offset = 0;
            HashMap<Integer, Integer> remap = new HashMap<>();
            for (int i = 0; i < str.length() && i + offset < original.length(); ++i) {
                while (str.charAt(i) != original.charAt(i + offset) && !(str.charAt(i) == ' ' && original.charAt(i + offset) == OP))
                    ++offset;
                remap.put(i, offset);
            }
            for (int i = 0; i < polishNotation.length(); ++i) {
                Integer o = parseMap.get(i);
                if (o == null)
                    continue;
                Integer r = remap.get(o);
                if (r == null || r == 0)
                    continue;
                parseMap.put(i, o + r);
            }
            return parseMap;
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void mapExceptionToStandardForm(String polish, String standard, ExpressionParseException e) throws ExpressionParseException {
        if (e == null)
            return;
        if (e.getErrorOffset() == -1 || e.getErrorLength() == 0)
            throw e;
        int start = e.getErrorOffset();
        int end = start + e.getErrorLength() - 1;
        HashMap<Integer, Integer> map = createParseMap(polish, standard);
        if (map == null)
            throw new ExpressionParseException(e.getMessage(), -1, 0);
        try {
            start = map.get(start);
            end = map.get(end);
        } catch (Throwable e1) {
            throw new ExpressionParseException(e.getMessage(), -1, 0);
        }
        int length = end - start + 1;
        if (length < 0)
            throw new ExpressionParseException(e.getMessage(), -1, 0);
        throw new ExpressionParseException(e.getMessage(), start, length);
    }

    private static String toPolish(String expr, LinkedList<String> quantifiers) throws ExpressionParseException {
        if (expr.length() == 0)
            throw new ExpressionParseException("Empty expression found in sentence", 0, 1);
        int matches = 0;
        StringBuilder regex = new StringBuilder("[");
        for (Operator o : Operator.OPERATOR_TYPES.get(Operator.Type.EQUIVALENCE)) {
            matches += StringUtils.countMatches(expr, String.valueOf(o.logic));
            regex.append(o.logic);
        }
        regex.append("]");
        if (matches == 1 && quantifiers.size() == 0) {
            Matcher m = Pattern.compile(regex.toString()).matcher(expr);
            Operator opr;
            if (!m.find() || (opr = Operator.getOperator(m.group())) == null)
                throw new ExpressionParseException("Failed to parse expression", -1, 0);
            String[] split = expr.split(String.valueOf(opr.logic));
            if (split.length != 2 || split[0].length() == 0 || split[1].length() == 0)
                throw new ExpressionParseException("Equivalence operator must join 2 expressions", expr.indexOf(opr.logic), 1);
            split[0] = removeParen(split[0]);
            split[1] = removeParen(split[1]);

            return "(" + opr.rep + " " + toPolish(split[0], findQuantifiers(split[0])) + " " + toPolish(split[1], findQuantifiers(split[1])) + ")";
        } else if (matches > 1) {
            Matcher m = Pattern.compile(regex.toString()).matcher(expr);
            int loc = -1;
            if (m.find() && m.find())
                loc = m.start();
            throw new ExpressionParseException("Only one equivalence relation allowed per expression", loc, loc == -1 ? 0 : 1);
        }
        int parenDepth = 0;
        Operator operator = null;
        ArrayList<String> expressions = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < expr.length(); ++i) {
            char c = expr.charAt(i);
            Operator tmpOpr;
            if (c == OP)
                parenDepth++;
            else if (c == CP)
                parenDepth--;
            else if (parenDepth == 0 && (tmpOpr = getBoolOpr(c)) != null) {
                if (operator == null)
                    operator = tmpOpr;
                if (tmpOpr == operator) {
                    if (start == i)
                        throw new ExpressionParseException("Binary operator needs to connect 2 expressions", i, 1);
                    expressions.add(expr.substring(start, i));
                    start = i + 1;
                } else
                    throw new ExpressionParseException("Invalid operator in generalized " + operator.name().toLowerCase(), i, 1);
            }
        }
        expressions.add(expr.substring(start));
        if (operator != null) {
            for (int i = 0; i < expressions.size(); ++i) {
                String exp = expressions.get(i);
                if (exp.length() == 0)
                    throw new ExpressionParseException("Binary connective missing expression", -1, 0);
                String noParen = removeParen(exp);
                int offset = (exp.length() - noParen.length()) / 2;
                try {
                    exp = toPolish(noParen, quantifiers);
                } catch (ExpressionParseException e) {
                    shiftParseException(e, offset);
                }
                expressions.set(i, exp);
            }
            return OP + operator.rep + " " + join(expressions) + CP;
        } else {
            String exp = expr;
            Operator opr;
            if ((opr = getUnaryOpr(exp.charAt(0))) != null) {
                if (opr.isType(Operator.Type.QUANTIFIER)) {
                    String quantifier = quantifiers.pollFirst();
                    if (quantifier == null)
                        throw new ExpressionParseException("Malformed quantifier in expression. Valid quantifier variables can start with the letters t-z", 0, 1);
                    exp = exp.substring(quantifier.length());
                    String noParen = removeParen(exp);
                    int offset = (exp.length() - noParen.length()) / 2;
                    try {
                        exp = toPolish(noParen, quantifiers);
                    } catch (ExpressionParseException e) {
                        shiftParseException(e, offset);
                    }
                    exp = OP + quantifier + " " + exp + CP;
                } else {
                    exp = exp.substring(1);
                    String noParen = removeParen(exp);
                    int offset = (exp.length() - noParen.length()) / 2;
                    try {
                        exp = toPolish(noParen, quantifiers);
                    } catch (ExpressionParseException e) {
                        shiftParseException(e, offset);
                    }
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
                            throw new ExpressionParseException("Functions can only contain comma separated literals", argStart, i - argStart + 1);
                        fun = exp.substring(0, i);
                        argStart = i + 1;
                    } else if (c == CP) {
                        if (argStart == -1)
                            throw new ExpressionParseException("No matching open parentheses for closing parentheses", i, 1);
                        if (exp.substring(i).length() > 1)
                            throw new ExpressionParseException("Invalid function definition", i + 1, exp.substring(i + 1).length());
                        if (exp.substring(argStart, i).startsWith(","))
                            throw new ExpressionParseException("Missing first function parameter", argStart, 1);
                        if (exp.substring(argStart, i).endsWith(","))
                            throw new ExpressionParseException("Missing last function parameter", i - 1, 1);
                        args = exp.substring(argStart, i).split(",");
                    }
                }
                if (argStart != -1) {
                    if (args == null)
                        throw new ExpressionParseException("Failed to parse function", -1, 0);
                    exp = OP + fun + " " + join(args) + CP;
                }
            }
            return exp;
        }
    }

    public static String toPolish(Expression[] expressions, String opr) {
        Objects.requireNonNull(expressions);
        Objects.requireNonNull(opr);
        ArrayList<String> polish = Arrays.stream(expressions).map(Expression::toString).collect(Collectors.toCollection(ArrayList::new));
        return OP + opr + " " + join(polish) + CP;
    }

    private static String join(ArrayList<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); ++i) {
            sb.append(list.get(i));
            if (i < list.size() - 1)
                sb.append(" ");
        }
        return sb.toString();
    }

    private static String join(String[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; ++i) {
            sb.append(arr[i]);
            if (i < arr.length - 1)
                sb.append(" ");
        }
        return sb.toString();
    }

    private static Operator getBoolOpr(char c) {
        for (Operator opr : Operator.OPERATOR_TYPES.get(Operator.Type.BINARY))
            if (c == opr.logic)
                return opr;
        return null;
    }

    private static Operator getUnaryOpr(char c) {
        for (Operator opr : Operator.OPERATOR_TYPES.get(Operator.Type.UNARY))
            if (c == opr.logic)
                return opr;
        return null;
    }

    private static void shiftParseException(ExpressionParseException e, int offset) throws ExpressionParseException {
        throw new ExpressionParseException(e.getMessage(), e.getErrorOffset() == -1 ? -1 : e.getErrorOffset() + offset, e.getErrorOffset() == -1 ? 0 : e.getErrorLength() + offset);
    }

}
