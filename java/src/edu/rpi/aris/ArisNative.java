package edu.rpi.aris;

import edu.rpi.aris.proof.SentenceUtil;

import java.text.ParseException;

public class ArisNative {

    static {
        System.loadLibrary("aris");
    }

    private static native String process_sentence(String conclusion, String[] premises, String rule, String[] variables);

    public static void main(String[] args) throws ParseException {
        System.out.println(process_sentence("B", new String[]{"(<i> A B)", "A"}, "Modus Ponens", new String[]{}));
        System.out.println(SentenceUtil.toPolishNotation("(Home(max) ∧ Happy(carl)) ∨ (Home(claire) ∧ Happy(scruffy))"));
    }

}
