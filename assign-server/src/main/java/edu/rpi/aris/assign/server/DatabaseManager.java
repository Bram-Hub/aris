package edu.rpi.aris.assign.server;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import edu.rpi.aris.assign.DBUtils;
import edu.rpi.aris.assign.GradingStatus;
import edu.rpi.aris.assign.NetUtil;
import edu.rpi.aris.assign.ServerRole;
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
import java.util.ArrayList;

public class DatabaseManager {

    public static final String DEFAULT_ADMIN_PASS = "ArisAdmin1";
    private static final String[] defaultRoleName = new String[]{"Admin", "Instructor", "TA", "Student"};
    private static final int[] defaultRoleRank = new int[]{0, 1, 2, 3};
    private static final String[] tables = new String[]{"submission", "assignment", "problem", "user_class", "users", "class", "version"};
    private static final int DB_SCHEMA_VERSION = 7;
    private static Logger logger = LogManager.getLogger(DatabaseManager.class);

    static {
        if (Security.getProvider("BC") == null)
            Security.addProvider(new BouncyCastleProvider());
    }

//    private final String user;
//    private final String pass;

    //    private String connectionString;
    private ComboPooledDataSource dataSource;

    public DatabaseManager(String host, int port, String database, String user, String pass) throws IOException, SQLException {
//        this.user = user;
//        this.pass = pass;
        dataSource = new ComboPooledDataSource();
        dataSource.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + database);
        dataSource.setUser(user);
        dataSource.setPassword(pass);
        dataSource.setAutoCommitOnClose(true);
        try (Connection connection = getConnection()) {
            logger.info("Verifying database connection");
            verifyDatabase(connection);
        }
    }

    private void createDefaultRoles() throws SQLException {
        //noinspection ConstantConditions
        if (defaultRoleName.length != defaultRoleRank.length)
            throw new IndexOutOfBoundsException("Default role names/ranks do not match");
        for (int i = 0; i < defaultRoleName.length; ++i)
            createRole(defaultRoleName[i], defaultRoleRank[i]);
    }

    private void verifyDatabase(Connection connection) throws SQLException, IOException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT count(*) FROM information_schema.tables WHERE table_name='version';");
             ResultSet set = statement.executeQuery();
             PreparedStatement version = connection.prepareStatement("SELECT version FROM version LIMIT 1;");
             PreparedStatement checkRole = connection.prepareStatement("SELECT count(*) FROM role;")) {
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
            try (ResultSet rs = checkRole.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0)
                    createDefaultRoles();
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
            statement.execute("DELETE FROM version;");
            statement.execute("INSERT INTO version (version) VALUES (" + DB_SCHEMA_VERSION + ");");
            statement.execute("CREATE TABLE IF NOT EXISTS role" +
                    "(id serial PRIMARY KEY," +
                    "name text," +
                    "role_rank integer);");
            statement.execute("CREATE TABLE IF NOT EXISTS users" +
                    "(id serial PRIMARY KEY," +
                    "username text," +
                    "salt text," +
                    "password_hash text," +
                    "access_token text," +
                    "force_reset boolean," +
                    "default_role integer," +
                    "constraint u_rfk foreign key (default_role) references role(id) on delete restrict);");
            statement.execute("CREATE TABLE IF NOT EXISTS class" +
                    "(id serial PRIMARY KEY," +
                    "name text);");
            statement.execute("CREATE TABLE IF NOT EXISTS user_class" +
                    "(user_id integer," +
                    "class_id integer," +
                    "role_id integer," +
                    "constraint uc_ufk foreign key (user_id) references users(id) on delete cascade," +
                    "constraint uc_cfk foreign key (class_id) references class(id) on delete cascade," +
                    "constraint uc_rfk foreign key (role_id) references role(id) on delete restrict);");
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
            statement.execute("CREATE TABLE IF NOT EXISTS permissions" +
                    "(name text PRIMARY KEY," +
                    "role_id int," +
                    "constraint perm_rfk foreign key (role_id) references role(id) on delete restrict);");
            createDefaultRoles();
            createUser("admin", DEFAULT_ADMIN_PASS, 0, true);
            connection.commit();
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
            statement.execute("UPDATE version SET version=2;");
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
            statement.execute("UPDATE version SET version=3;");
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
            updateProblem.setString(1, "Aris");
            updateProblem.executeUpdate();
            statement.execute("ALTER TABLE assignment RENAME proof_id TO problem_id;");
            statement.execute("ALTER TABLE submission RENAME proof_id TO problem_id;");
            statement.execute("UPDATE version SET version=4;");
            connection.commit();
        } catch (Throwable e) {
            connection.rollback();
            logger.error("An error occurred while updating the database schema and the changes were rolled back");
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
        updateSchema4(connection);
    }

    private void updateSchema4(Connection connection) throws SQLException {
        logger.info("Updating database schema to version 5");
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE user_class ADD COLUMN is_ta boolean;");
            statement.execute("UPDATE user_class SET is_ta=false;");
            statement.execute("UPDATE version SET version=5;");
            connection.commit();
        } catch (Throwable e) {
            connection.rollback();
            logger.error("An error occurred while updating the database schema and the changes were rolled back");
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
        updateSchema5(connection);
    }

    private void updateSchema5(Connection connection) throws SQLException {
        logger.info("Updating database schema to version 6");
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE users ADD COLUMN force_reset boolean;");
            statement.execute("UPDATE users SET force_reset=false;");
            statement.execute("UPDATE version SET version=6;");
            connection.commit();
        } catch (Throwable e) {
            connection.rollback();
            logger.error("An error occurred while updating the database schema and the changes were rolled back");
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
        updateSchema6(connection);
    }

    private void updateSchema6(Connection connection) throws SQLException {
        logger.info("Updating database schema to version 7");
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS role" +
                    "(id serial PRIMARY KEY," +
                    "name text," +
                    "role_rank integer);");
            statement.execute("CREATE TABLE IF NOT EXISTS permissions" +
                    "(name text PRIMARY KEY," +
                    "role_id int," +
                    "constraint perm_rfk foreign key (role_id) references role(id) on delete restrict);");

            createDefaultRoles();

            // Update users table
            statement.execute("ALTER TABLE users ADD COLUMN default_role integer;");
            statement.execute("ALTER TABLE users ADD constraint u_rfk foreign key (default_role) references role(id) on delete restrict;");

            statement.execute("UPDATE users SET default_role=1 WHERE user_type='ADMIN';");
            statement.execute("UPDATE users SET default_role=2 WHERE user_type='INSTRUCTOR';");
            statement.execute("UPDATE users SET default_role=4 WHERE user_type='STUDENT';");

            statement.execute("ALTER TABLE users DROP COLUMN user_type;");

            // Update user_class table
            statement.execute("ALTER TABLE user_class ADD COLUMN role_id integer;");
            statement.execute("ALTER TABLE user_class ADD constraint uc_rfk foreign key (role_id) references role(id) on delete restrict;");

            statement.execute("UPDATE user_class SET role_id=4;");

            statement.execute("UPDATE user_class SET role_id=3 WHERE is_ta;");

            statement.execute("ALTER TABLE user_class DROP COLUMN is_ta;");

            statement.execute("UPDATE user_class SET role_id=2 FROM users WHERE users.id=user_class.user_id AND users.user_type='INSTRUCTOR';");

            statement.execute("UPDATE version SET version=7;");
            connection.commit();
        } catch (Throwable e) {
            connection.rollback();
            logger.error("An error occurred while updating the database schema and the changes were rolled back");
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    public Pair<String, String> createUser(String username, String password, int roleId, boolean forceReset) throws SQLException {
        if (username == null || username.length() == 0)
            return new ImmutablePair<>(null, NetUtil.INVALID);
        if (password == null)
            password = RandomStringUtils.randomAlphabetic(16);
        try (Connection connection = getConnection();
             PreparedStatement count = connection.prepareStatement("SELECT count(*) FROM users WHERE username = ?;");
             PreparedStatement statement = connection.prepareStatement("INSERT INTO users (username, salt, password_hash, force_reset, default_role) VALUES(?, ?, ?, ?, ?);")) {
            count.setString(1, username);
            try (ResultSet rs = count.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0)
                    return new ImmutablePair<>(null, NetUtil.USER_EXISTS);
            }
            Pair<String, String> sh = DBUtils.getSaltAndHash(password);
            statement.setString(1, username);
            statement.setString(2, sh.getKey());
            statement.setString(3, sh.getValue());
            statement.setBoolean(4, forceReset);
            statement.setInt(5, roleId);
            statement.executeUpdate();
            return new ImmutablePair<>(password, NetUtil.OK);
        }
    }

    public ServerRole createRole(String name, int rank) throws SQLException {
        if (name == null)
            return null;
        try (Connection connection = getConnection();
             PreparedStatement createRole = connection.prepareStatement("INSERT INTO role (name, role_rank) VALUES (?, ?) RETURNING id;")) {
            createRole.setString(1, name);
            createRole.setInt(2, rank);
            try (ResultSet rs = createRole.executeQuery()) {
                if (rs.next())
                    return new ServerRole(rs.getInt(1), name, rank);
            }
        }
        return null;
    }

    public ArrayList<Pair<String, String>> getUsers() throws SQLException {
        ArrayList<Pair<String, String>> users = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement getUsers = connection.prepareStatement("SELECT username, default_role FROM users;");
             ResultSet rs = getUsers.executeQuery()) {
            while (rs.next()) {
                users.add(new ImmutablePair<>(rs.getString(1), AssignServerMain.getServer().getPermissions().getRole(rs.getInt(2)).getName()));
            }
        }
        return users;
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
//        return DriverManager.getConnection(connectionString, user, pass);
    }

}
