package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;

public class AuthMessage extends Message {

    private static final SecureRandom random = new SecureRandom();
    private static final Logger log = LogManager.getLogger();

    private final String username;
    private boolean isAccessToken;
    private String version;
    private String passAccessToken;
    private Auth status;

    public AuthMessage(String username, String passAccessToken, boolean isAccessToken) {
        super(null, true);
        this.username = username;
        this.passAccessToken = passAccessToken;
        this.isAccessToken = isAccessToken;
        this.version = LibAssign.VERSION;
    }

    public AuthMessage(Auth status) {
        this(null, null, false);
        this.status = status;
    }

    public User checkAuth(Connection connection, ServerPermissions permissions) throws SQLException {
        if (username == null) {
            log.info("Username is null");
            status = Auth.FAIL;
            return null;
        }
        String pass = passAccessToken;
        passAccessToken = null;
        Thread.currentThread().setName(Thread.currentThread().getName() + "/" + username);
        log.info("Authenticating user: " + username);
        try (PreparedStatement statement = connection.prepareStatement("SELECT salt, password_hash, access_token, id, default_role, force_reset FROM users WHERE username = ?;")) {
            statement.setString(1, username);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    String salt = rs.getString(1);
                    String savedHash = rs.getString(isAccessToken ? 3 : 2);
                    int userId = rs.getInt(4);
                    ServerRole userRole;
                    try {
                        userRole = permissions.getRole(rs.getInt(5));
                    } catch (IllegalArgumentException e) {
                        log.error("Failed to parse UserType", e);
                        userRole = permissions.getLowestRole();
                        try (PreparedStatement updateUserType = connection.prepareStatement("UPDATE users SET default_role = ? WHERE username = ?;")) {
                            updateUserType.setInt(1, userRole.getId());
                            updateUserType.setString(2, username);
                            updateUserType.executeUpdate();
                        }
                    }
                    boolean forceReset = rs.getBoolean(6);
                    if (DBUtils.checkPass(pass, salt, savedHash)) {
                        isAccessToken = true;
                        passAccessToken = generateAccessToken();
                        MessageDigest digest = DBUtils.getDigest();
                        digest.update(Base64.getDecoder().decode(salt));
                        String hashed = Base64.getEncoder().encodeToString(digest.digest(passAccessToken.getBytes()));
                        try (PreparedStatement updateAccessToken = connection.prepareStatement("UPDATE users SET access_token = ? WHERE username = ?;")) {
                            updateAccessToken.setString(1, hashed);
                            updateAccessToken.setString(2, username);
                            updateAccessToken.executeUpdate();
                        }
                        User user = new User(userId, username, userRole, forceReset);
                        if (forceReset) {
                            log.info("Password expired reset required");
                            status = Auth.RESET;
                        } else
                            status = Auth.OK;
                        return user;
                    } else {
                        status = Auth.FAIL;
                        return null;
                    }
                } else {
                    status = Auth.FAIL;
                    return null;
                }
            }
        }
    }

    private String generateAccessToken() {
        byte[] tokenBytes = new byte[256];
        random.nextBytes(tokenBytes);
        return Base64.getEncoder().encodeToString(tokenBytes);
    }

    @Override
    public ErrorType processMessage(Connection connection, User user, ServerPermissions permissions) throws Exception {
        return ErrorType.UNAUTHORIZED;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.AUTH;
    }

    @Override
    public boolean checkValid() {
        return true;
    }

    public String getVersion() {
        return version;
    }

    public String getUsername() {
        return username;
    }

    public boolean isAccessToken() {
        return isAccessToken;
    }

    public String getPassAccessToken() {
        return passAccessToken;
    }

    public void setPassAccessToken(String passAccessToken) {
        this.passAccessToken = passAccessToken;
    }

    public Auth getStatus() {
        return status;
    }

    public void setStatus(Auth status) {
        this.status = status;
    }

    public enum Auth {
        BAN,
        ERROR,
        FAIL,
        INVALID,
        OK,
        RESET,
        UNSUPPORTED_VERSION
    }
}
