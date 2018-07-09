package edu.rpi.aris.proof;

import org.junit.Test;

import java.text.ParseException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SentenceUtilTest {

    private static final String[] logicExpr = {"A", "A ∧ B", "(A ∧ B)", "(((((A∧ B)))))", "¬¬¬(A ∨¬B)", "()", "∀x∃y(F(x))", "∃x(A(x)) ∧ B"};
    private static final String[] polishExpr = {"A", "(∧ A B)", "(∧ A B)", "(∧ A B)", "(¬ (¬ (¬ (∨ A (¬ B)))))", null, "(∀x (∃y (F x)))", "(∧ (∃x (A x)) B)"};

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

}