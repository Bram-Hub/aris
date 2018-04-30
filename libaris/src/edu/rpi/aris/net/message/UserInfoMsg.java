package edu.rpi.aris.net.message;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import edu.rpi.aris.net.MessageParseException;
import edu.rpi.aris.net.User;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class UserInfoMsg extends Message {

    public static final String USR_ID = "usr_id";
    public static final String USR_TYPE = "usr_type";
    public static final String CLASSES = "classes";
    public static final String CLS_ID = "cls_id";
    public static final String CLS_NAME = "cls_name";

    private int userId;
    private String userType;
    private HashMap<Integer, String> classes = new HashMap<>();

    public UserInfoMsg() {
        super(MessageType.GET_USER_INFO);
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public HashMap<Integer, String> getClasses() {
        return classes;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    @Override
    protected void parseMessage(JsonObject jsonMsg) {
        // No parsing needs to be done on the server side
    }

    @Override
    protected void parseResponse(JsonObject jsonMsg) throws MessageParseException {
        userId = Message.getInt(jsonMsg, USR_ID, -1, true);
        userType = Message.getString(jsonMsg, USR_TYPE, null, true);
        JsonArray classArray = Message.getArray(jsonMsg, CLASSES);
        for (JsonElement e : classArray) {
            JsonObject o = Message.getAsObject(e);
            classes.put(Message.getInt(o, CLS_ID, -1, true), Message.getString(o, CLS_NAME, null, true));
        }
    }

    @Override
    public ErrorType processMessage(Connection connection, User user) throws SQLException {
        userId = user.uid;
        try (PreparedStatement getUserType = connection.prepareStatement("SELECT user_type FROM users WHERE username = ?;");
             PreparedStatement getInfo = connection.prepareStatement("SELECT c.id, c.name FROM class c, users u, user_class uc WHERE u.id = uc.user_id AND c.id = uc.class_id AND u.id = ?")) {
            getUserType.setString(1, user.username);
            try (ResultSet userTypeRs = getUserType.executeQuery()) {
                if (userTypeRs.next())
                    userType = userTypeRs.getString(1);
                else
                    return ErrorType.NOT_FOUND;
            }
            getInfo.setInt(1, userId);
            try (ResultSet infoRs = getInfo.executeQuery()) {
                while (infoRs.next())
                    classes.put(infoRs.getInt(1), infoRs.getString(2));
            }
        }
        return null;
    }

    @Override
    public Pair<JsonObject, byte[]> buildMessage() {
        return new ImmutablePair<>(new JsonObject(), null);
    }

    @Override
    public Pair<JsonObject, byte[]> buildReplyMessage() {
        JsonObject obj = new JsonObject();
        obj.addProperty(USR_ID, userId);
        obj.addProperty(USR_TYPE, userType);
        JsonArray classArr = new JsonArray();
        for (Map.Entry<Integer, String> c : classes.entrySet()) {
            JsonObject cObj = new JsonObject();
            cObj.addProperty(CLS_ID, c.getKey());
            cObj.addProperty(CLS_NAME, c.getValue());
            classArr.add(cObj);
        }
        obj.add(CLASSES, classArr);
        return new ImmutablePair<>(obj, null);
    }

}
