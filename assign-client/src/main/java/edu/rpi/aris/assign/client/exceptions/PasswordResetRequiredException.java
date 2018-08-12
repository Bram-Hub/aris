package edu.rpi.aris.assign.client.exceptions;

public class PasswordResetRequiredException extends RuntimeException {

    public PasswordResetRequiredException() {
        super("Your password has expired and must be reset");
    }

}
