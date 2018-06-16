package edu.rpi.aris.net.message;

import edu.rpi.aris.net.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

public class UserInfoMsg extends Message {

    private int userId;
    private String userType;
    private HashMap<Integer, String> classes = new HashMap<>();

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
    public MessageType getMessageType() {
        return MessageType.GET_USER_INFO;
    }

}
