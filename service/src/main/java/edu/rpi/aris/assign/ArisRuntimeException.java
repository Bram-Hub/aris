package edu.rpi.aris.assign;

public class ArisRuntimeException extends RuntimeException {

    public ArisRuntimeException() {
        super();
    }

    public ArisRuntimeException(String message) {
        super(message);
    }

    public ArisRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public ArisRuntimeException(Throwable cause) {
        super(cause);
    }

}
