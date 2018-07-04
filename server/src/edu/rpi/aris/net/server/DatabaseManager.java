package edu.rpi.aris.net.server;

import edu.rpi.aris.LibAris;
import edu.rpi.aris.net.DBUtils;
import edu.rpi.aris.net.GradingStatus;
import edu.rpi.aris.net.NetUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Security;
import java.sql.*;

public class DatabaseManager {

    private static final String[] tables = new String[]{"submission", "assignment", "problem", "user_class", "users", "class", "version"};
    private static final int DB_SCHEMA_VERSION = 4;
    private static Logger logger = LogManager.getLogger(DatabaseManager.class);

    static {
        if (Security.getProvider("BC") == null)
            Security.addProvider(new BouncyCastleProvider());
    }

    private final String user;
    private final String pass;

    private String connectionString;

    public DatabaseManager(String host, int port, String database, String user, String pass) throws IOException, SQLException {
        this.user = user;
        this.pass = pass;
        connectionString = "jdbc:postgresql://" + host + ":" + port + "/" + database;
        try (Connection connection = getConnection()) {
            logger.info("Verifying database connection");
            verifyDatabase(connection);
        }
    }

    private void verifyDatabase(Connection connection) throws SQLException, IOException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT count(*) FROM information_schema.tables WHERE table_name='version';");
             ResultSet set = statement.executeQuery();
             PreparedStatement version = connection.prepareStatement("SELECT version FROM version LIMIT 1;")) {
            logger.info("Checking database schema version");
            if (!set.next() || set.getInt(1) == 0)
                createTables(connection);
            else {
                try (ResultSet rs = version.executeQuery()) {
                    if (!rs.next())
                        createTables(connection);
                    else {
                        int v = rs.getInt(1);
                        logger.info("Database version: " + v + " Current version: " + DB_SCHEMA_VERSION);
                        if (v < 0 || v > DB_SCHEMA_VERSION)
                            throw new SQLException("Unknown database schema version: " + v);
                        if (v < DB_SCHEMA_VERSION) {
                            try {
                                Method update = DatabaseManager.class.getDeclaredMethod("updateSchema" + v, Connection.class);
                                update.invoke(this, connection);
                            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                                throw new IOException("Cannot update database schema from version " + v, e);
                            }
                        }
                    }
                }
            }
        }
    }

    private void createTables(Connection connection) throws SQLException {
        logger.warn("Creating non existent tables");
        logger.warn("If this is not the first run of the program this may have unexpected results");
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS version (version integer);");
            statement.execute("INSERT INTO version (version) VALUES (" + DB_SCHEMA_VERSION + ");");
            statement.execute("CREATE TABLE IF NOT EXISTS users" +
                    "(id serial PRIMARY KEY," +
                    "username text," +
                    "user_type text," +
                    "salt text," +
                    "password_hash text," +
                    "access_token text," +
                    "check (user_type in ('instructor', 'student')));");
            statement.execute("CREATE TABLE IF NOT EXISTS class" +
                    "(id serial PRIMARY KEY," +
                    "name text);");
            statement.execute("CREATE TABLE IF NOT EXISTS user_class" +
                    "(user_id integer," +
                    "class_id integer," +
                    "constraint uc_ufk foreign key (user_id) references users(id) on delete cascade," +
                    "constraint uc_cfk foreign key (class_id) references class(id) on delete cascade);");
            statement.execute("CREATE TABLE IF NOT EXISTS problem" +
                    "(id serial PRIMARY KEY," +
                    "name text," +
                    "data bytea," +
                    "created_by text," +
                    "created_on timestamp," +
                    "module_name text);");
            statement.execute("CREATE TABLE IF NOT EXISTS assignment" +
                    "(id integer," +
                    "class_id integer," +
                    "problem_id integer," +
                    "name text," +
                    "due_date timestamp," +
                    "assigned_by integer," +
                    "PRIMARY KEY(id, class_id, problem_id)," +
                    "constraint a_cfk foreign key (class_id) references class(id) on delete cascade," +
                    "constraint a_pfk foreign key (problem_id) references problem(id) on delete cascade," +
                    "constraint a_abfk foreign key (assigned_by) references users(id) on delete set NULL);");
            statement.execute("CREATE TABLE IF NOT EXISTS submission" +
                    "(id serial PRIMARY KEY," +
                    "class_id integer," +
                    "assignment_id integer," +
                    "user_id integer," +
                    "problem_id integer," +
                    "data bytea," +
                    "time timestamp," +
                    "short_status text," +
                    "status text," +
                    "check (short_status in ('" + GradingStatus.CORRECT.name() + "', '" + GradingStatus.INCORRECT.name() + "', '" + GradingStatus.GRADING.name() + "', '" + GradingStatus.CORRECT_WARN.name() + "', '" + GradingStatus.INCORRECT_WARN.name() + "'))," +
                    "constraint s_cfk foreign key (class_id) references class(id) on delete cascade," +
                    "constraint s_afk foreign key (assignment_id, class_id, problem_id) references assignment(id, class_id, problem_id) on delete cascade," +
                    "constraint s_ufk foreign key (user_id) references users(id) on delete cascade," +
                    "constraint s_pfk foreign key (problem_id) references problem(id) on delete cascade);");
            connection.commit();
            createUser("admin", "ArisAdmin1", NetUtil.USER_INSTRUCTOR);
        } catch (Throwable e) {
            connection.rollback();
            logger.error("An error occurred while creating the tables and the changes were rolled back");
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    @SuppressWarnings("unused")
    private void updateSchema1(Connection connection) throws SQLException {
        logger.info("Updating database schema to version 2");
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE proof ADD COLUMN created_on timestamp;");
            statement.execute("UPDATE proof SET created_on=now();");
            statement.execute("UPDATE version SET version=2");
            connection.commit();
        } catch (Throwable e) {
            connection.rollback();
            logger.error("An error occurred while updating the database schema and the changes were rolled back");
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
        updateSchema2(connection);
    }

    private void updateSchema2(Connection connection) throws SQLException {
        logger.info("Updating database schema to version 3");
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE temp_proof (id integer, uid integer, uname text);");
            statement.execute("INSERT INTO temp_proof (id, uid) SELECT id, created_by FROM proof;");
            statement.execute("UPDATE temp_proof SET uname = users.username FROM users WHERE temp_proof.uid = users.id;");
            statement.execute("ALTER TABLE proof DROP COLUMN created_by;");
            statement.execute("ALTER TABLE proof ADD COLUMN created_by text;");
            statement.execute("UPDATE proof SET created_by=temp_proof.uname FROM temp_proof WHERE proof.id = temp_proof.id;");
            statement.execute("DROP TABLE temp_proof;");
            statement.execute("UPDATE version SET version=3");
            connection.commit();
        } catch (Throwable e) {
            connection.rollback();
            logger.error("An error occurred while updating the database schema and the changes were rolled back");
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
        updateSchema3(connection);
    }

    private void updateSchema3(Connection connection) throws SQLException {
        logger.info("Updating database schema to version 4");
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement();
             PreparedStatement updateProblem = connection.prepareStatement("UPDATE problem SET module_name = ?;")) {
            statement.execute("ALTER TABLE proof RENAME TO problem;");
            statement.execute("ALTER TABLE problem ADD COLUMN module_name text;");
            updateProblem.setString(1, LibAris.getModuleName());
            updateProblem.executeUpdate();
            statement.execute("ALTER TABLE assignment RENAME proof_id TO problem_id;");
            statement.execute("ALTER TABLE submission RENAME proof_id TO problem_id;");
            connection.commit();
        } catch (Throwable e) {
            connection.rollback();
            logger.error("An error occurred while updating the database schema and the changes were rolled back");
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    public Pair<String, String> createUser(String username, String password, String userType) throws SQLException {
        if (username == null || username.length() == 0 || userType == null || !(userType.equals(NetUtil.USER_STUDENT) || userType.equals(NetUtil.USER_INSTRUCTOR)))
            return new ImmutablePair<>(null, NetUtil.INVALID);
        if (password == null)
            password = RandomStringUtils.randomAlphabetic(16);
        try (Connection connection = getConnection();
             PreparedStatement count = connection.prepareStatement("SELECT count(*) FROM users WHERE username = ?;");
             PreparedStatement statement = connection.prepareStatement("INSERT INTO users (username, user_type, salt, password_hash) VALUES(?, ?, ?, ?);")) {
            count.setString(1, username);
            try (ResultSet rs = count.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0)
                    return new ImmutablePair<>(null, NetUtil.USER_EXISTS);
            }
            Pair<String, String> sh = DBUtils.getSaltAndHash(password);
            statement.setString(1, username);
            statement.setString(2, userType);
            statement.setString(3, sh.getKey());
            statement.setString(4, sh.getValue());
            statement.executeUpdate();
            return new ImmutablePair<>(password, NetUtil.OK);
        }
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(connectionString, user, pass);
    }

}
