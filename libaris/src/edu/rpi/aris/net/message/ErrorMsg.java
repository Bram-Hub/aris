package edu.rpi.aris.net.message;

import com.google.gson.JsonObject;
import edu.rpi.aris.net.MessageParseException;
import edu.rpi.aris.net.User;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.sql.Connection;

public class ErrorMsg extends Message {

    private ErrorType errorType;
    private String errorMsg;

    public ErrorMsg() {
        this(null, null);
    }

    public ErrorMsg(String msg) {
        this(null, msg);
    }

    public ErrorMsg(ErrorType error) {
        this(error, null);
    }

    public ErrorMsg(ErrorType error, String msg) {
        super(MessageType.ERROR);
        errorType = error == null ? ErrorType.UNKNOWN_ERROR : error;
        errorMsg = msg;
    }

    @Override
    protected void parseMessage(JsonObject jsonMsg) throws MessageParseException {
        errorType = ErrorType.valueOf(getString(jsonMsg, ERR_TYPE, ErrorType.UNKNOWN_ERROR.name(), false));
        errorMsg = getString(jsonMsg, ERR_MSG, null, false);
    }

    @Override
    protected void parseReply(JsonObject jsonMsg) throws MessageParseException {
        parseMessage(jsonMsg);
    }

    @Override
    public ErrorType processMessage(Connection connection, User user) {
        return errorType;
    }

    @Override
    public Pair<JsonObject, byte[]> buildMessage() {
        JsonObject obj = new JsonObject();
        obj.addProperty(ERR_TYPE, errorType.name());
        if (errorMsg != null)
            obj.addProperty(ERR_MSG, errorMsg);
        return new ImmutablePair<>(obj, null);
    }

    @Override
    public Pair<JsonObject, byte[]> buildReplyMessage() {
        return buildMessage();
    }

}
