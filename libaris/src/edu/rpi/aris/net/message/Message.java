package edu.rpi.aris.net.message;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import edu.rpi.aris.net.MessageBuildException;
import edu.rpi.aris.net.MessageCommunication;
import edu.rpi.aris.net.MessageHandler;
import edu.rpi.aris.net.MessageParseException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.sql.SQLException;

public abstract class Message {

    public static final String MSG_TYPE_KEY = "msg_type";
    public static final String ERR_TYPE_KEY = "err_type";
    public static final String ERR_MSG_KEY = "err_msg";

    private static Logger logger = LogManager.getLogger(Message.class);
    private static JsonParser parser = new JsonParser();

    private MessageType type;

    Message() {
    }

    Message(MessageType type) {
        this.type = type;
    }

    public static Message parse(MessageCommunication com) {
        return parse(com, false);
    }

    public static Message parseReply(MessageCommunication com) {
        return parse(com, true);
    }

    public static Message parse(MessageCommunication com, boolean isReply) {
        MessageType msgType = null;
        try {
            try {
                String msgJson = com.readMessage();
                JsonObject msgObject = parser.parse(msgJson).getAsJsonObject();
                String msgTypeName = msgObject.get(MSG_TYPE_KEY).getAsString();
                try {
                    msgType = MessageType.valueOf(msgTypeName);
                } catch (IllegalArgumentException e) {
                    if (!isReply)
                        sendError(ErrorType.UNKNOWN_MSG_TYPE, "Invalid message type: " + msgTypeName, com);
                    throw new IOException("Invalid message type: " + msgTypeName, e);
                }
                try {
                    Message msg = msgType.msgClass.newInstance();
                    msg.setMessageType(msgType);
                    if (isReply)
                        msg.parseResponse(msgObject);
                    else
                        msg.parseMessage(msgObject);
                    return msg;
                } catch (NullPointerException | InstantiationException | IllegalAccessException e) {
                    throw new RuntimeException("Unable to instantiate new instance of " + msgType.msgClass + " for message type " + msgType.name(), e);
                }
            } catch (ClassCastException | IllegalStateException e) {
                logger.error("Failed to read msg_type key from message");
                if (!isReply)
                    sendError(ErrorType.UNKNOWN_MSG_TYPE, "Failed to read msg_type key from message", com);
            } catch (MessageParseException e) {
                logger.error("Malformed message received from peer", e);
                if (!isReply)
                    sendError(ErrorType.PARSE_ERR, "Invalid syntax for message type " + msgType.name(), com);
            } catch (RuntimeException e) {
                if (msgType != null)
                    logger.error("MessageType " + msgType.name() + " has not been fully implemented", e);
                if (!isReply)
                    sendError(ErrorType.NOT_IMPLEMENTED, "Function not implemented", com);
            }
        } catch (IOException e) {
            logger.error("Failed to parse message", e);
        }
        return null;
    }

    public static void sendError(ErrorType errorType, String msg, MessageCommunication com) throws IOException {
        JsonObject jsonObject = new JsonObject();
        jsonObject.add(MSG_TYPE_KEY, new JsonPrimitive(MessageType.ERROR.name()));
        jsonObject.add(ERR_TYPE_KEY, new JsonPrimitive(errorType.name()));
        if (msg != null)
            jsonObject.add(ERR_MSG_KEY, new JsonPrimitive(msg));
        com.sendMessage(jsonObject.toString());
        com.sendMessage(String.valueOf(0));
    }

    public final MessageType getMessageType() {
        return type;
    }

    public final void setMessageType(MessageType type) {
        this.type = type;
    }

    public void sendMessage(MessageCommunication com) throws IOException, MessageBuildException {
        Pair<JsonObject, byte[]> msg = buildMessage();
        msg.getLeft().add(MSG_TYPE_KEY, new JsonPrimitive(type.name()));
        com.sendMessage(msg.getLeft().toString());
        if (msg.getRight() != null) {
            com.sendMessage(String.valueOf(msg.getRight().length));
            com.sendData(msg.getRight());
        } else
            com.sendMessage(String.valueOf(0));
    }

    public void replyMessage(MessageCommunication com) throws IOException, MessageBuildException {
        Pair<JsonObject, byte[]> msg = buildReplyMessage();
        msg.getLeft().add(MSG_TYPE_KEY, new JsonPrimitive(type.name()));
        com.sendMessage(msg.getLeft().toString());
        if (msg.getRight() != null) {
            com.sendMessage(String.valueOf(msg.getRight().length));
            com.sendData(msg.getRight());
        } else
            com.sendMessage(String.valueOf(0));
    }

    protected abstract void parseMessage(JsonObject jsonMsg) throws MessageParseException;

    protected abstract void parseResponse(JsonObject jsonMsg) throws MessageParseException;

    public abstract ErrorType processMessage(MessageHandler handler) throws SQLException, IOException;

    public abstract Pair<JsonObject, byte[]> buildMessage() throws MessageBuildException;

    public abstract Pair<JsonObject, byte[]> buildReplyMessage() throws MessageBuildException;
}
