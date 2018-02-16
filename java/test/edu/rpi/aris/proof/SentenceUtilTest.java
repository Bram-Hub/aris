package edu.rpi.aris.proof;

import org.junit.Test;

import java.text.ParseException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SentenceUtilTest {

    private static final String[] logicExpr = {"A", "A ∧ B", "(A ∧ B)", "(((((A∧ B)))))", "¬¬¬(A ∨¬B)", "()"};
    private static final String[] polishExpr = {"A", "(& A B)", "(& A B)", "(& A B)", "(! (! (! (| A (! B)))))", null};

    @Test
    public void toPolishNotation() {
        for (int i = 0; i < logicExpr.length; ++i) {
            try {
                assertEquals(polishExpr[i], SentenceUtil.toPolishNotation(logicExpr[i]));
            } catch (ParseException e) {
                try {
                    assertNull(polishExpr[i]);
                } catch (AssertionError e1) {
                    e.printStackTrace();
                    throw e1;
                }
            }
        }
    }

    public static Premise[] getPremises(String[] prems) throws ParseException {
        Premise[] p = new Premise[prems.length];
        for (int i = 0; i < prems.length; i++)
            p[i] = new Premise(new Expression(SentenceUtil.toPolishNotation(prems[i])));
        return p;
    }

    public static Expression[] getExpressions(String[] exps) throws ParseException {
        Expression[] e = new Expression[exps.length];
        for (int i = 0; i < exps.length; ++i)
            e[i] = new Expression(SentenceUtil.toPolishNotation(exps[i]));
        return e;
    }

}