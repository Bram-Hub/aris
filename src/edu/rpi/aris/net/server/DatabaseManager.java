package edu.rpi.aris.net.server;

import edu.rpi.aris.Main;
import edu.rpi.aris.net.NetUtil;
import javafx.util.Pair;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.File;
import java.io.IOException;
import java.security.*;
import java.sql.*;
import java.util.Base64;
import java.util.HashSet;

public class DatabaseManager {

    private static final String[] tables = new String[]{"submission", "assignment", "proof", "user", "class"};
    private static Logger logger = LogManager.getLogger(DatabaseManager.class);

    static {
        if (Security.getProvider("BC") == null)
            Security.addProvider(new BouncyCastleProvider());
    }

    private Connection connection;
    private File dbFile;
    private Thread shutdownHook;
    private SecureRandom random = new SecureRandom();
    private MessageDigest digest;

    public DatabaseManager(File dbFile) throws IOException, SQLException {
        try {
            digest = MessageDigest.getInstance("SHA512", "BC");
            this.dbFile = dbFile;
            boolean exists = dbFile.exists();
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getCanonicalPath());
            if (exists)
                verifyDatabase();
            else
                createTables(false);
            shutdownHook = new Thread(() -> {
                try {
                    close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            Main.instance.showExceptionError(Thread.currentThread(), e, true);
        } catch (Throwable e) {
            if (connection != null)
                connection.close();
            throw e;
        }
    }

    public static void main(String[] args) throws IOException, SQLException {
        new DatabaseManager(new File("test.db"));
    }

    private void verifyDatabase() throws SQLException {
        PreparedStatement statement = connection.prepareStatement("SELECT name FROM sqlite_master WHERE type='table';");
        statement.execute();
        ResultSet set = statement.getResultSet();
        HashSet<String> tables = new HashSet<>();
        while (set.next())
            tables.add(set.getString(1));
        for (String t : DatabaseManager.tables) {
            if (!tables.contains(t)) {
                createTables(true);
                return;
            }
        }
    }

    private void createTables(boolean exists) throws SQLException {
        if (exists) {
            logger.warn("The database either does not exist or is invalid");
            logger.warn("Aris will now create the database which may result in loss of data");
            logger.warn("Do you want to continue? (y/N)");
            String ans = Main.readLine();
            if (!ans.equalsIgnoreCase("y"))
                throw new SQLException("Unable to open database");
        }
        Statement statement = connection.createStatement();
        statement.setQueryTimeout(30);
        for (String t : tables)
            statement.execute("DROP TABLE IF EXISTS " + t);
        statement.execute("CREATE TABLE submission (id integer PRIMARY KEY, class_id integer, assignment_id integer, user_id integer, proof_id integer, data blob, time text, status text);");
        statement.execute("CREATE TABLE assignment (id integer, class_id integer, proof_id integer, name text, due_date text, assigned_by integer, PRIMARY KEY(id, class_id, proof_id));");
        statement.execute("CREATE TABLE proof (id integer PRIMARY KEY, name text, data blob, created_by integer);");
        statement.execute("CREATE TABLE user (id integer PRIMARY KEY, username text, user_type text, salt text, password_hash text, access_token text);");
        statement.execute("CREATE TABLE user_class (user_id integer, class_id integer);");
        statement.execute("CREATE TABLE class (id integer PRIMARY KEY, name text);");
    }

    public Pair<String, String> createUser(String username, String password, String userType) throws SQLException {
        if (username == null || username.length() == 0 || userType == null || !(userType.equals(NetUtil.USER_STUDENT) || userType.equals(NetUtil.USER_INSTRUCTOR)))
            return new Pair<>(null, NetUtil.INVALID);
        if (password == null)
            password = RandomStringUtils.randomAlphabetic(16);
        PreparedStatement count = getStatement("SELECT count(*) FROM user WHERE username = ?;");
        count.setString(1, username);
        ResultSet rs;
        if (count.execute() && (rs = count.getResultSet()).next() && rs.getInt(1) > 0)
            return new Pair<>(null, NetUtil.USER_EXISTS);
        Pair<String, String> sh = getSaltAndHash(password);
        PreparedStatement statement = getStatement("INSERT INTO user VALUES(NULL, ?, ?, ?, ?, NULL);");
        statement.setString(1, username);
        statement.setString(2, userType);
        statement.setString(3, sh.getKey());
        statement.setString(4, sh.getValue());
        statement.execute();
        return new Pair<>(password, NetUtil.OK);
    }

    public Pair<String, String> setPassword(String username, String password) throws SQLException {
        if (username == null || username.length() == 0)
            return new Pair<>(null, NetUtil.INVALID);
        if (password == null)
            password = RandomStringUtils.randomAlphabetic(16);
        PreparedStatement statement = getStatement("UPDATE user SET salt = ?, password = ? WHERE username = ?;");
        Pair<String, String> sh = getSaltAndHash(password);
        statement.setString(1, sh.getKey());
        statement.setString(2, sh.getValue());
        statement.setString(3, username);
        statement.execute();
        return new Pair<>(password, NetUtil.OK);
    }

    private Pair<String, String> getSaltAndHash(String password) {
        byte[] saltBytes = new byte[16];
        random.nextBytes(saltBytes);
        String salt = Base64.getEncoder().encodeToString(saltBytes);
        digest.update(saltBytes);
        String hash = Base64.getEncoder().encodeToString(digest.digest(password.getBytes()));
        return new Pair<>(salt, hash);
    }

    public void close() throws SQLException {
        if (Thread.currentThread() != shutdownHook)
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        connection.close();
    }

    public PreparedStatement getStatement(String statement) throws SQLException {
        return connection.prepareStatement(statement);
    }

}
