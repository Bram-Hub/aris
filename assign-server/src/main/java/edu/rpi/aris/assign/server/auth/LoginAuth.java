package edu.rpi.aris.assign.server.auth;

import edu.rpi.aris.assign.DBUtils;
import edu.rpi.aris.assign.ServerPermissions;
import edu.rpi.aris.assign.ServerRole;
import edu.rpi.aris.assign.User;
import edu.rpi.aris.assign.message.AuthMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;

public abstract class LoginAuth {

    private static final Logger log = LogManager.getLogger();

    LoginAuth() {
    }

    public static User verifyAuth(AuthMessage authMessage, Connection connection, ServerPermissions permissions) throws SQLException {
        String pass = authMessage.getPassAccessToken();
        authMessage.setPassAccessToken(null);
        if (authMessage.getUsername() == null) {
            log.info("Username is null");
            authMessage.setStatus(AuthMessage.Auth.FAIL);
            return null;
        }
        Thread.currentThread().setName(Thread.currentThread().getName() + "/" + authMessage.getUsername());
        log.info("Authenticating user: " + authMessage.getUsername());
        log.info("Authentication method: " + (authMessage.isAccessToken() ? "Acess Token" : "Password"));
        try (PreparedStatement statement = connection.prepareStatement("SELECT salt, password_hash, access_token, id, default_role, force_reset, auth_type FROM users WHERE username = ?;")) {
            statement.setString(1, authMessage.getUsername());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    String salt = rs.getString(1);
                    String savedHash = rs.getString(authMessage.isAccessToken() ? 3 : 2);
                    int userId = rs.getInt(4);
                    ServerRole userRole;
                    try {
                        userRole = permissions.getRole(rs.getInt(5));
                    } catch (IllegalArgumentException e) {
                        log.error("Failed to parse User Role", e);
                        userRole = permissions.getLowestRole();
                        try (PreparedStatement updateUserType = connection.prepareStatement("UPDATE users SET default_role = ? WHERE username = ?;")) {
                            updateUserType.setInt(1, userRole.getId());
                            updateUserType.setString(2, authMessage.getUsername());
                            updateUserType.executeUpdate();
                        }
                    }
                    boolean forceReset = rs.getBoolean(6);
                    Type authType;
                    try {
                        authType = authMessage.isAccessToken() ? Type.ACCESS_TOKEN : Type.valueOf(rs.getString(7));
                    } catch (IllegalArgumentException e) {
                        authMessage.setStatus(AuthMessage.Auth.ERROR);
                        return null;
                    }
                    if (authType.getAuth().checkAuth(authMessage.getUsername(), pass, salt, savedHash) == null) {
                        authMessage.setIsAccessToken(true);
                        authMessage.setPassAccessToken(DBUtils.generateAccessToken());
                        MessageDigest digest = DBUtils.getDigest();
                        digest.update(Base64.getDecoder().decode(salt));
                        String hashed = Base64.getEncoder().encodeToString(digest.digest(authMessage.getPassAccessToken().getBytes()));
                        try (PreparedStatement updateAccessToken = connection.prepareStatement("UPDATE users SET access_token = ? WHERE username = ?;")) {
                            updateAccessToken.setString(1, hashed);
                            updateAccessToken.setString(2, authMessage.getUsername());
                            updateAccessToken.executeUpdate();
                        }
                        User user = new User(userId, authMessage.getUsername(), userRole, forceReset);
                        if (forceReset) {
                            log.info("Password expired reset required");
                            authMessage.setStatus(AuthMessage.Auth.RESET);
                        } else
                            authMessage.setStatus(AuthMessage.Auth.OK);
                        return user;
                    } else {
                        log.info("invalid " + (authMessage.isAccessToken() ? "access token" : "password"));
                        authMessage.setStatus(AuthMessage.Auth.FAIL);
                        return null;
                    }
                } else {
                    authMessage.setStatus(AuthMessage.Auth.FAIL);
                    return null;
                }
            }
        }
    }

    /**
     * @param user      the username
     * @param pass      the password
     * @param salt      the stored password salt from the database
     * @param savedHash the stored password hash from the database
     * @return null if the password is correct or a String with the error message
     */
    protected abstract String checkAuth(String user, String pass, String salt, String savedHash);

    public abstract boolean isSupported();

    public enum Type {
        ACCESS_TOKEN(LocalLoginAuth.getInstance()),
        LOCAL(LocalLoginAuth.getInstance()),
        PAM(PAMLoginAuth.getInstance());

        @NotNull
        private LoginAuth auth;

        Type(@NotNull LoginAuth a) {
            auth = a;
        }

        @NotNull
        public LoginAuth getAuth() {
            return auth;
        }

    }

}
