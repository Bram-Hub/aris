package edu.rpi.aris.proof;

import org.apache.commons.lang.ArrayUtils;

import java.util.HashMap;
import java.util.HashSet;

public enum Operator {

    NOT("¬", new Type[]{Type.UNARY}),
    AND("∧", new Type[]{Type.BINARY, Type.GENERALIZABLE_LOGIC}),
    OR("∨", new Type[]{Type.BINARY, Type.GENERALIZABLE_LOGIC}),
    CONDITIONAL("→", new Type[]{Type.BINARY}),
    BICONDITIONAL("↔", new Type[]{Type.BINARY, Type.GENERALIZABLE_LOGIC}),
    EQUALS("=", new Type[]{Type.EQUIVALENCE}),
    NOT_EQUALS("≠", new Type[]{Type.EQUIVALENCE}),
    MULTIPLICATION("*", new Type[]{Type.BINARY, Type.MATH, Type.GENERALIZABLE_MATH}),
    ADDITION("+", new Type[]{Type.BINARY, Type.MATH, Type.GENERALIZABLE_MATH}),
    EXISTENTIAL("∃", new Type[]{Type.UNARY, Type.QUANTIFIER}),
    UNIVERSAL("∀", new Type[]{Type.UNARY, Type.QUANTIFIER}),
    ELEMENT_OF("∈", new Type[]{Type.BINARY, Type.SET}),
    SUBSET("⊆", new Type[]{Type.BINARY, Type.SET});

    public static final HashMap<Type, HashSet<Operator>> OPERATOR_TYPES;

    static {
        OPERATOR_TYPES = new HashMap<>();
        for (Operator o : Operator.values())
            for (Type t : o.types)
                OPERATOR_TYPES.computeIfAbsent(t, t1 -> new HashSet<>()).add(o);
    }

    public final String rep;
    public final Type[] types;

    Operator(String rep, Type[] types) {
        this.rep = rep;
        this.types = types;
    }

    public static Operator getOperator(String opr) {
        for (Operator o : Operator.values())
            if (o.isType(Type.QUANTIFIER)) {
                if (opr.startsWith(String.valueOf(o.rep)))
                    return o;
            } else if (o.rep.equals(opr))
                return o;
        return null;
    }

    public static boolean containsType(Type type, String str) {
        for (char c : str.toCharArray()) {
            for (Operator o : OPERATOR_TYPES.get(type))
                if (o.rep.length() == 1 && o.rep.charAt(0) == c)
                    return true;
        }
        return false;
    }

    public boolean isType(Type type) {
        return ArrayUtils.contains(this.types, type);
    }

    public boolean isVariableOperator() {
        for (Type t : types)
            if (t.isVariableType)
                return true;
        return false;
    }

    public enum Type {
        BINARY(false),
        GENERALIZABLE_LOGIC(false),
        GENERALIZABLE_MATH(true),
        QUANTIFIER(false),
        EQUIVALENCE(true),
        UNARY(false),
        SET(true),
        MATH(true);

        //Determines whether operators of this type are restricted to having constants and variables instead of literals
        public final boolean isVariableType;

        Type(boolean isVariableType) {
            this.isVariableType = isVariableType;
        }

    }

}
