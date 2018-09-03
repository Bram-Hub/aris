package edu.rpi.aris.assign.client;

import edu.rpi.aris.assign.message.Message;

import java.util.concurrent.locks.ReentrantLock;

public interface ResponseHandler<T extends Message> {

    void response(T message);

    void onError(boolean suggestRetry, T msg);

    ReentrantLock getLock();

}
