package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Operator;
import edu.rpi.aris.proof.Proof;
import edu.rpi.aris.proof.SentenceUtil;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;

public class Lemma {

    private final Proof proof;
    private ArrayList<Expression> premises = new ArrayList<>();
    private Expression goal;
    private boolean valid;

    public Lemma(Proof proof) {
        this.proof = proof;
        valid = proof.getNumGoals() == 1;// && proof.verifyProof().get(0) == Proof.Status.CORRECT;
        if (valid) {
            goal = normalizeVariables(proof.getGoal(0).getExpression());
            for (int i = 0; i < proof.getNumPremises(); ++i)
                premises.add(normalizeVariables(proof.getLine(i).getExpression()));
        }
    }

    public static void main(String[] args) throws ParseException {
        Proof proof = new Proof("");
        proof.addLine(0, true, 0).setExpressionString("((A∨B∨C)∧(D∨E∨F))→(A∨B∨C)", true);
        proof.addLine(0, true, 0).setExpressionString("(D∨E∨F)", true);
        proof.addGoal(0).setGoalString("(A∨B∨C)", true);
        Lemma lemma = new Lemma(proof);
        ArrayList<Expression> expressions = new ArrayList<>();
        expressions.add(SentenceUtil.toExpression("B"));
        expressions.add(SentenceUtil.toExpression("(A∧B)→A"));
        Expression goal = SentenceUtil.toExpression("A");
        System.out.println(lemma.isValid(expressions, goal));
    }

    public boolean isValid(ArrayList<Expression> premises, Expression goal) {
        if (!valid || goal == null || premises.contains(null) || premises.size() != this.premises.size())
            return false;
        ArrayList<Pair<Expression, Expression>> goalPairs = structuralExpressionMatch(this.goal, goal);
        if (!checkPairs(goalPairs))
            return false;
        if (premises.size() == 0)
            return true;
        //noinspection unchecked
        ArrayList<Pair<Expression, Expression>>[] pairs = new ArrayList[premises.size()];
        HashMap<Expression, HashMap<Expression, ArrayList<Pair<Expression, Expression>>>> possibleMatches = new HashMap<>();
        for (Expression p1 : this.premises) {
            HashMap<Expression, ArrayList<Pair<Expression, Expression>>> matches = possibleMatches.computeIfAbsent(p1, p -> new HashMap<>());
            for (Expression p2 : premises) {
                ArrayList<Pair<Expression, Expression>> p = structuralExpressionMatch(p1, p2);
                if (checkPairs(goalPairs, p))
                    matches.put(p2, p);
            }
        }

        return true;
    }

    @SafeVarargs
    private final boolean checkPairs(ArrayList<Pair<Expression, Expression>>... pairs) {
        HashMap<Expression, Expression> map = new HashMap<>();
        for (ArrayList<Pair<Expression, Expression>> l : pairs) {
            for (Pair<Expression, Expression> p : l) {
                Expression oldVal = map.put(p.getKey(), p.getValue());
                if (oldVal != null && !oldVal.equals(p.getValue()))
                    return false;
            }
        }
        return true;
    }

    private Expression normalizeVariables(Expression exp) {
        return normalizeVariables(exp, new ArrayList<>());
    }

    private Expression normalizeVariables(Expression exp, ArrayList<String> vars) {
//        if (exp.getOperator().isType(Operator.Type.QUANTIFIER))
//            vars.add(exp.getQuantifierVariable());
        //TODO
        return exp;
    }

    private ArrayList<Pair<Expression, Expression>> structuralExpressionMatch(Expression e1, Expression e2) {
        ArrayList<Pair<Expression, Expression>> matches = new ArrayList<>();
        structuralExpressionMatch(e1, e2, matches);
        return matches;
    }

    private void structuralExpressionMatch(Expression e1, Expression e2, ArrayList<Pair<Expression, Expression>> matches) {
        if (e1.isLiteral() || e2.isLiteral()) {
            matches.add(new ImmutablePair<>(e1, e2));
            return;
        }
        if (e1.isFunctional() && e2.isFunctional()) {
            if (e1.getFunctionOperator().equals(e2.getFunctionOperator()) && e1.getNumExpressions() == e2.getNumExpressions()) {
                for (int i = 0; i < e1.getNumExpressions(); ++i)
                    structuralExpressionMatch(e1.getExpressions()[i], e2.getExpressions()[i], matches);
            } else
                matches.add(new ImmutablePair<>(e1, e2));
            return;
        }
        if (e1.isFunctional() || e2.isFunctional() || e1.getOperator() != e2.getOperator() || e1.getNumExpressions() != e2.getNumExpressions()) {
            matches.add(new ImmutablePair<>(e1, e2));
            return;
        }
        if (e1.getOperator().isType(Operator.Type.QUANTIFIER) && !e1.getQuantifierVariable().equals(e2.getQuantifierVariable())) {
            matches.add(new ImmutablePair<>(e1, e2));
            return;
        }
        for (int i = 0; i < e1.getNumExpressions(); ++i)
            structuralExpressionMatch(e1.getExpressions()[i], e2.getExpressions()[i], matches);
    }

}
