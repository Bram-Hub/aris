package edu.rpi.aris.assign.client;

import edu.rpi.aris.assign.message.Message;

public interface ResponseHandler<T extends Message> {

    void response(T message);

    void onError();

}
