package edu.rpi.aris;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class LibAris {
    public static final String NAME = "Aris";
    public static final String VERSION;
    private static final Logger logger = LogManager.getLogger(LibAris.class);

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

}
