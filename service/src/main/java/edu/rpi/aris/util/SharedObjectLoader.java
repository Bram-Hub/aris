package edu.rpi.aris.util;

import java.io.*;
import java.util.*;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.*;

public class SharedObjectLoader {
    private static final Logger log = LogManager.getLogger();
    private static final Set<String> loaded = new HashSet();

    public static boolean isLoaded(String LIB_NAME) {
        return loaded.contains(LIB_NAME);
    }
    public static void loadLib(String LIB_NAME) {
        if(!loaded.contains(LIB_NAME)) {
            __loadLib(LIB_NAME);
        }
    }

    private static String getPlatformLibrarySuffix() {
        if (SystemUtils.IS_OS_LINUX) {
            return ".so";
        } else {
            // TODO: drop a dll on windows, a .so on mac
            return null;
        }
    }

    private static void __loadLib(String LIB_NAME) {
        String suffix = getPlatformLibrarySuffix();
        if(suffix == null) {
            log.info("Loading libraries is not yet supported on the current platform");
            return;
        }
        String LIB_FILE = LIB_NAME + suffix;
        log.info("Loading " + LIB_FILE + " native library");

        File tmpFile = new File(System.getProperty("java.io.tmpdir"), LIB_FILE);
        int i = 0;
        while (tmpFile.exists() && !tmpFile.delete())
            tmpFile = new File(System.getProperty("java.io.tmpdir"), LIB_NAME + (i++) + ".so");
        boolean copied = false;
        try (InputStream in = ClassLoader.getSystemResourceAsStream(LIB_FILE);
             FileOutputStream out = new FileOutputStream(tmpFile)) {
            if (in != null) {
                IOUtils.copy(in, out);
                copied = true;
            }
        } catch (IOException e) {
            copied = false;
            log.error("Failed to extract " + LIB_NAME + " to temp directory", e);
        }
        if (copied) {
            try {
                System.load(tmpFile.getCanonicalPath());
                loaded.add(LIB_NAME);
            } catch (Exception e) {
                log.error("Failed to load native " + LIB_NAME + " library", e);
            }
        }
    }
}
