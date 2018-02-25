package edu.rpi.aris.proof;

import java.util.Arrays;

public enum Operator {

    NOT("!", '¬', true, false, false),
    AND("&", '∧', false, true, false),
    OR("|", '∨', false, true, false),
    CONDITIONAL("→", '→', false, false, false),
    BICONDITIONAL("↔", '↔', false, false, false),
    EQUALS("=", '=', false, false, false),
    NOT_EQUALS("≠", '≠', false, false, false),
    EXISTENTIAL("∃", '∃', true, false, true),
    UNIVERSAL("∀", '∀', true, false, true);

    public static final Operator[] BINARY_OPERATOR = Arrays.stream(Operator.values()).filter(opr -> !opr.isUnary).toArray(Operator[]::new);
    public static final Operator[] UNARY_OPERATOR = Arrays.stream(Operator.values()).filter(opr -> opr.isUnary).toArray(Operator[]::new);
    public static final Operator[] QUANTIFIER_OPERATOR = Arrays.stream(Operator.values()).filter(opr -> opr.isQuantifier).toArray(Operator[]::new);

    public final String rep;
    public final char logic;
    public final boolean isUnary, canGeneralize, isQuantifier;

    Operator(String rep, char logic, boolean isUnary, boolean canGeneralize, boolean isQuantifier) {
        this.rep = rep;
        this.logic = logic;
        this.isUnary = isUnary;
        this.canGeneralize = canGeneralize;
        this.isQuantifier = isQuantifier;
    }

    public static Operator getOperator(String opr) {
        for (Operator o : Operator.values())
            if (o.isQuantifier) {
                if (opr.startsWith(String.valueOf(o.rep)))
                    return o;
            } else if (o.rep.equals(opr))
                return o;
        return null;
    }

}
