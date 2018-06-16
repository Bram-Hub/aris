package edu.rpi.aris.net.message;

import edu.rpi.aris.net.User;

import java.sql.Connection;

public class ErrorMsg extends Message {

    private ErrorType errorType;
    private String errorMsg;

    public ErrorMsg(String msg) {
        this(null, msg);
    }

    public ErrorMsg(ErrorType error) {
        this(error, null);
    }

    public ErrorMsg(ErrorType error, String msg) {
        errorType = error == null ? ErrorType.UNKNOWN_ERROR : error;
        errorMsg = msg;
    }

    @Override
    public ErrorType processMessage(Connection connection, User user) {
        return errorType;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.ERROR;
    }

}
