package edu.rpi.aris.assign.client.model;

import edu.rpi.aris.assign.NamedThreadFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class OfflineDB {

    private static final String DB_FILENAME = "offline.db";
    private static final Logger log = LogManager.getLogger();

    private static final OfflineDB instance;
    private static final ThreadPoolExecutor threadPool = (ThreadPoolExecutor) Executors.newCachedThreadPool(new NamedThreadFactory("Offline Database Thread", true));

    static {
        OfflineDB tmp = null;
        try {
            tmp = new OfflineDB();
        } catch (ClassNotFoundException e) {
            log.error("Failed to load H2 jdbc library", e);
        } catch (SQLException | IOException e) {
            log.error("Failed to open connection to H2 file", e);
        }
        instance = tmp;
    }

    private String connectionStr;

    private OfflineDB() throws ClassNotFoundException, SQLException, IOException {
        log.info("Loading H2 for offline database");
        Class.forName("org.h2.Driver");
        File file = new File(LocalConfig.CLIENT_STORAGE_DIR, DB_FILENAME);
        connectionStr = "jdbc:h2:" + file.getCanonicalPath();
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

    private static boolean loaded() {
        return instance != null;
    }

    public static void submit(Consumer<Connection> runnable, boolean wait) {
        AtomicBoolean doWait = new AtomicBoolean(wait);
        threadPool.submit(() -> {
            try {
                if (!loaded()) {
                    runnable.accept(null);
                    return;
                }
                try {
                    Connection connection = DriverManager.getConnection(instance.connectionStr);
                    try {
                        connection.setAutoCommit(false);
                        runnable.accept(connection);
                        connection.commit();
                    } catch (Exception e) {
                        log.error("Offline DB Error", e);
                        connection.rollback();
                    }
                } catch (SQLException e) {
                    log.error("Failed to get DB connection", e);
                }
            } finally {
                if (doWait.get())
                    synchronized (runnable) {
                        doWait.set(false);
                        runnable.notifyAll();
                    }
            }
        });
        synchronized (runnable) {
            while (doWait.get()) {
                try {
                    runnable.wait();
                } catch (InterruptedException e) {
                    log.error(e);
                }
            }
        }
    }

    public static void submit(Consumer<Connection> runnable) {
        submit(runnable, false);
    }

    private void checkTables(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS classes" +
                    "(cid integer NOT NULL PRIMARY KEY," +
                    "name varchar NOT NULL);");
            stmt.execute("CREATE TABLE IF NOT EXISTS problems" +
                    "(id integer NOT NULL PRIMARY KEY," +
                    "name varchar NOT NULL," +
                    "module_name varchar NOT NULL," +
                    "problem_hash varchar NOT NULL," +
                    "data blob NOT NULL);");
            stmt.execute("CREATE TABLE IF NOT EXISTS assignments" +
                    "(aid integer NOT NULL," +
                    "cid integer NOT NULL," +
                    "name varchar NOT NULL," +
                    "due_date timestamp WITH TIME ZONE NOT NULL," +
                    "pid integer NOT NULL," +
                    "PRIMARY KEY (aid, cid, pid));");
            stmt.execute("CREATE TABLE IF NOT EXISTS submissions" +
                    "(sid integer NOT NULL PRIMARY KEY," +
                    "pid integer NOT NULL," +
                    "aid integer NOT NULL," +
                    "cid integer NOT NULL," +
                    "submitted_on timestamp WITH TIME ZONE NOT NULL," +
                    "module_name varchar NOT NULL," +
                    "data blob NOT NULL);");
            stmt.execute("CREATE TABLE IF NOT EXISTS attempts" +
                    "(aid integer NOT NULL," +
                    "cid integer NOT NULL," +
                    "pid integer NOT NULL," +
                    "created_time timestamp WITH TIME ZONE NOT NULL," +
                    "module_name varchar NOT NULL," +
                    "data blob NOT NULL," +
                    "PRIMARY KEY (aid, cid, pid, created_time));");
        }
    }

}
