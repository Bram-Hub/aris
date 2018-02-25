package edu.rpi.aris.proof;

import java.text.ParseException;

public class ExpressionParseException extends ParseException {

    private final int length;

    /**
     * Constructs an ExpressionParseException with the specified detail message and
     * offset.
     * A detail message is a String that describes this particular exception.
     *
     * @param s           the detail message
     * @param errorOffset the position where the error is found while parsing.
     * @param length      the length of the error
     */
    public ExpressionParseException(String s, int errorOffset, int length) {
        super(s, errorOffset);
        this.length = length;
    }

    public int getErrorLength() {
        return length;
    }

}
