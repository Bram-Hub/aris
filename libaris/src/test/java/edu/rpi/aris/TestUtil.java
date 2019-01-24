package edu.rpi.aris;

import edu.rpi.aris.rules.RuleList;

import java.text.ParseException;

public class TestUtil {
//    public static Premise[][] getPremises(String[][] prems) throws ParseException {
//        Premise[][] p = new Premise[prems.length][prems[0].length];
//        for (int i = 0; i < prems.length; i++)
//            for (int j = 0; j < prems[0].length; j++) {
//                if (prems[i][j].contains(Claim.SUBPROOF_REP)) {
//                    String[] split = prems[i][j].split(Claim.SUBPROOF_REP);
//                    p[i][j] = new Premise(SentenceUtil.toExpression(split[0]), SentenceUtil.toExpression(split[1]));
//                } else
//                    p[i][j] = new Premise(SentenceUtil.toExpression(prems[i][j]));
//            }
//        return p;
//    }

//    public static Expression[] getExpressions(String[] exps) throws ParseException {
//        Expression[] e = new Expression[exps.length];
//        for (int i = 0; i < exps.length; ++i)
//            e[i] = new Expression(SentenceUtil.toPolishNotation(exps[i]));
//        return e;
//    }

//    public static HashMap<Integer, HashSet<Integer>> getValidMap(int[][] valid) {
//        HashMap<Integer, HashSet<Integer>> map = new HashMap<>();
//        for (int[] arr : valid) {
//            HashSet<Integer> set = map.computeIfAbsent(arr[0], k -> new HashSet<>());
//            set.add(arr[1]);
//        }
//        return map;
//    }

    public static void validateClaims(String[][] prems, String[] concs, int[][] valid, RuleList rule) throws ParseException {
//        Premise[][] premis = getPremises(prems);
//        Expression[] conc = getExpressions(concs);
//        HashMap<Integer, HashSet<Integer>> validMap = getValidMap(valid);
//        for (int i = 0; i < premis.length; ++i) {
//            for (int j = 0; j < conc.length; ++j) {
//                Claim c = new Claim(conc[j], premis[i], rule.rule);
//                if (validMap.containsKey(i) && validMap.get(i).contains(j)) {
//                    String result = c.isValidClaim();
//                    assertNull("Should be valid\n" + c + "\n", result);
//                } else
//                    assertNotNull("Should be invalid\n" + c, c.isValidClaim());
//            }
//        }
    }

}
