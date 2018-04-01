package edu.rpi.aris.net.server;

import org.apache.commons.lang3.SystemUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

public class ServerConfig {

    private static final String STORAGE_CONFIG = "storage-dir";
    private static final String LOG_CONFIG = "logfile-base";
    private static final String DATABASE_NAME_CONFIG = "db-name";
    private static final String DATABASE_USER_CONFIG = "db-user";
    private static final String DATABASE_PASS_CONFIG = "db-pass";
    private static final String DATABASE_HOST_CONFIG = "db-host";
    private static final String DATABASE_PORT_CONFIG = "db-port";

    private static ServerConfig instance;
    private File configFile = new File(System.getProperty("user.home"), "aris.cfg");
    private File storageDir, baseLogFile;
    private String dbHost, dbName, dbUser, dbPass;
    private int dbPort;
    private HashMap<String, String> configOptions = new HashMap<>();

    private ServerConfig() throws IOException {
        if (SystemUtils.IS_OS_LINUX || SystemUtils.IS_OS_MAC)
            configFile = new File("/etc/aris.cfg");
        load();
    }

    public static ServerConfig getInstance() throws IOException {
        if (instance == null)
            instance = new ServerConfig();
        return instance;
    }

    private void load() throws IOException {
        configOptions.clear();
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("#"))
                    line = line.substring(0, line.indexOf('#'));
                line = line.trim();
                if (line.length() <= 0)
                    continue;
                if (!line.contains(" "))
                    throw new IOException("Invalid config line: " + line);
                String key = line.substring(0, line.indexOf(' '));
                String value = line.substring(line.indexOf(' ') + 1);
                configOptions.put(key, value);
            }
            storageDir = new File(getConfigOption(STORAGE_CONFIG, null));
            baseLogFile = new File(getConfigOption(LOG_CONFIG, null));
            dbName = getConfigOption(DATABASE_NAME_CONFIG, "aris");
            dbUser = getConfigOption(DATABASE_USER_CONFIG, "aris");
            dbPass = getConfigOption(DATABASE_PASS_CONFIG, null);
            dbHost = getConfigOption(DATABASE_HOST_CONFIG, "localhost");
            String portStr = getConfigOption(DATABASE_PORT_CONFIG, "5432");
            try {
                dbPort = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid server port: " + portStr);
            }
            if (dbPort <= 0 || dbPort > 65535)
                throw new IOException("Invalid server port: " + dbPort);
        }
    }

    private String getConfigOption(String key, String def) throws IOException {
        String option = configOptions.remove(key);
        if (option == null && def == null)
            throw new IOException("Configuration (" + configFile.getCanonicalPath() + ") missing option: " + key);
        return option == null ? def : option;
    }

    public File getConfigurationFile() {
        return configFile;
    }

    public File getStorageDir() {
        return storageDir;
    }

    public File getBaseLogfile() {
        return baseLogFile;
    }

    public String getDbName() {
        return dbName;
    }

    public String getDbHost() {
        return dbHost;
    }

    public String getDbUser() {
        return dbUser;
    }

    public String getDbPass() {
        return dbPass;
    }

    public int getDbPort() {
        return dbPort;
    }
}
