package edu.rpi.aris.assign.server.auth;

import edu.rpi.aris.assign.*;
import edu.rpi.aris.assign.message.AuthMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;

public class LoginUtil {
    private static final Logger log = LogManager.getLogger();

    static {
        // make sure we register the authentication classes
        LocalLoginAuth.register();
        PAMLoginAuth.register();
    }

    public static void register() {
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
        log.info("Authentication method: " + (authMessage.isAccessToken() ? "Access Token" : "Password"));
        try (PreparedStatement statement = connection.prepareStatement("SELECT salt, password_hash, access_token, id, default_role, force_reset, auth_type FROM users WHERE username = ?;")) {
            statement.setString(1, authMessage.getUsername());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    String salt = rs.getString(1);
                    String savedHash = rs.getString(authMessage.isAccessToken() ? 3 : 2);

                    Pair<LoginAuth, AuthType> auth = getAuth(rs.getString(7), authMessage);
                    if (auth == null)
                        return null;

                    boolean forceReset = auth.getFirst().isLocalAuth() && rs.getBoolean(6);

                    if (verifyAuth(auth.getFirst(), authMessage, pass, salt, savedHash)) {
                        ServerRole userRole = getUserRole(rs.getInt(5), authMessage.getUsername(), connection, permissions);
                        return handleAuthSuccess(authMessage, rs.getInt(4), userRole, auth.getSecond(), forceReset, auth.getFirst().isLocalAuth(), salt, connection);
                    } else {
                        log.info("Invalid " + (authMessage.isAccessToken() ? "access token" : "password"));
                        authMessage.setStatus(AuthMessage.Auth.FAIL);
                        authMessage.setErrorMsg("Invalid username or password");
                        return null;
                    }
                } else {
                    authMessage.setStatus(AuthMessage.Auth.FAIL);
                    authMessage.setErrorMsg("Invalid username or password");
                    return null;
                }
            }
        }
    }

    private static boolean verifyAuth(LoginAuth auth, AuthMessage msg, String pass, String salt, String hash) {
        String response = auth.checkAuth(msg.getUsername(), pass, salt, hash);
        msg.setErrorMsg(response);
        return response == null;
    }

    private static User handleAuthSuccess(AuthMessage msg, int userId, ServerRole userRole, AuthType authType, boolean forceReset, boolean canChangePassword, String salt, Connection connection) throws SQLException {
        msg.setIsAccessToken(true);
        msg.setPassAccessToken(DBUtils.generateAccessToken());
        MessageDigest digest = DBUtils.getDigest();
        digest.update(Base64.getDecoder().decode(salt));
        String hashed = Base64.getEncoder().encodeToString(digest.digest(msg.getPassAccessToken().getBytes()));
        try (PreparedStatement updateAccessToken = connection.prepareStatement("UPDATE users SET access_token = ? WHERE username = ?;")) {
            updateAccessToken.setString(1, hashed);
            updateAccessToken.setString(2, msg.getUsername());
            updateAccessToken.executeUpdate();
        }
        User user = new User(userId, msg.getUsername(), userRole, authType, forceReset, canChangePassword);
        if (forceReset) {
            log.info("Password expired reset required");
            msg.setStatus(AuthMessage.Auth.RESET);
        } else
            msg.setStatus(AuthMessage.Auth.OK);
        return user;
    }

    private static ServerRole getUserRole(int roleId, String username, Connection connection, ServerPermissions permissions) throws SQLException {
        ServerRole userRole;
        try {
            userRole = permissions.getRole(roleId);
        } catch (IllegalArgumentException e) {
            log.error("Failed to parse User Role", e);
            userRole = permissions.getLowestRole();
            try (PreparedStatement updateUserType = connection.prepareStatement("UPDATE users SET default_role = ? WHERE username = ?;")) {
                updateUserType.setInt(1, userRole.getId());
                updateUserType.setString(2, username);
                updateUserType.executeUpdate();
            }
        }
        return userRole;
    }

    private static Pair<LoginAuth, AuthType> getAuth(String authStr, AuthMessage msg) {
        AuthType authTypeUse, authType;
        try {
            authType = AuthType.valueOf(authStr);
            authTypeUse = msg.isAccessToken() ? AuthType.ACCESS_TOKEN : authType;
        } catch (IllegalArgumentException e) {
            log.error("Failed to parse AuthType: " + authStr, e);
            msg.setStatus(AuthMessage.Auth.ERROR);
            msg.setErrorMsg("Unknown AuthType: " + authStr);
            return null;
        }
        LoginAuth auth = LoginAuth.getAuthForType(authTypeUse);
        if (auth == null) {
            log.error("Failed to get LoginAuth instance for AuthType: " + authTypeUse);
            msg.setStatus(AuthMessage.Auth.ERROR);
            msg.setErrorMsg("Unknown AuthType: " + authStr);
            return null;
        }
        if (!auth.isSupported()) {
            log.error("AuthType: " + authTypeUse + " is unsupported on this system");
            msg.setStatus(AuthMessage.Auth.ERROR);
            msg.setErrorMsg("Authentication type not supported by server");
            return null;
        }
        return new Pair<>(auth, authType);
    }

}
