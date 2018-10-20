package edu.rpi.aris.assign.client.exceptions;

import edu.rpi.aris.assign.client.ResponseHandler;
import edu.rpi.aris.assign.message.Message;

public abstract class ArisCommunicationException extends RuntimeException {

    public ArisCommunicationException(String reason) {
        super(reason);
    }

    public abstract <T extends Message> void handleError(ResponseHandler<T> handler, T message);

}
