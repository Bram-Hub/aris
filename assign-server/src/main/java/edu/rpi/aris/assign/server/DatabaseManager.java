package edu.rpi.aris.assign.server;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import edu.rpi.aris.assign.DBUtils;
import edu.rpi.aris.assign.GradingStatus;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.sql.*;
import java.util.ArrayList;

public class DatabaseManager {

    public static final String DEFAULT_ADMIN_PASS = "ArisAdmin1";
    private static final String[] defaultRoleName = new String[]{"Admin", "Instructor", "TA", "Student"};
    private static final int[] defaultRoleRank = new int[]{0, 1, 2, 3};
    private static final int DB_SCHEMA_VERSION = 13;
    private static Logger logger = LogManager.getLogger(DatabaseManager.class);

    static {
        if (Security.getProvider("BC") == null)
            Security.addProvider(new BouncyCastleProvider());
    }

    private ComboPooledDataSource dataSource;

    public DatabaseManager(String host, int port, String database, String user, String pass) throws IOException, SQLException {
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

    private void createDefaultRoles(Connection connection) throws SQLException {
        //noinspection ConstantConditions
        if (defaultRoleName.length != defaultRoleRank.length)
            throw new IndexOutOfBoundsException("Default role names/ranks do not match");
        for (int i = 0; i < defaultRoleName.length; ++i)
            DBUtils.createRole(defaultRoleName[i], defaultRoleRank[i], connection);
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
                                String methodName = "updateSchema" + v;
                                logger.info("Calling update method: " + methodName);
                                Method update = DatabaseManager.class.getDeclaredMethod(methodName, Connection.class);
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
                    createDefaultRoles(connection);
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
                    "(id serial NOT NULL PRIMARY KEY," +
                    "name text NOT NULL," +
                    "role_rank integer NOT NULL);");
            statement.execute("CREATE TABLE IF NOT EXISTS users" +
                    "(id serial NOT NULL PRIMARY KEY," +
                    "username text NOT NULL UNIQUE," +
                    "full_name text NOT NULL," +
                    "salt text NOT NULL," +
                    "password_hash text NOT NULL," +
                    "access_token text NOT NULL," +
                    "force_reset boolean NOT NULL," +
                    "default_role integer NOT NULL," +
                    "constraint u_rfk foreign key (default_role) references role(id) on delete restrict);");
            statement.execute("CREATE TABLE IF NOT EXISTS class" +
                    "(id serial NOT NULL PRIMARY KEY," +
                    "name text NOT NULL);");
            statement.execute("CREATE TABLE IF NOT EXISTS user_class" +
                    "(user_id integer NOT NULL," +
                    "class_id integer NOT NULL," +
                    "role_id integer NOT NULL," +
                    "PRIMARY KEY(user_id, class_id)," +
                    "constraint uc_ufk foreign key (user_id) references users(id) on delete cascade," +
                    "constraint uc_cfk foreign key (class_id) references class(id) on delete cascade," +
                    "constraint uc_rfk foreign key (role_id) references role(id) on delete restrict);");
            statement.execute("CREATE TABLE IF NOT EXISTS problem" +
                    "(id serial NOT NULL PRIMARY KEY," +
                    "name text NOT NULL," +
                    "data bytea NOT NULL," +
                    "created_by text NOT NULL," +
                    "created_on timestamp NOT NULL," +
                    "module_name text NOT NULL," +
                    "problem_hash text NOT NULL);");
            statement.execute("CREATE TABLE IF NOT EXISTS assignment" +
                    "(id integer NOT NULL," +
                    "class_id integer NOT NULL," +
                    "problem_id integer NOT NULL," +
                    "name text NOT NULL," +
                    "due_date timestamp NOT NULL," +
                    "assigned_by integer NULL," +
                    "PRIMARY KEY(id, class_id, problem_id)," +
                    "constraint a_cfk foreign key (class_id) references class(id) on delete cascade," +
                    "constraint a_pfk foreign key (problem_id) references problem(id) on delete cascade," +
                    "constraint a_abfk foreign key (assigned_by) references users(id) on delete set NULL);");
            statement.execute("CREATE TABLE IF NOT EXISTS submission" +
                    "(id serial NOT NULL PRIMARY KEY," +
                    "class_id integer NOT NULL," +
                    "assignment_id integer NOT NULL," +
                    "user_id integer NOT NULL," +
                    "problem_id integer NOT NULL," +
                    "data bytea NOT NULL," +
                    "time timestamp NOT NULL," +
                    "short_status text NOT NULL," +
                    "status text NOT NULL," +
                    "grade real NOT NULL," +
                    "constraint submission_short_status_check check (short_status in ('" + GradingStatus.CORRECT.name() + "', '" + GradingStatus.INCORRECT.name() + "', '" + GradingStatus.GRADING.name() + "', '" + GradingStatus.PARTIAL.name() + "'))," +
                    "constraint s_cufk foreign key (user_id, class_id) references user_class(user_id, class_id) on delete cascade," +
                    "constraint s_afk foreign key (assignment_id, class_id, problem_id) references assignment(id, class_id, problem_id) on delete cascade," +
                    "constraint s_pfk foreign key (problem_id) references problem(id) on delete cascade);");
            statement.execute("CREATE TABLE IF NOT EXISTS permissions" +
                    "(name text NOT NULL PRIMARY KEY," +
                    "role_id integer NOT NULL," +
                    "constraint perm_rfk foreign key (role_id) references role(id) on delete restrict);");
            createDefaultRoles(connection);
            DBUtils.createUser(connection, "admin", DEFAULT_ADMIN_PASS, "Admin", 1, true);
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

            createDefaultRoles(connection);

            // Update users table
            statement.execute("ALTER TABLE users ADD COLUMN default_role integer;");
            statement.execute("ALTER TABLE users ADD constraint u_rfk foreign key (default_role) references role(id) on delete restrict;");

            statement.execute("UPDATE users SET default_role=1 WHERE user_type='ADMIN';");
            statement.execute("UPDATE users SET default_role=2 WHERE user_type='INSTRUCTOR';");
            statement.execute("UPDATE users SET default_role=4 WHERE user_type='STUDENT';");

            // Update user_class table
            statement.execute("ALTER TABLE user_class ADD COLUMN role_id integer;");
            statement.execute("ALTER TABLE user_class ADD constraint uc_rfk foreign key (role_id) references role(id) on delete restrict;");

            statement.execute("UPDATE user_class SET role_id=4;");

            statement.execute("UPDATE user_class SET role_id=3 WHERE is_ta;");

            statement.execute("ALTER TABLE user_class DROP COLUMN is_ta;");

            statement.execute("UPDATE user_class SET role_id=2 FROM users WHERE users.id=user_class.user_id AND users.user_type='INSTRUCTOR';");

            statement.execute("ALTER TABLE users DROP COLUMN user_type;");

            statement.execute("UPDATE version SET version=7;");
            connection.commit();
        } catch (Throwable e) {
            connection.rollback();
            logger.error("An error occurred while updating the database schema and the changes were rolled back");
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
        updateSchema7(connection);
    }

    private void updateSchema7(Connection connection) throws SQLException {
        logger.info("Updating database schema to version 8");
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE submission DROP CONSTRAINT s_cfk;");
            statement.execute("ALTER TABLE submission DROP CONSTRAINT s_ufk;");

            statement.execute("ALTER TABLE user_class ADD PRIMARY KEY (user_id, class_id);");

            statement.execute("ALTER TABLE submission ADD CONSTRAINT s_cufk foreign key (user_id, class_id) references user_class(user_id, class_id) on delete cascade;");

            statement.execute("UPDATE version SET version=8;");
            connection.commit();
        } catch (Throwable e) {
            connection.rollback();
            logger.error("An error occurred while updating the database schema and the changes were rolled back");
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
        updateSchema8(connection);
    }

    private void updateSchema8(Connection connection) throws SQLException {
        logger.info("Updating database schema to version 9");
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE users ADD COLUMN full_name text;");
            statement.execute("UPDATE users SET full_name=username;");

            statement.execute("UPDATE version SET version=9;");
            connection.commit();
        } catch (Throwable e) {
            connection.rollback();
            logger.error("An error occurred while updating the database schema and the changes were rolled back");
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
        updateSchema9(connection);
    }

    private void updateSchema9(Connection connection) throws SQLException {
        logger.info("Updating database schema to version 10");
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE users ADD UNIQUE (username);");

            statement.execute("UPDATE version SET version=10;");
            connection.commit();
        } catch (Throwable e) {
            connection.rollback();
            logger.error("An error occurred while updating the database schema and the changes were rolled back");
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
        updateSchema10(connection);
    }

    private void updateSchema10(Connection connection) throws SQLException {
        logger.info("Updating database schema to version 11");
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE problem ADD COLUMN problem_hash text;");
            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                throw new SQLException("Failed to add problem hash column", e);
            }
            try (ResultSet rs = statement.executeQuery("SELECT id, data FROM problem;");
                 PreparedStatement updateHash = connection.prepareStatement("UPDATE problem SET problem_hash=? WHERE id=?;")) {
                byte[] buffer = new byte[1024];
                int read;
                while (rs.next()) {
                    int id = rs.getInt(1);
                    try (InputStream in = rs.getBinaryStream(2)) {
                        while ((read = in.read(buffer)) >= 0) {
                            digest.update(buffer, 0, read);
                        }
                        updateHash.setString(1, Hex.toHexString(digest.digest()));
                        updateHash.setInt(2, id);
                        updateHash.addBatch();
                    } catch (IOException e) {
                        throw new SQLException("Failed to read problem for hashing", e);
                    }
                }
                updateHash.executeBatch();
            }
            statement.execute("ALTER TABLE problem ALTER COLUMN problem_hash SET NOT NULL;");
            statement.execute("UPDATE version SET version=11;");
            connection.commit();
        } catch (Throwable e) {
            connection.rollback();
            logger.error("An error occurred while updating the database schema and the changes were rolled back");
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
        updateSchema11(connection);
    }

    private void updateSchema11(Connection connection) throws SQLException {
        logger.info("Updating database schema to version 12");
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE submission ADD COLUMN grade real;");
            statement.execute("ALTER TABLE submission DROP CONSTRAINT submission_short_status_check;");
            statement.execute("ALTER TABLE submission ADD CONSTRAINT submission_short_status_check check (short_status in ('" + GradingStatus.CORRECT.name() + "', '" + GradingStatus.INCORRECT.name() + "', '" + GradingStatus.GRADING.name() + "', '" + GradingStatus.PARTIAL.name() + "'));");
            statement.execute("UPDATE version SET version=12;");
            connection.commit();
        } catch (Throwable e) {
            connection.rollback();
            logger.error("An error occurred while updating the database schema and the changes were rolled back");
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
        updateSchema12(connection);
    }

    private void updateSchema12(Connection connection) throws SQLException {
        logger.info("Updating database schema to version 13");
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE submission DROP CONSTRAINT submission_short_status_check;");
            statement.execute("ALTER TABLE submission ADD CONSTRAINT submission_short_status_check check (short_status in ('" + GradingStatus.CORRECT.name() + "', '" + GradingStatus.INCORRECT.name() + "', '" + GradingStatus.GRADING.name() + "', '" + GradingStatus.PARTIAL.name() + "', '" + GradingStatus.ERROR.name() + "'));");
            statement.execute("UPDATE version SET version=13;");
            connection.commit();
        } catch (Throwable e) {
            connection.rollback();
            logger.error("An error occurred while updating the database schema and the changes were rolled back");
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    public Pair<String, Integer> createUser(String username, String password, String fullName, int roleId, boolean forceReset) throws SQLException {
        try (Connection connection = getConnection()) {
            return DBUtils.createUser(connection, username, password, fullName, roleId, forceReset);
        }
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
    }

}
