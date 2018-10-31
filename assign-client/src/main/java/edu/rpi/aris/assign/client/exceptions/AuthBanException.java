package edu.rpi.aris.assign.client.exceptions;

import edu.rpi.aris.assign.client.AssignClient;
import edu.rpi.aris.assign.client.ResponseHandler;
import edu.rpi.aris.assign.message.Message;

public class AuthBanException extends ArisCommunicationException {

    public AuthBanException() {
        super("Authentication failed. Your ip address has been temporarily banned");
    }

    @Override
    public <T extends Message> void handleError(ResponseHandler<T> handler, T message) {
        AssignClient.displayErrorMsg("Temporary Ban", getMessage());
        handler.onError(false, message);
    }
}
