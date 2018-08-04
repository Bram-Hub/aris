package edu.rpi.aris.assign.client.exceptions;

public class InvalidAccessTokenException extends Exception {

    public InvalidAccessTokenException() {
        super("Access Token Expired");
    }

}
