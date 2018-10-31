package edu.rpi.aris.assign.client.model;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class OfflineDB {

    private static final String DB_FILENAME = "offline.db";
    private static final Logger log = LogManager.getLogger();

    private static final OfflineDB instance;

    static {
        OfflineDB tmp = null;
        try {
            tmp = new OfflineDB();
        } catch (ClassNotFoundException e) {
            log.error("Failed to load sqlite jdbc library", e);
        } catch (SQLException | IOException e) {
            log.error("Failed to open connection to sqlite file", e);
        }
        instance = tmp;
    }

    private String connectionStr;

    private OfflineDB() throws ClassNotFoundException, SQLException, IOException {
        log.info("Loading SQLite for offline database");
        Class.forName("org.sqlite.JDBC");
        File file = new File(LocalConfig.CLIENT_STORAGE_DIR, DB_FILENAME);
        connectionStr = "jdbc:sqlite:" + file.getCanonicalPath();
        try (Connection connection = DriverManager.getConnection(connectionStr)) {
            connection.setAutoCommit(false);
            try {
                checkTables(connection);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    public static boolean loaded() {
        return instance != null;
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(instance.connectionStr);
    }

    private void checkTables(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS assignments" +
                    "(aid integer NOT NULL," +
                    "cid integer NOT NULL," +
                    "name text NOT NULL," +
                    "due_date text NOT NULL," +
                    "pid integer NOT NULL," +
                    "PRIMARY KEY (aid, cid, pid));");
            stmt.execute("CREATE TABLE IF NOT EXISTS problems" +
                    "(id integer NOT NULL PRIMARY KEY," +
                    "name text NOT NULL," +
                    "module_name text NOT NULL," +
                    "data blob NOT NULL);");
            stmt.execute("CREATE TABLE IF NOT EXISTS submissions" +
                    "(sid integer NOT NULL PRIMARY KEY," +
                    "pid integer NOT NULL," +
                    "aid integer NOT NULL," +
                    "cid integer NOT NULL," +
                    "submitted_on text NOT_NULL," +
                    "module_name text NOT NULL," +
                    "data blob NOT NULL);");
            stmt.execute("CREATE TABLE IF NOT EXISTS attempts" +
                    "(aid integer NOT NULL," +
                    "cid integer NOT NULL," +
                    "pid integer NOT NULL," +
                    "created_time text NOT NULL," +
                    "module_name text NOT NULL," +
                    "data blob NOT NULL," +
                    "PRIMARY KEY (aid, cid, pid, created_time));");
            stmt.execute("CREATE TABLE IF NOT EXISTS classes" +
                    "(cid integer NOT NULL PRIMARY KEY," +
                    "name text NOT NULL);");
        }
    }

}
