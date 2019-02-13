package edu.rpi.aris.ast;

import java.util.ArrayList;
import java.util.List;

public class Expression {
    static { edu.rpi.aris.util.SharedObjectLoader.loadLib("libaris_rs"); }

    public native String toDebugString();
    public native String toString();
    public static native Expression parseViaRust(String s);

    @Override public native boolean equals(Object e);

    public static class BottomExpression extends Expression {}

    public static class PredicateExpression extends Expression {
        public String name;
        public List<Expression> args;
        PredicateExpression() { name = null; args = new ArrayList(); }
    }

    public static abstract class UnaryExpression extends Expression {
        public Expression operand;
        UnaryExpression() { operand = null; }
    }
    public static class NotExpression extends Expression.UnaryExpression {}

    public static abstract class BinaryExpression extends Expression {
        public Expression l; public Expression r;
        BinaryExpression() { l = null; r = null; }
    }
    public static class ImplicationExpression extends Expression.BinaryExpression {}
    public static class AddExpression extends Expression.BinaryExpression {}
    public static class MultExpression extends Expression.BinaryExpression {}

    public static abstract class AssociativeBinopExpression extends Expression {
        public ArrayList<Expression> exprs;
        AssociativeBinopExpression() { exprs = new ArrayList(); }
        public void addOperand(Expression e) { exprs.add(0, e); }
    }
    public static class AndExpression extends Expression.AssociativeBinopExpression {}
    public static class OrExpression extends Expression.AssociativeBinopExpression {}
    public static class BiconExpression extends Expression.AssociativeBinopExpression {}

    public static abstract class QuantifierExpression extends Expression {
        public String boundvar; public Expression body;
        QuantifierExpression() { boundvar = null; body = null; }
    }
    public static class ForallExpression extends Expression.QuantifierExpression {}
    public static class ExistsExpression extends Expression.QuantifierExpression {}
}
