package edu.rpi.aris;

import edu.rpi.aris.proof.Claim;
import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Premise;
import edu.rpi.aris.proof.SentenceUtil;
import edu.rpi.aris.rules.RuleList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ArisNative {

    private static Logger logger = LogManager.getLogger(ArisNative.class);

    static {
        System.loadLibrary("aris");
    }

    private static native String process_sentence(String conclusion, String[] premises, String rule, String[] variables);

    public static void main(String[] args) {
        try {
            System.out.println(new Expression("(! A B)"));
            System.out.println(process_sentence("B", new String[]{"(<i> A B)", "A"}, "Modus Ponens", new String[]{}));
            Expression premise = new Expression(SentenceUtil.toPolishNotation("(((Home(max,test) ∨ Happy(carl)) ∧ (Home(claire) ∨ Happy(scruffy))))"));
            Premise[] p = new Premise[]{new Premise(premise)};
            System.out.println(premise);
            Expression conclusion = new Expression(SentenceUtil.toPolishNotation("(Home(max,test) ∨ Happy(carl))"));
            Claim claim = new Claim(conclusion, p, RuleList.SIMPLIFICATION.rule);
            System.out.println(claim.isValidClaim());
        } catch (Throwable e) {
            logger.catching(e);
        }
    }

}
