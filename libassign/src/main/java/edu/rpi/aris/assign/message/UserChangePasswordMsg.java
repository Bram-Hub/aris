package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.DBUtils;
import edu.rpi.aris.assign.Perm;
import edu.rpi.aris.assign.ServerPermissions;
import edu.rpi.aris.assign.User;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserChangePasswordMsg extends Message {

    private final String username;
    private String newPass;
    private String oldPass;

    public UserChangePasswordMsg(String username, String newPass, String oldPass) {
        super(Perm.USER_CHANGE_PASS, true);
        this.username = username;
        this.newPass = newPass;
        this.oldPass = oldPass;
    }

    public UserChangePasswordMsg(String username, String newPass) {
        this(username, newPass, null);
    }

    // DO NOT REMOVE!! Default constructor is required for gson deserialization
    private UserChangePasswordMsg() {
        this(null, null, null);
    }

    @Nullable
    @Override
    public ErrorType processMessage(@NotNull Connection connection, @NotNull User user, @NotNull ServerPermissions permissions) throws SQLException {
        try {
            if (!user.username.equals(username) && !permissions.hasPermission(user, Perm.USER_CHANGE_PASS))
                return ErrorType.UNAUTHORIZED;
            if (user.username.equals(username)) {
                if (oldPass == null)
                    return ErrorType.AUTH_FAIL;
                try (PreparedStatement getHash = connection.prepareStatement("SELECT salt, password_hash FROM users WHERE username = ?;")) {
                    getHash.setString(1, username);
                    try (ResultSet rs = getHash.executeQuery()) {
                        if (!rs.next() || !DBUtils.checkPass(oldPass, rs.getString(1), rs.getString(2)))
                            return ErrorType.AUTH_FAIL;
                    }
                }
            }
            if (user.username.equals(username) && (newPass.equals(oldPass) || !DBUtils.checkPasswordComplexity(username, newPass)))
                return ErrorType.AUTH_WEAK_PASS;
            Pair<String, ErrorType> pair = DBUtils.setPassword(connection, username, newPass);
            if (pair.getRight() == null) {
                if (user.username.equals(username))
                    user.resetPass();
                try (PreparedStatement forceOff = connection.prepareStatement("UPDATE users SET force_reset = ? WHERE username = ?;")) {
                    forceOff.setBoolean(1, !user.username.equals(username));
                    forceOff.setString(2, username);
                    forceOff.executeUpdate();
                }
            }
            return pair.getRight();
        } finally {
            newPass = null;
            oldPass = null;
        }
    }

    @NotNull
    @Override
    public MessageType getMessageType() {
        return MessageType.CHANGE_PASSWORD;
    }

    @Override
    public boolean checkValid() {
        return username != null;
    }

    public String getUsername() {
        return username;
    }
}
