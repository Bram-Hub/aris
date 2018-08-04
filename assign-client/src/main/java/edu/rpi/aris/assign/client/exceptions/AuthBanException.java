package edu.rpi.aris.assign.client.exceptions;

public class AuthBanException extends Exception {

    public AuthBanException() {
        super("Authentication failed. Your ip address has been temporarily banned");
    }

}
