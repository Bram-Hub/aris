package edu.rpi.aris.assign;

public class ArisModuleException extends Exception {

    public ArisModuleException() {
        super();
    }

    public ArisModuleException(String message) {
        super(message);
    }

    public ArisModuleException(String message, Throwable cause) {
        super(message, cause);
    }

    public ArisModuleException(Throwable cause) {
        super(cause);
    }

}
