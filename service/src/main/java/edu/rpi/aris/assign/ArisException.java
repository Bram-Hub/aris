package edu.rpi.aris.assign;

public class ArisException extends Exception {

    public ArisException() {
        super();
    }

    public ArisException(String message) {
        super(message);
    }

    public ArisException(String message, Throwable cause) {
        super(message, cause);
    }

    public ArisException(Throwable cause) {
        super(cause);
    }

}
