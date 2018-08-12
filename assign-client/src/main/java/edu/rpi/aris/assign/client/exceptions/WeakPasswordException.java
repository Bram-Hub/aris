package edu.rpi.aris.assign.client.exceptions;

public class WeakPasswordException extends RuntimeException {

    public WeakPasswordException() {
        super("The given password does not meet the minimum password requirements");
    }

}
