package edu.rpi.aris.net.message;

import com.google.gson.*;
import edu.rpi.aris.net.MessageBuildException;
import edu.rpi.aris.net.MessageCommunication;
import edu.rpi.aris.net.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

public abstract class Message {

    private static final Gson gson;
    private static Logger logger = LogManager.getLogger(Message.class);

    static {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(Message.class, new ArisMessageAdapter());
        gsonBuilder.registerTypeAdapter(byte[].class, new ByteArrayAdapter());
        gson = gsonBuilder.create();
    }

    private byte[] data = null;

    public static Message parse(MessageCommunication com) {
        try {
            return gson.fromJson(com.readMessage(), Message.class);
        } catch (JsonSyntaxException e) {
            logger.error("Message in incorrect format", e);
        } catch (IOException e) {
            logger.error("Failed to read json from peer", e);
        }
        return null;
    }

    public static void sendError(ErrorType errorType, MessageCommunication com) throws IOException {
        sendError(errorType, null, com);
    }

    public static void sendError(ErrorType errorType, String msg, MessageCommunication com) throws IOException {
        new ErrorMsg(errorType, msg).sendMessage(com);
    }

    public final byte[] getData() {
        return data;
    }

    public final void setData(byte[] data) {
        this.data = data;
    }

    public final void sendMessage(MessageCommunication com) throws IOException {
       com.sendMessage(gson.toJson(this));
    }

    public abstract ErrorType processMessage(Connection connection, User user) throws SQLException, IOException;

    public abstract MessageType getMessageType();

}
