package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.DBUtils;
import edu.rpi.aris.assign.NetUtil;
import edu.rpi.aris.assign.User;
import org.apache.commons.lang3.tuple.Pair;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserEditMsg extends Message {

    private final String username;
    private final String newType;
    private final boolean changePass;
    private String newPass;
    private String oldPass;

    public UserEditMsg(String username, String newType, String newPass, String oldPass, boolean changePass) {
        this.username = username;
        this.newType = newType;
        this.newPass = newPass;
        this.oldPass = oldPass;
        this.changePass = changePass;
    }

    // DO NOT REMOVE!! Default constructor is required for gson deserialization
    private UserEditMsg() {
        username = null;
        newPass = null;
        oldPass = null;
        newType = null;
        changePass = false;
    }

    @Override
    public ErrorType processMessage(Connection connection, User user) throws SQLException {
        boolean resetPass = newPass != null;
        try {
            if (!user.username.equals(username) && !user.userType.equals(NetUtil.USER_INSTRUCTOR))
                return ErrorType.UNAUTHORIZED;
            if (!user.userType.equals(NetUtil.USER_INSTRUCTOR) && newType != null && !newType.equals(user.userType))
                return ErrorType.UNAUTHORIZED;
            if (oldPass == null)
                return ErrorType.AUTH_FAIL;
            try (PreparedStatement getHash = connection.prepareStatement("SELECT salt, password_hash FROM users WHERE username = ?;")) {
                getHash.setString(1, username);
                try (ResultSet rs = getHash.executeQuery()) {
                    if (!rs.next() || !DBUtils.checkPass(oldPass, rs.getString(1), rs.getString(2)))
                        return ErrorType.AUTH_FAIL;
                }
            }
            if (newType != null && !newType.equals(user.userType))
                try (PreparedStatement update = connection.prepareStatement("UPDATE users SET user_type = ? WHERE username = ?;")) {
                    update.setString(1, newType);
                    update.setString(2, username);
                    update.executeUpdate();
                }
            if (changePass) {
                Pair<String, ErrorType> pair = DBUtils.setPassword(connection, username, newPass);
                newPass = pair.getLeft();
                return pair.getRight();
            }
            return null;
        } finally {
            if (resetPass)
                newPass = null;
            oldPass = null;
        }
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.EDIT_USER;
    }

    @Override
    public boolean checkValid() {
        return username != null;
    }
}
