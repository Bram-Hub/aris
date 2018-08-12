package edu.rpi.aris.assign.client.exceptions;

public class AuthBanException extends RuntimeException {

    public AuthBanException() {
        super("Authentication failed. Your ip address has been temporarily banned");
    }

}
