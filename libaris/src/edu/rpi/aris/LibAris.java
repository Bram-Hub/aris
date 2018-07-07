package edu.rpi.aris;

import edu.rpi.aris.assign.ArisClientModule;
import edu.rpi.aris.assign.ArisModuleException;
import edu.rpi.aris.assign.ArisServerModule;
import edu.rpi.aris.assign.ProblemConverter;
import edu.rpi.aris.assign.spi.ArisModule;
import edu.rpi.aris.proof.SaveInfoListener;
import edu.rpi.aris.proof.SaveManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

public class LibAris implements ArisModule {

    public static final String NAME = "Aris";
    public static final String VERSION;
    private static final Logger logger = LogManager.getLogger(LibAris.class);
    private static LibAris instance;

    static {
        BufferedReader reader = new BufferedReader(new InputStreamReader(LibAris.class.getResourceAsStream("VERSION")));
        String version = "UNKNOWN";
        try {
            version = reader.readLine();
            reader.close();
        } catch (IOException e) {
            logger.error("An error occurred while attempting to read the version", e);
        }
        VERSION = version;
    }

    private ArisClientModule clientModule = null;
    private ArisServerModule serverModule = null;
    private boolean loadedClient = false;
    private boolean loadedServer = false;
    private HashMap<String, String> assignProperties = new HashMap<>();

    public LibAris() {
        instance = this;
    }

    public static LibAris getInstance() {
        return instance;
    }

    public HashMap<String, String> getProperties() {
        return assignProperties;
    }

    @Override
    public String getModuleName() {
        return NAME;
    }

    @Override
    public synchronized ArisClientModule getClientModule() {
        if (!loadedClient) {
            loadedClient = true;
            try {
                Class<?> clazz = Class.forName("edu.rpi.aris.gui.Aris");
                clientModule = (ArisClientModule) clazz.getMethod("getInstance").invoke(null);
            } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                logger.fatal("Failed get Aris client class", e);
                return clientModule;
            }
        }
        return clientModule;
    }

    @Override
    public synchronized ArisServerModule getServerModule() {
        return serverModule;
    }

    @Override
    public ProblemConverter getProblemConverter() throws ArisModuleException {
        return new SaveManager((SaveInfoListener) clientModule);
    }

    @Override
    public void setArisProperties(HashMap<String, String> properties) {
        this.assignProperties = properties;
    }
}
