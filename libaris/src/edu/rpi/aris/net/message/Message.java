package edu.rpi.aris.net.message;

import com.google.gson.*;
import edu.rpi.aris.net.MessageBuildException;
import edu.rpi.aris.net.MessageCommunication;
import edu.rpi.aris.net.MessageParseException;
import edu.rpi.aris.net.User;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

public abstract class Message {

    public static final String MSG_TYPE_KEY = "msg_type";

    private static Logger logger = LogManager.getLogger(Message.class);
    private static JsonParser parser = new JsonParser();

    private final MessageType type;

    Message(MessageType type) {
        Objects.requireNonNull(type);
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

    public static void sendError(ErrorType errorType, MessageCommunication com) throws IOException {
        sendError(errorType, null, com);
    }

    public static void sendError(ErrorType errorType, String msg, MessageCommunication com) throws IOException {
        try {
            new ErrorMsg(errorType, msg).sendMessage(com);
        } catch (MessageBuildException e) {
            logger.error("Failed to reply with error message", e);
        }
    }

    public static int getInt(JsonObject obj, String key, int defaultValue, boolean error) throws MessageParseException {
        try {
            return obj.get(key).getAsInt();
        } catch (NullPointerException | ClassCastException | IllegalStateException e) {
            if (error)
                throw new MessageParseException("Missing value or mismatched type for key: " + key, e);
            return defaultValue;
        }
    }

    public static String getString(JsonObject obj, String key, String defaultValue, boolean error) throws MessageParseException {
        try {
            return obj.get(key).getAsString();
        } catch (NullPointerException | ClassCastException | IllegalStateException e) {
            if (error)
                throw new MessageParseException("Missing value or mismatched type for key: " + key, e);
            return defaultValue;
        }
    }

    public static JsonArray getArray(JsonObject obj, String key) throws MessageParseException {
        try {
            return obj.get(key).getAsJsonArray();
        } catch (NullPointerException | IllegalStateException e) {
            throw new MessageParseException("Missing value or mismatched type for key: " + key, e);
        }
    }

    public static JsonObject getAsObject(JsonElement element) throws MessageParseException {
        try {
            return element.getAsJsonObject();
        } catch (IllegalStateException e) {
            throw new MessageParseException("Invalid json object in message", e);
        }
    }

    public final MessageType getMessageType() {
        return type;
    }

    public final void sendMessage(MessageCommunication com) throws IOException, MessageBuildException {
        send(buildMessage(), com);
    }

    public final void replyMessage(MessageCommunication com) throws IOException, MessageBuildException {
        send(buildReplyMessage(), com);
    }

    private void send(Pair<JsonObject, byte[]> msg, MessageCommunication com) throws IOException {
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

    public abstract ErrorType processMessage(Connection connection, User user) throws SQLException, IOException;

    public abstract Pair<JsonObject, byte[]> buildMessage() throws MessageBuildException;

    public abstract Pair<JsonObject, byte[]> buildReplyMessage() throws MessageBuildException;
}
