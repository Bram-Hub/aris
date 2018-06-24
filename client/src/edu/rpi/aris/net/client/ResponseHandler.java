package edu.rpi.aris.net.client;

import edu.rpi.aris.net.message.Message;

public interface ResponseHandler<T extends Message> {

    void response(T message);

    void onError();

}
