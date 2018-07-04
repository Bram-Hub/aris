package edu.rpi.aris.assign;

public class MessageParseException extends Exception {

    public MessageParseException() {
    }

    public MessageParseException(String message) {
        super(message);
    }

    public MessageParseException(Throwable cause) {
        super(cause);
    }

    public MessageParseException(String message, Throwable cause) {
        super(message, cause);
    }

}
