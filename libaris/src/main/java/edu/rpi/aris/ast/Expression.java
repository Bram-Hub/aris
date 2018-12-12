package edu.rpi.aris.ast;

import java.util.*;

public class Expression {
    public static class BottomExpression extends Expression {
        @Override public String toString() {
            return "_|_";
        }
    }

    public static class PredicateExpression extends Expression {
        public String name;
        public List<String> args;
        PredicateExpression() { name = null; args = new ArrayList(); }
        @Override public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(name);
            for(int i=0; i<args.size(); i++) {
                if(i == 0) { sb.append('('); } else { sb.append(", "); }
                sb.append(args.get(i));
                if(i+1 == args.size()) { sb.append(')'); }
            }
            return sb.toString();
        }
    }

    public static class UnaryExpression extends Expression {
        Expression operand;
        UnaryExpression() { operand = null; }
    }
    public static class NotExpression extends Expression.UnaryExpression {
        @Override public String toString() { return "~" + operand.toString(); }
    }

    public static class BinaryExpression extends Expression {
        Expression l; Expression r;
        BinaryExpression() { l = null; r = null; }
    }
    public static class ImplicationExpression extends Expression.BinaryExpression {
        @Override public String toString() { return l.toString() + " -> " + r.toString(); }
    }
    public static class AddExpression extends Expression.BinaryExpression {
        @Override public String toString() { return l.toString() + " + " + r.toString(); }
    }
    public static class MultExpression extends Expression.BinaryExpression {
        @Override public String toString() { return l.toString() + " * " + r.toString(); }
    }

    public static abstract class AssociativeBinopExpression extends Expression {
        public ArrayList<Expression> exprs;
        protected abstract String canonicalRepr();
        AssociativeBinopExpression() { exprs = new ArrayList(); }
        public void addOperand(Expression e) { exprs.add(0, e); }
        @Override public String toString() {
            StringBuilder sb = new StringBuilder();
            for(int i=0; i<exprs.size(); i++) {
                if(i == 0) { sb.append('('); } else { sb.append(' '); sb.append(canonicalRepr()); sb.append(' '); }
                sb.append(exprs.get(i).toString());
                if(i+1 == exprs.size()) { sb.append(')'); }
            }
            return sb.toString();
        }
    }
    public static class AndExpression extends Expression.AssociativeBinopExpression {
        protected String canonicalRepr() { return "/\\"; }
    }
    public static class OrExpression extends Expression.AssociativeBinopExpression {
        protected String canonicalRepr() { return "\\/"; }
    }
    public static class BiconExpression extends Expression.AssociativeBinopExpression {
        protected String canonicalRepr() { return "<->"; }
    }

    public static abstract class QuantifierExpression extends Expression {
        String boundvar; Expression body;
        protected abstract String canonicalRepr();
        QuantifierExpression() { boundvar = null; body = null; }
        @Override public String toString() { return canonicalRepr() + boundvar + ", (" + (body != null ? body.toString() : "null") + ")"; }
    }
    public static class ForallExpression extends Expression.QuantifierExpression {
        protected String canonicalRepr() { return "forall "; }
    }
    public static class ExistsExpression extends Expression.QuantifierExpression {
        protected String canonicalRepr() { return "exists "; }
    }
}
