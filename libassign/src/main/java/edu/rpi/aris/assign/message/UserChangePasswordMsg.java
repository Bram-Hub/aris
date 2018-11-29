package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.DBUtils;
import edu.rpi.aris.assign.Perm;
import edu.rpi.aris.assign.ServerPermissions;
import edu.rpi.aris.assign.User;
import org.apache.commons.lang3.tuple.Triple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;

public class UserChangePasswordMsg extends Message {

    private final String username;
    private String newPass;
    private String oldPass;
    private String accessToken = null;

    public UserChangePasswordMsg(String username, String newPass, String oldPass) {
        super(Perm.USER_CHANGE_PASS, true);
        this.username = username;
        this.newPass = newPass;
        this.oldPass = oldPass;
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
            if (newPass == null || (user.username.equals(username) && !DBUtils.checkPasswordComplexity(username, newPass, oldPass)))
                return ErrorType.AUTH_WEAK_PASS;
            Triple<String, String, ErrorType> pair = DBUtils.setPassword(connection, username, newPass);
            if (pair.getRight() == null) {
                String hashedAccessToken = "";
                if (user.username.equals(username)) {
                    user.resetPass();
                    accessToken = DBUtils.generateAccessToken();
                    MessageDigest digest = DBUtils.getDigest();
                    digest.update(Base64.getDecoder().decode(pair.getMiddle()));
                    hashedAccessToken = Base64.getEncoder().encodeToString(digest.digest(accessToken.getBytes()));
                }
                try (PreparedStatement updateAccessToken = connection.prepareStatement("UPDATE users SET force_reset = ?, access_token = ? WHERE username = ?;")) {
                    updateAccessToken.setBoolean(1, !user.username.equals(username));
                    updateAccessToken.setString(2, hashedAccessToken);
                    updateAccessToken.setString(3, username);
                    updateAccessToken.executeUpdate();
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

    public String getAccessToken() {
        return accessToken;
    }
}
