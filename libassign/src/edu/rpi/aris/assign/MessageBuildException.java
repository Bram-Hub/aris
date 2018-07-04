package edu.rpi.aris.assign;

public class MessageBuildException extends Exception {

    public MessageBuildException(String msg) {
        super(msg);
    }

    public MessageBuildException(Throwable e) {
        super(e);
    }

    public MessageBuildException(String msg, Throwable e) {
        super(msg, e);
    }

}
