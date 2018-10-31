package edu.rpi.aris.assign.client.exceptions;

import edu.rpi.aris.assign.client.AssignClient;
import edu.rpi.aris.assign.client.ResponseHandler;
import edu.rpi.aris.assign.message.Message;

public class WeakPasswordException extends ArisCommunicationException {

    public WeakPasswordException() {
        super("The given password does not meet the minimum password requirements");
    }

    @Override
    public <T extends Message> void handleError(ResponseHandler<T> handler, T message) {
        AssignClient.displayErrorMsg("Weak Password", "The password does not meet the complexity requirements", true);
        handler.onError(true, message);
    }
}
