package edu.rpi.aris.net.server;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

public class ServerConfig {

    private static final String STORAGE_CONFIG = "storage-dir";
    private static final String LOG_CONFIG = "logfile-base";
    private static final String CA_CONFIG = "ca";
    private static final String KEY_CONFIG = "key";
    private static final String DATABASE_NAME_CONFIG = "db-name";
    private static final String DATABASE_USER_CONFIG = "db-user";
    private static final String DATABASE_PASS_CONFIG = "db-pass";
    private static final String DATABASE_HOST_CONFIG = "db-host";
    private static final String DATABASE_PORT_CONFIG = "db-port";

    private static ServerConfig instance;
    private static Logger logger = LogManager.getLogger(ServerConfig.class);
    private File configFile = new File(System.getProperty("user.home"), "aris.cfg");
    private File storageDir, baseLogFile, caFile, keyFile;
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
        if (!configFile.exists()) {
            logger.fatal("Server configuration file does not exist: " + configFile.getCanonicalPath());
            System.exit(1);
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("#"))
                    line = line.substring(0, line.indexOf('#'));
                line = line.trim();
                if (line.length() <= 0)
                    continue;
                if (!line.contains(" ")) {
                    logger.fatal("Invalid line in config file: " + line);
                    System.exit(1);
                }
                String key = line.substring(0, line.indexOf(' '));
                String value = line.substring(line.indexOf(' ') + 1);
                configOptions.put(key, value);
            }
        }
        // required configs
        dbPass = getConfigOption(DATABASE_PASS_CONFIG, null, false);
        storageDir = new File(getConfigOption(STORAGE_CONFIG, null, false));
        baseLogFile = new File(getConfigOption(LOG_CONFIG, null, false));
        // optional configs
        String caStr = getConfigOption(CA_CONFIG, null, true);
        if (caStr != null)
            caFile = new File(caStr);
        String keyStr = getConfigOption(KEY_CONFIG, null, true);
        if (keyStr != null)
            keyFile = new File(keyStr);
        dbName = getConfigOption(DATABASE_NAME_CONFIG, "aris", true);
        dbUser = getConfigOption(DATABASE_USER_CONFIG, "aris", true);
        dbHost = getConfigOption(DATABASE_HOST_CONFIG, "localhost", true);
        String portStr = getConfigOption(DATABASE_PORT_CONFIG, "5432", true);
        try {
            dbPort = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            logger.fatal("Invalid server port: " + portStr);
            System.exit(1);
        }
        if (dbPort <= 0 || dbPort > 65535) {
            logger.fatal("Invalid server port: " + portStr);
            System.exit(1);
        }
        if (configOptions.size() > 0)
            logger.error("Unknown configuration options: " + StringUtils.join(configOptions.keySet(), ", "));
    }

    private String getConfigOption(String key, String defaultValue, boolean optional) throws IOException {
        String option = configOptions.remove(key);
        if (option == null && defaultValue == null && !optional) {
            logger.fatal("Configuration (" + configFile.getCanonicalPath() + ") missing option: " + key);
            System.exit(1);
        }
        return option == null ? defaultValue : option;
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

    public File getCaFile() {
        return caFile;
    }

    public void setCaFile(File caFile) {
        this.caFile = caFile;
    }

    public File getKeyFile() {
        return keyFile;
    }

    public void setKeyFile(File keyFile) {
        this.keyFile = keyFile;
    }
}
