package edu.rpi.aris.proof;

public enum Operator {

    NOT("!", '¬'),
    AND("&", '∧'),
    OR("|", '∨'),
    CONDITIONAL("->", '→'),
    BICONDITIONAL("<->", '↔');

    //    public static final char[] OPERATORS;
    public static final Operator[] BOOL_OPER, UNAIRY_OPER;

    static {
        Operator[] operators = Operator.values();
//        OPERATORS = new char[operators.length];
//        for (int i = 0; i < operators.length; ++i)
//            OPERATORS[i] = operators[i].logic;
        BOOL_OPER = new Operator[operators.length - 1];
        UNAIRY_OPER = new Operator[operators.length - BOOL_OPER.length];
        int j = 0;
        int k = 0;
        for (int i = 0; i < operators.length; ++i)
            if (operators[i] != NOT)
                BOOL_OPER[j++] = operators[i];
            else
                UNAIRY_OPER[k++] = operators[i];

    }

    public final String rep;
    public final char logic;

    Operator(String rep, char logic) {
        this.rep = rep;
        this.logic = logic;
    }

}
