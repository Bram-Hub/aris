package edu.rpi.aris.rules;

import edu.rpi.aris.TestUtil;
import org.junit.Test;

import java.text.ParseException;

public class SimplificationTest {

    private String[][] premis = new String[][]{{"A ∧ B ∧ C ∧ D ∧ E"}, {"A ∧ B"}, {"(A ∨ B) ∧ C"}};
    private String[] conc = new String[]{"A", "B", "A ∧ B", "B ∧ D ∧ A", "A ∨ B"};
    private int[][] valid = new int[][]{{0, 0}, {0, 1}, {0, 2}, {0, 3}, {1, 0}, {1, 1}, {1, 2}, {2, 4}};

    @Test
    public void test() throws ParseException {
        TestUtil.validateClaims(premis, conc, valid, RuleList.SIMPLIFICATION);
    }

}