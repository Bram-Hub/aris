package edu.rpi.aris.assign;

import edu.rpi.aris.assign.message.ErrorType;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;

public class DBUtils {

    public static final String COMPLEXITY_RULES = "Password Complexity Requirements:\n" +
            "\tat least 8 characters\n" +
            "\tCannot contain your username\n" +
            "\tCannot be your old password";
    private static final Logger logger = LogManager.getLogger(DBUtils.class);
    private static final SecureRandom random = new SecureRandom();

    public static MessageDigest getDigest() {
        try {
            return MessageDigest.getInstance("SHA512", "BC");
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            logger.fatal("Failed to create MessageDigest", e);
            throw new RuntimeException("Unable to start server due to inability to verify user credentials", e);
        }
    }

    public static boolean checkPass(@NotNull String pass, String salt, String savedHash) {
        MessageDigest digest = getDigest();
        digest.update(Base64.getDecoder().decode(salt));
        String hash = Base64.getEncoder().encodeToString(digest.digest(pass.getBytes()));
        return hash.equals(savedHash);
    }

    @NotNull
    @Contract("_, null, _ -> new")
    public static Triple<String, String, ErrorType> setPassword(Connection connection, String username, String password) throws SQLException {
        if (username == null || username.length() == 0)
            return new ImmutableTriple<>(null, null, ErrorType.INVALID_PASSWORD);
        if (password == null)
            password = RandomStringUtils.randomAlphabetic(16);
        try (PreparedStatement statement = connection.prepareStatement("UPDATE users SET salt = ?, password_hash = ? WHERE username = ?;")) {
            Pair<String, String> sh = getSaltAndHash(password);
            statement.setString(1, sh.getKey());
            statement.setString(2, sh.getValue());
            statement.setString(3, username);
            statement.executeUpdate();
            return new ImmutableTriple<>(password, sh.getKey(), null);
        }
    }

    public static boolean checkPasswordComplexity(String username, @NotNull String password, @Nullable String oldPass) {
        return password.length() >= 8 && !password.toLowerCase().contains(username.toLowerCase()) && !password.equalsIgnoreCase(oldPass);
    }

    @NotNull
    @Contract("_ -> new")
    public static Pair<String, String> getSaltAndHash(@NotNull String password) {
        MessageDigest digest = getDigest();
        byte[] saltBytes = new byte[16];
        random.nextBytes(saltBytes);
        String salt = Base64.getEncoder().encodeToString(saltBytes);
        digest.update(saltBytes);
        String hash = Base64.getEncoder().encodeToString(digest.digest(password.getBytes()));
        return new ImmutablePair<>(salt, hash);
    }

    public static Pair<String, Integer> createUser(Connection connection, String username, String password, String fullName, int roleId, boolean forceReset) throws SQLException {
        if (username == null || username.length() == 0)
            return new ImmutablePair<>(null, -1);
        boolean passProvided = true;
        if (password == null) {
            passProvided = false;
            password = RandomStringUtils.randomAlphabetic(16);
        }
        try (PreparedStatement count = connection.prepareStatement("SELECT count(*) FROM users WHERE username = ?;");
             PreparedStatement statement = connection.prepareStatement("INSERT INTO users (username, salt, password_hash, force_reset, default_role, full_name) VALUES(?, ?, ?, ?, ?, ?) RETURNING id;")) {
            count.setString(1, username);
            try (ResultSet rs = count.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0)
                    return new ImmutablePair<>(null, -1);
            }
            Pair<String, String> sh = getSaltAndHash(password);
            if (passProvided)
                password = null;
            statement.setString(1, username);
            statement.setString(2, sh.getKey());
            statement.setString(3, sh.getValue());
            statement.setBoolean(4, forceReset);
            statement.setInt(5, roleId);
            statement.setString(6, fullName);
            int uid = -1;
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next())
                    uid = rs.getInt(1);
            }
            return new ImmutablePair<>(password, uid);
        }
    }

    public static ServerRole createRole(String name, int rank, Connection connection) throws SQLException {
        if (name == null)
            return null;
        try (PreparedStatement createRole = connection.prepareStatement("INSERT INTO role (name, role_rank) VALUES (?, ?) RETURNING id;")) {
            createRole.setString(1, name);
            createRole.setInt(2, rank);
            try (ResultSet rs = createRole.executeQuery()) {
                if (rs.next())
                    return new ServerRole(rs.getInt(1), name, rank);
            }
        }
        return null;
    }

    public static String generateAccessToken() {
        byte[] tokenBytes = new byte[256];
        random.nextBytes(tokenBytes);
        return Base64.getEncoder().encodeToString(tokenBytes);
    }
}
