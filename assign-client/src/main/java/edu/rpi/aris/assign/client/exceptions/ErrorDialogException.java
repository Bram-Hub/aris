package edu.rpi.aris.assign.client.exceptions;

import edu.rpi.aris.assign.client.AssignClient;
import edu.rpi.aris.assign.client.ResponseHandler;
import edu.rpi.aris.assign.message.Message;

public class ErrorDialogException extends ArisCommunicationException {

    private final String title;
    private final boolean wait;

    public ErrorDialogException(String title, String msg, boolean wait) {
        super(msg);
        this.title = title;
        this.wait = wait;
    }

    public boolean doWait() {
        return wait;
    }

    public String getTitle() {
        return title;
    }

    @Override
    public <T extends Message> void handleError(ResponseHandler<T> handler, T message) {
        AssignClient.getInstance().getMainWindow().displayErrorMsg(title, getMessage(), wait);
        handler.onError(false, message);
    }
}
