package edu.rpi.aris;

import edu.rpi.aris.assign.ArisClientModule;
import edu.rpi.aris.assign.ArisServerModule;
import edu.rpi.aris.assign.ProblemConverter;
import edu.rpi.aris.assign.spi.ArisModule;
import edu.rpi.aris.proof.SaveInfoListener;
import edu.rpi.aris.proof.SaveManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;

public class LibAris implements ArisModule<LibAris> {

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

    private ArisClientModule<LibAris> clientModule = null;
    private ArisServerModule<LibAris> serverModule = null;
    private boolean loadedClient = false;
    private boolean loadedServer = false;
    private HashMap<String, String> assignProperties = new HashMap<>();

    public LibAris() {
        instance = this;
    }

    public static LibAris getInstance() {
        if (instance == null)
            new LibAris();
        return instance;
    }

    public HashMap<String, String> getProperties() {
        return assignProperties;
    }

    @NotNull
    @Override
    public String getModuleName() {
        return NAME;
    }

    @Override
    public synchronized ArisClientModule<LibAris> getClientModule() {
        if (!loadedClient) {
            loadedClient = true;
            try {
                Class<?> clazz = Class.forName("edu.rpi.aris.gui.Aris");
                //noinspection unchecked
                clientModule = (ArisClientModule<LibAris>) clazz.getMethod("getInstance").invoke(null);
            } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                logger.fatal("Failed get Aris client class", e);
                return clientModule;
            }
        }
        return clientModule;
    }

    @Override
    public synchronized ArisServerModule<LibAris> getServerModule() {
        if (!loadedServer) {
            loadedServer = true;
            try {
                Class<?> clazz = Class.forName("edu.rpi.aris.server.ArisServer");
                //noinspection unchecked
                serverModule = (ArisServerModule<LibAris>) clazz.getMethod("getInstance").invoke(null);
            } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                logger.fatal("Failed get Aris client class", e);
                return serverModule;
            }
        }
        return serverModule;
    }

    @NotNull
    @Override
    public ProblemConverter<LibAris> getProblemConverter() {
        return new SaveManager((SaveInfoListener) (clientModule == null ? serverModule : clientModule));
    }

    @Override
    public void setArisProperties(@NotNull HashMap<String, String> properties) {
        this.assignProperties = properties;
    }

    @NotNull
    @Override
    public InputStream getModuleIcon() {
        return LibAris.class.getResourceAsStream("aris.png");
    }

    @NotNull
    @Override
    public ArrayList<String> getProblemFileExtensions() {
        ArrayList<String> list = new ArrayList<>();
        list.add(SaveManager.FILE_EXTENSION);
        list.add(SaveManager.FITCH_FILE_EXT);
        return list;
    }
}
