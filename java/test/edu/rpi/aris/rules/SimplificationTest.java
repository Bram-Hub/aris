package edu.rpi.aris.rules;

import edu.rpi.aris.proof.Claim;
import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Premise;
import edu.rpi.aris.proof.SentenceUtilTest;
import org.junit.Test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

public class SimplificationTest {

    private String[] premis = new String[]{"A ∧ B ∧ C ∧ D ∧ E", "A ∧ B", "(A ∨ B) ∧ C"};
    private String[] conc = new String[]{"A", "B", "A ∧ B", "B ∧ D ∧ A", "A ∨ B"};
    private int[][] valid = new int[][]{{0, 0}, {0, 1}, {0, 2}, {0, 3}, {1, 0}, {1, 1}, {1, 2}, {2, 4}};

    @Test
    public void test() throws ParseException {
        Premise[] premis = SentenceUtilTest.getPremises(this.premis);
        Expression[] conc = SentenceUtilTest.getExpressions(this.conc);
        HashMap<Integer, HashSet<Integer>> list = new HashMap<>();
        for (int[] arr : valid) {
            HashSet<Integer> set = list.computeIfAbsent(arr[0], k -> new HashSet<>());
            set.add(arr[1]);
        }
        for (int i = 0; i < premis.length; ++i) {
            for (int j = 0; j < conc.length; ++j) {
                Claim c = new Claim(conc[j], new Premise[]{premis[i]}, RuleList.SIMPLIFICATION.rule);
                if (list.containsKey(i) && list.get(i).contains(j)) {
                    String result = c.isValidClaim();
                    assertNull("Should be valid\n" + c + "\n", result);
                } else
                    assertNotNull("Should be invalid\n" + c, c.isValidClaim());
            }
        }
    }

}