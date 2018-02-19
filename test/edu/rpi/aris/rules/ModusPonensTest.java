package edu.rpi.aris.rules;

import edu.rpi.aris.TestUtil;
import org.junit.Test;

import java.text.ParseException;

public class ModusPonensTest {

    private String[][] premis = new String[][]{{"A → B", "A"}, {"(A ∧ B) → C", "A ∧ B"}, {"A → (B ∧ C)", "A"}, {"A ∧ B", "A"}, {"A → B", "B"}};
    private String[] conc = new String[]{"A", "B", "C", "A ∧ B", "B ∧ C"};
    private int[][] valid = new int[][]{{0, 1}, {1, 2}, {2, 4}};

    @Test
    public void test() throws ParseException {
        TestUtil.validateClaims(premis, conc, valid, RuleList.MODUS_PONENS);
    }

}