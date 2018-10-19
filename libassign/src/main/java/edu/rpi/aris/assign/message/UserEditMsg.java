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

public class UserEditMsg extends Message {

    private final String username;
    private final boolean changePass;
    private String newPass;
    private String oldPass;

    public UserEditMsg(String username, String newPass, String oldPass, boolean changePass) {
        super(Perm.USER_EDIT, true);
        this.username = username;
        this.newPass = newPass;
        this.oldPass = oldPass;
        this.changePass = changePass;
    }

    // DO NOT REMOVE!! Default constructor is required for gson deserialization
    private UserEditMsg() {
        this(null, null, null, false);
    }

    @Nullable
    @Override
    public ErrorType processMessage(@NotNull Connection connection, @NotNull User user, @NotNull ServerPermissions permissions) throws SQLException {
        boolean resetPass = newPass != null;
        try {
            if (!user.username.equals(username) && !permissions.hasPermission(user, Perm.USER_EDIT))
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
            if (changePass) {
                if (newPass.equals(oldPass) || !DBUtils.checkPasswordComplexity(username, newPass))
                    return ErrorType.AUTH_WEAK_PASS;
                Pair<String, ErrorType> pair = DBUtils.setPassword(connection, username, newPass);
                newPass = pair.getLeft();
                if (pair.getRight() == null) {
                    user.resetPass();
                    try (PreparedStatement forceOff = connection.prepareStatement("UPDATE users SET force_reset = false WHERE username = ?;")) {
                        forceOff.setString(1, username);
                        forceOff.executeUpdate();
                    }
                }
                return pair.getRight();
            }
            return null;
        } finally {
            if (resetPass)
                newPass = null;
            oldPass = null;
        }
    }

    public boolean isChangePass() {
        return changePass;
    }

    @NotNull
    @Override
    public MessageType getMessageType() {
        return MessageType.EDIT_USER;
    }

    @Override
    public boolean checkValid() {
        return username != null;
    }

    public String getUsername() {
        return username;
    }
}
