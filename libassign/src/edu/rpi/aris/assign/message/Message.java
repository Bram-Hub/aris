package edu.rpi.aris.assign.message;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import edu.rpi.aris.assign.MessageCommunication;
import edu.rpi.aris.assign.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.ZonedDateTime;

public abstract class Message {

    private static final Gson gson;
    private static Logger logger = LogManager.getLogger(Message.class);

    static {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(Message.class, new ArisMessageAdapter());
        gsonBuilder.registerTypeAdapter(byte[].class, new ByteArrayAdapter());
        gsonBuilder.registerTypeAdapter(ZonedDateTime.class, new ZDTGsonAdapter());
        gson = gsonBuilder.create();
    }

    @NotNull
    private static Message parse(@NotNull MessageCommunication com) {
        try {
            Message msg = gson.fromJson(com.readMessage(), Message.class);
            if (!msg.checkValid()) {
                logger.error("Message not formatted properly");
                return new ErrorMsg(ErrorType.PARSE_ERR, "Improperly formatted json message");
            }
            return msg;
        } catch (JsonSyntaxException e) {
            logger.error("Message in incorrect format", e);
            return new ErrorMsg(ErrorType.PARSE_ERR, "Message in incorrect format");
        } catch (IOException e) {
            logger.error("Failed to read json from peer", e);
            return new ErrorMsg(ErrorType.IO_ERROR, "Failed to read json from peer");
        }
    }

    @Nullable
    public static Message get(@NotNull MessageCommunication com) {
        Message reply = parse(com);
        if (reply instanceof ErrorMsg) {
            com.handleErrorMsg((ErrorMsg) reply);
            return null;
        }
        return reply;
    }

    public final void send(@NotNull MessageCommunication com) throws IOException {
        com.sendMessage(gson.toJson(this, Message.class));
    }

    /**
     * Sends the message represented by this {@link Message} object using the given
     * {@link MessageCommunication} and retrieves the reply from the {@link MessageCommunication}.
     * If an error occurs {@link MessageCommunication#handleErrorMsg(ErrorMsg)} is called to handle the error
     *
     * @param com The {@link MessageCommunication} object to communicate with. Cannot be null
     * @return The reply message or null if an error occurred. The return type (if it's not null) should be the same
     * type as the object this method was called on
     * @throws IOException If there is an error when communicating
     */
    @Nullable
    public final Message sendAndGet(@NotNull MessageCommunication com) throws IOException {
        send(com);
        Message reply = parse(com);
        if (!reply.getClass().equals(this.getClass())) {
            if (!(reply instanceof ErrorMsg))
                reply = new ErrorMsg(ErrorType.INCORRECT_MSG_TYPE, "Expected \"" + getMessageType() + "\" received \"" + reply.getMessageType().name() + "\"");
            com.handleErrorMsg((ErrorMsg) reply);
            return null;
        }
        return reply;
    }

    @Nullable
    public abstract ErrorType processMessage(Connection connection, User user) throws SQLException;

    @NotNull
    public abstract MessageType getMessageType();

    public abstract boolean checkValid();

}
