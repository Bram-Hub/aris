package edu.rpi.aris.assign.client.exceptions;

public class InvalidAccessTokenException extends RuntimeException {

    public InvalidAccessTokenException() {
        super("Access Token Expired");
    }

}
