package edu.rpi.aris.proof;

public enum Operator {

    NOT("!", '¬', true, false),
    AND("&", '∧', false, true),
    OR("|", '∨', false, true),
    CONDITIONAL("->", '→', false, false),
    BICONDITIONAL("<->", '↔', false, false);

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
    public final boolean isUnary, canGeneralize;

    Operator(String rep, char logic, boolean isUnary, boolean canGeneralize) {
        this.rep = rep;
        this.logic = logic;
        this.isUnary = isUnary;
        this.canGeneralize = canGeneralize;
    }

    public static Operator getOperator(String opr) {
        for (Operator o : Operator.values())
            if (o.rep.equals(opr))
                return o;
        return null;
    }

}
