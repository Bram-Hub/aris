package edu.rpi.aris.assign.client.exceptions;

import edu.rpi.aris.assign.client.AssignClient;
import edu.rpi.aris.assign.client.ResponseHandler;
import edu.rpi.aris.assign.message.Message;

public class InvalidCredentialsException extends ArisCommunicationException {

    public InvalidCredentialsException() {
        super("Invalid Credentials");
    }

    @Override
    public <T extends Message> void handleError(ResponseHandler<T> handler, T message) {
        AssignClient.getInstance().getMainWindow().displayErrorMsg("Invalid Credentials", "Your username or password was incorrect", true);
        handler.onError(true, message);
    }
}
