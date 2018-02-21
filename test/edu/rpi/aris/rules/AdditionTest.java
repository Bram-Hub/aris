package edu.rpi.aris.rules;

import edu.rpi.aris.TestUtil;
import org.junit.Test;

import java.text.ParseException;

public class AdditionTest {

    private String[][] premise = new String[][]{{"A → B"}, {"(A ∧ B) → C"}, {"A → (B ∧ C)"}, {"A ∧ B"}};
    private String[] conc = new String[]{"A", "B", "(A → B) ∨ A ∨ (A → B) ∨ C", "((A ∧ B) → C) ∨ (A ∧ B) ∨ (A → B)", "(A ∧ B) ∨ (A → B)"};
    private int[][] valid = new int[][]{{0, 2}, {0, 3}, {0, 4}, {1, 3}, {3, 3}, {3, 4}};

    @Test
    public void test() throws ParseException {
        TestUtil.validateClaims(premise, conc, valid, RuleList.ADDITION);
    }

}