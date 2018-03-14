package edu.rpi.aris.net.server;

import edu.rpi.aris.Main;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.HashSet;

public class DatabaseManager {

    private static final String[] tables = new String[]{"submission", "assignment", "proof", "user", "class"};
    private static Logger logger = LogManager.getLogger(DatabaseManager.class);
    private Connection connection;
    private File dbFile;
    private Thread shutdownHook;

    public DatabaseManager(File dbFile) throws IOException, SQLException {
        try {
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
            String ans = "n";
            try {
                ans = Main.SYSTEM_IN.readLine();
            } catch (IOException e) {
                logger.error("Error reading from stdin", e);
            }
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
        statement.execute("CREATE TABLE user (id integer, username text, class_id integer, user_type text, salt text, password_hash text, access_token text, PRIMARY KEY(id, class_id));");
        statement.execute("CREATE TABLE class (id integer PRIMARY KEY, name text);");
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
