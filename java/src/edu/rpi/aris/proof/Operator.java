package edu.rpi.aris.proof;

public enum Operator {

    NOT("!", '¬', true),
    AND("&", '∧', false),
    OR("|", '∨', false),
    CONDITIONAL("->", '→', false),
    BICONDITIONAL("<->", '↔', false);

    public static final Operator[] BINARY_OPER, UNARY_OPER;
    public static final int NUM_UNARY = 1;

    static {
        Operator[] operators = Operator.values();
        BINARY_OPER = new Operator[operators.length - NUM_UNARY];
        UNARY_OPER = new Operator[operators.length - BINARY_OPER.length];
        int j = 0;
        int k = 0;
        for (int i = 0; i < operators.length; ++i)
            if (operators[i].isUnary)
                UNARY_OPER[j++] = operators[i];
            else
                BINARY_OPER[k++] = operators[i];

    }

    public final String rep;
    public final char logic;
    public final boolean isUnary;

    Operator(String rep, char logic, boolean isUnary) {
        this.rep = rep;
        this.logic = logic;
        this.isUnary = isUnary;
    }

}
