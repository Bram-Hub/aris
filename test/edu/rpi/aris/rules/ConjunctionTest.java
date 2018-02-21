package edu.rpi.aris.rules;

import edu.rpi.aris.TestUtil;
import org.junit.Test;

import java.text.ParseException;

public class ConjunctionTest {

    private String[][] premise = new String[][]{{"B", "A"}, {"(A ∧ B)", "C"}, {"A → (B ∧ C)", "A"}, {"A ∧ B", "A"}, {"A → B", "A"}};
    private String[] conc = new String[]{"A", "B", "C", "A ∧ B", "B ∧ C", "(A ∧ B) ∧ C", "(A → (B ∧ C)) ∧ A"};
    private int[][] valid = new int[][]{{0, 3}, {1, 5}, {2, 6}};

    @Test
    public void test() throws ParseException {
        TestUtil.validateClaims(premise, conc, valid, RuleList.CONJUNCTION);
    }

}