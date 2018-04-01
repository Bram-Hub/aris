package edu.rpi.aris.net.server;

import edu.rpi.aris.Main;
import edu.rpi.aris.net.NetUtil;
import javafx.util.Pair;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.IOException;
import java.security.*;
import java.sql.*;
import java.util.Base64;
import java.util.HashSet;

public class DatabaseManager {

    private static final String[] tables = new String[]{"submission", "assignment", "proof", "users", "class", "user_class"};
    private static Logger logger = LogManager.getLogger(DatabaseManager.class);

    static {
        if (Security.getProvider("BC") == null)
            Security.addProvider(new BouncyCastleProvider());
    }

    private final String user;
    private final String pass;

    private SecureRandom random = new SecureRandom();
    private MessageDigest digest;
    private String connectionString;

    public DatabaseManager(String host, int port, String database, String user, String pass) throws IOException, SQLException {
        this.user = user;
        this.pass = pass;
        connectionString = "jdbc:postgresql://" + host + ":" + port + "/" + database;
        try {
            digest = MessageDigest.getInstance("SHA512", "BC");
            try (Connection connection = getConnection()) {
                connection.setAutoCommit(true);
                verifyDatabase(connection);
            }
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            Main.instance.showExceptionError(Thread.currentThread(), e, true);
        }
    }

    private void verifyDatabase(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT table_name FROM information_schema.tables;");
             ResultSet set = statement.executeQuery()) {
            HashSet<String> tables = new HashSet<>();
            while (set.next())
                tables.add(set.getString(1));
            for (String t : DatabaseManager.tables) {
                if (!tables.contains(t)) {
                    createTables(connection);
                    return;
                }
            }
        }
    }

    private void createTables(Connection connection) throws SQLException {
//        if (exists) {
//            logger.warn("The database either does not exist or is invalid");
//            logger.warn("Aris will now create the database which may result in loss of data");
//            logger.warn("Do you want to continue? (y/N)");
//            String ans = Main.readLine();
//            if (!ans.equalsIgnoreCase("y"))
//                throw new SQLException("Unable to open database");
//        }
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(30);
            for (String t : tables)
                statement.execute("DROP TABLE IF EXISTS " + t);
            statement.execute("CREATE TABLE users" +
                    "(id serial PRIMARY KEY," +
                    "username text," +
                    "user_type text," +
                    "salt text," +
                    "password_hash text," +
                    "access_token text," +
                    "check (user_type in ('instructor', 'student')));");
            statement.execute("CREATE TABLE class" +
                    "(id serial PRIMARY KEY," +
                    "name text);");
            statement.execute("CREATE TABLE user_class" +
                    "(user_id integer," +
                    "class_id integer," +
                    "constraint uc_ufk foreign key (user_id) references users(id) on delete cascade," +
                    "constraint uc_cfk foreign key (class_id) references class(id)) on delete cascade;");
            statement.execute("CREATE TABLE proof" +
                    "(id serial PRIMARY KEY," +
                    "name text," +
                    "data bytea," +
                    "created_by integer," +
                    "constraint p_cb foreign key (created_by) references users(id) on delete set NULL;");
            statement.execute("CREATE TABLE assignment" +
                    "(id integer," +
                    "class_id integer," +
                    "proof_id integer," +
                    "name text," +
                    "due_date timestamp," +
                    "assigned_by integer," +
                    "PRIMARY KEY(id, class_id, proof_id)," +
                    "constraint a_cfk foreign key (class_id) references class(id) on delete cascade," +
                    "constraint a_pfk foreign key (proof_id) references proof(id) on delete cascade," +
                    "constraint a_abfk foreign key (assigned_by) references users(id) on delete set NULL;");
            statement.execute("CREATE TABLE submission" +
                    "(id serial PRIMARY KEY," +
                    "class_id integer," +
                    "assignment_id integer," +
                    "user_id integer," +
                    "proof_id integer," +
                    "data bytea," +
                    "time timestamp," +
                    "status text," +
                    "constraint s_cfk foreign key (class_id) references class(id) on delete cascade," +
                    "constraint s_afk foreign key (assignment_id, class_id, proof_id) references assignment(id, class_id, proof_id) on delete cascade," +
                    "constraint s_ufk foreign key (user_id) references users(id) on delete cascade," +
                    "constraint s_pfk foreign key (proof_id) references proof(id) on delete cascade;");
        }
    }

    public Pair<String, String> createUser(String username, String password, String userType) throws SQLException, IOException {
        if (username == null || username.length() == 0 || userType == null || !(userType.equals(NetUtil.USER_STUDENT) || userType.equals(NetUtil.USER_INSTRUCTOR)))
            return new Pair<>(null, NetUtil.INVALID);
        if (password == null)
            password = RandomStringUtils.randomAlphabetic(16);
        try (Connection connection = getConnection();
             PreparedStatement count = connection.prepareStatement("SELECT count(*) FROM users WHERE username = ?;");
             PreparedStatement statement = connection.prepareStatement("INSERT INTO users (username, user_type, salt, password_hash) VALUES(?, ?, ?, ?);")) {
            count.setString(1, username);
            try (ResultSet rs = count.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0)
                    return new Pair<>(null, NetUtil.USER_EXISTS);
            }
            Pair<String, String> sh = getSaltAndHash(password);
            statement.setString(1, username);
            statement.setString(2, userType);
            statement.setString(3, sh.getKey());
            statement.setString(4, sh.getValue());
            statement.executeUpdate();
            return new Pair<>(password, NetUtil.OK);
        }
    }

    public Pair<String, String> setPassword(String username, String password) throws SQLException, IOException {
        if (username == null || username.length() == 0)
            return new Pair<>(null, NetUtil.INVALID);
        if (password == null)
            password = RandomStringUtils.randomAlphabetic(16);
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE users SET salt = ?, password = ? WHERE username = ?;");) {
            Pair<String, String> sh = getSaltAndHash(password);
            statement.setString(1, sh.getKey());
            statement.setString(2, sh.getValue());
            statement.setString(3, username);
            statement.executeUpdate();
            return new Pair<>(password, NetUtil.OK);
        }
    }

    private Pair<String, String> getSaltAndHash(String password) {
        byte[] saltBytes = new byte[16];
        random.nextBytes(saltBytes);
        String salt = Base64.getEncoder().encodeToString(saltBytes);
        digest.update(saltBytes);
        String hash = Base64.getEncoder().encodeToString(digest.digest(password.getBytes()));
        return new Pair<>(salt, hash);
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(connectionString, user, pass);
    }

}
