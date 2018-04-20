package edu.rpi.aris.net.message;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import edu.rpi.aris.net.MessageHandler;
import edu.rpi.aris.net.MessageParseException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;

public class UserInfoMsg extends Message {

    public static final String USR_ID = "usr_id";
    public static final String USR_TYPE = "usr_type";
    public static final String CLASSES = "classes";
    public static final String CLS_ID = "cls_id";
    public static final String CLS_NAME = "cls_name";

    private int userId;
    private String userType;
    private HashMap<Integer, String> classes = new HashMap<>();

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public HashMap<Integer, String> getClasses() {
        return classes;
    }

    @Override
    protected void parseMessage(JsonObject jsonMsg) {
        // No parsing needs to be done on the server side
    }

    @Override
    protected void parseResponse(JsonObject jsonMsg) throws MessageParseException {
        try {
            userId = jsonMsg.get(USR_ID).getAsInt();
            userType = jsonMsg.get(USR_TYPE).getAsString();
            JsonArray classArray = jsonMsg.get(CLASSES).getAsJsonArray();
            for (JsonElement e : classArray) {
                JsonObject o = e.getAsJsonObject();
                classes.put(o.get(CLS_ID).getAsInt(), o.get(CLS_NAME).getAsString());
            }
        } catch (ClassCastException | IllegalStateException e) {
            throw new MessageParseException(e);
        }
    }

    @Override
    public ErrorType processMessage(MessageHandler handler) throws SQLException, IOException {
        return handler.getUserInfo(this);
    }

    @Override
    public Pair<JsonObject, byte[]> buildMessage() {
        return new ImmutablePair<>(new JsonObject(), null);
    }

    @Override
    public Pair<JsonObject, byte[]> buildReplyMessage() {
        JsonObject obj = new JsonObject();
        return null;
    }
}
