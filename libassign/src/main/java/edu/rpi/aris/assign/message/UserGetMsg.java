package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.User;
import edu.rpi.aris.assign.UserType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class UserGetMsg extends Message {

    private int userId;
    private UserType userType;
    private HashMap<Integer, String> classes = new HashMap<>();

    public UserType getUserType() {
        return userType;
    }

    public void setUserType(UserType userType) {
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
    public ErrorType processMessage(Connection connection, User user) throws SQLException {
        userId = user.uid;
        try (PreparedStatement getInfo = connection.prepareStatement(user.userType == UserType.ADMIN ? "SELECT id, name FROM class;" : "SELECT c.id, c.name FROM class c, users u, user_class uc WHERE u.id = uc.user_id AND c.id = uc.class_id AND u.id = ?")) {
            userType = user.userType;
            if (userType != UserType.ADMIN)
                getInfo.setInt(1, userId);
            try (ResultSet infoRs = getInfo.executeQuery()) {
                while (infoRs.next())
                    classes.put(infoRs.getInt(1), infoRs.getString(2));
            }
        }
        return null;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.GET_USER_INFO;
    }

    @Override
    public boolean checkValid() {
        for (Map.Entry<Integer, String> c : classes.entrySet())
            if (c.getKey() == null || c.getValue() == null)
                return false;
        return true;
    }

}
