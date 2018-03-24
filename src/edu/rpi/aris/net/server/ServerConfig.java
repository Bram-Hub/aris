package edu.rpi.aris.net.server;

import java.io.File;

public class ServerConfig {

    private static ServerConfig instance;
    private File configDir = new File(System.getProperty("user.home"), ".aris-java");

    public static ServerConfig getInstance() {
        if (instance == null)
            instance = new ServerConfig();
        return instance;
    }

    public File getConfigurationDir() {
        return configDir;
    }

    public File getLoggingDir() {
        return new File("");
    }
}
