package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.ServerPermissions;
import edu.rpi.aris.assign.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;

public class ErrorMsg extends Message {

    private final ErrorType errorType;
    private final String errorMsg;

    public ErrorMsg(String msg) {
        this(null, msg);
    }

    // DO NOT REMOVE!! Default constructor is required for gson deserialization
    private ErrorMsg() {
        super(null, true);
        errorMsg = null;
        errorType = null;
    }

    public ErrorMsg(ErrorType error) {
        this(error, null);
    }

    public ErrorMsg(ErrorType error, String msg) {
        super(null, true);
        errorType = error == null ? ErrorType.UNKNOWN_ERROR : error;
        errorMsg = msg;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    @Nullable
    @Override
    public ErrorType processMessage(@NotNull Connection connection, @NotNull User user, @NotNull ServerPermissions permissions) {
        return errorType;
    }

    @NotNull
    @Override
    public MessageType getMessageType() {
        return MessageType.ERROR;
    }

    @Override
    public boolean checkValid() {
        return true;
    }

    @Override
    public String toString() {
        return "Error Message:\n" +
                "\tError Type: " + (errorType == null ? null : errorType.name()) + "\n" +
                "\tError Message: " + errorMsg;
    }
}
