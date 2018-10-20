package edu.rpi.aris.assign.client.exceptions;

import edu.rpi.aris.assign.client.ResponseHandler;
import edu.rpi.aris.assign.message.Message;

public class CancellationException extends ArisCommunicationException {

    public CancellationException() {
        super("Operation cancelled");
    }

    @Override
    public <T extends Message> void handleError(ResponseHandler<T> handler, T message) {
        handler.onError(false, message);
    }
}
