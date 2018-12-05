package edu.rpi.aris.assign.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SrvProp {

    private static final Logger log = LogManager.getLogger();
    private final DatabaseManager db;
    private final String key;
    private String value;

    public SrvProp(DatabaseManager db, String key, String defaultValue) {
        this.db = db;
        this.key = key;
        getInitValue(defaultValue);
    }

    private synchronized void getInitValue(String defaultValue) {
        try (Connection connection = db.getConnection();
             PreparedStatement select = connection.prepareStatement("SELECT value FROM property WHERE key = ?;");
             PreparedStatement insert = connection.prepareStatement("INSERT INTO property (key, value) VALUES (?, ?);")) {
            select.setString(1, key);
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    value = rs.getString(1);
                } else {
                    insert.setString(1, key);
                    insert.setString(2, defaultValue);
                    insert.executeUpdate();
                    value = defaultValue;
                }
            }
        } catch (SQLException e) {
            log.error("An error occurred while attempting to initialize server property \"" + key + "\"", e);
            value = defaultValue;
        }
    }

    public String getValue() {
        return value;
    }

    public synchronized void setValue(String value) {
        try (Connection connection = db.getConnection();
             PreparedStatement update = connection.prepareStatement("INSERT INTO property (key, value) VALUES (?, ?) ON CONFLICT (key) DO UPDATE SET value = ? WHERE key = ?;")) {
            update.setString(1, key);
            update.setString(2, value);
            update.setString(3, value);
            update.setString(4, key);
            update.executeUpdate();
            this.value = value;
        } catch (SQLException e) {
            log.error("An error occurred while attempting to update server property \"" + key + "\"", e);
        }
    }

}
