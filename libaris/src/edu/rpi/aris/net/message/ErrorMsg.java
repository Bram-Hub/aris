package edu.rpi.aris.net.message;

import com.google.gson.JsonObject;
import edu.rpi.aris.net.MessageParseException;
import edu.rpi.aris.net.User;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.sql.Connection;

public class ErrorMsg extends Message {

    private static final String ERR_TYPE_KEY = "err_type";
    private static final String ERR_MSG_KEY = "err_msg";

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
        errorType = ErrorType.valueOf(Message.getString(jsonMsg, ERR_TYPE_KEY, ErrorType.UNKNOWN_ERROR.name(), false));
        errorMsg = Message.getString(jsonMsg, ERR_MSG_KEY, null, false);
    }

    @Override
    protected void parseResponse(JsonObject jsonMsg) throws MessageParseException {
        parseMessage(jsonMsg);
    }

    @Override
    public ErrorType processMessage(Connection connection, User user) {
        return errorType;
    }

    @Override
    public Pair<JsonObject, byte[]> buildMessage() {
        JsonObject obj = new JsonObject();
        obj.addProperty(ERR_TYPE_KEY, errorType.name());
        if (errorMsg != null)
            obj.addProperty(ERR_MSG_KEY, errorMsg);
        return new ImmutablePair<>(obj, null);
    }

    @Override
    public Pair<JsonObject, byte[]> buildReplyMessage() {
        return buildMessage();
    }

}
