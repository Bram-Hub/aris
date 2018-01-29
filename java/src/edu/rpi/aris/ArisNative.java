package edu.rpi.aris;

public class ArisNative {

    static {
        System.loadLibrary("aris");
    }

    private static native String process_sentence(String conclusion, String[] premises, String rule, String[] variables);

    public static void main(String[] args) {
        System.out.println(process_sentence("A", new String[]{"(<a> A B)"}, "Simplification", new String[]{}));
    }

}
