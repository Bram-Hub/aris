package edu.rpi.aris;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import edu.rpi.aris.net.NetUtil;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Update {

    private static final String RELEASE_CHECK_URL = "https://api.github.com/repos/cicchr/ARIS-Java/releases/latest";
    private static final String REPO_BASE_URL = "https://raw.githubusercontent.com/cicchr/ARIS-Java/";
    private static final String MAVEN_BASE_URL = "http://central.maven.org/maven2/";
    private static final String LIB_ARIS_LIBS_LOC = "/libaris/libaris.iml";
    private static final String CLIENT_LIBS_LOC = "/client/client.iml";
    private static final String SERVER_LIBS_LOC = "/server/server.iml";
    private static final String LIBRARY_LINE_ID = "type=\"library\"";
    private static final File UPDATE_DOWNLOAD_DIR = new File(System.getProperty("java.io.tmpdir"), "aris-update");
    private static final Pattern LIB_PATTERN = Pattern.compile("(?<=name=\").*?(?=\")");

    private static final Logger logger = LogManager.getLogger(Update.class);
    private Stream updateStream;
    private JsonObject releaseData;
    private String updateVersion;

    private Update(Stream updateStream) {
        Objects.requireNonNull(updateStream);
        this.updateStream = updateStream;
    }

    private boolean checkUpdate() {
        try {
            logger.info("Checking for update");
            URL releaseUrl = new URL(RELEASE_CHECK_URL);
            try (InputStream in = releaseUrl.openStream();
                 InputStreamReader reader = new InputStreamReader(in)) {
                releaseData = new JsonParser().parse(reader).getAsJsonObject();
                JsonElement tagElement = releaseData.get("tag_name");
                if (tagElement == null)
                    return false;
                updateVersion = tagElement.getAsString();
                logger.info("Current version: " + LibAris.VERSION);
                logger.info("Latest version:  " + updateVersion);
                if (NetUtil.versionCompare(LibAris.VERSION, updateVersion) < 0) {
                    logger.info("Update available");
                    return true;
                } else
                    logger.info("No update available");
            }
        } catch (IOException e) {
            logger.error("Failed to check for update", e);
        }
        return false;
    }

    private String getArisJar() {
        JsonArray array = releaseData.get("assets").getAsJsonArray();
        if (array == null)
            return null;
        for (int i = 0; i < array.size(); ++i) {
            JsonObject asset = array.get(i).getAsJsonObject();
            if (asset.get("name").getAsString().equals(updateStream.assetName))
                return asset.get("browser_download_url").getAsString();
        }
        return null;
    }

    private void getLibs(String urlStr, HashMap<String, String> set) throws IOException {
        URL url = new URL(urlStr);
        try (InputStream is = url.openStream();
             InputStreamReader isr = new InputStreamReader(is);
             BufferedReader reader = new BufferedReader(isr)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.contains(LIBRARY_LINE_ID))
                    continue;
                Matcher m = LIB_PATTERN.matcher(line);
                if (!m.find())
                    continue;
                line = m.group();
                String[] split = line.split(":");
                if (split.length != 3)
                    throw new IOException("Invalid library list in remote repository");
                String groupId = split[0].replaceAll("\\.", "/");
                String artifactId = split[1];
                String version = split[2];
                String name = artifactId + "-" + version + ".jar";
                set.put(name, MAVEN_BASE_URL + groupId + "/" + artifactId + "/" + version + "/" + name);
            }
        }
    }

    private HashMap<String, String> getLibs() throws IOException {
        HashMap<String, String> libs = new HashMap<>();
        getLibs(REPO_BASE_URL + updateVersion + LIB_ARIS_LIBS_LOC, libs);
        getLibs(REPO_BASE_URL + updateVersion + (updateStream == Stream.CLIENT ? CLIENT_LIBS_LOC : SERVER_LIBS_LOC), libs);
        return libs;
    }

    private boolean guessDevEnvironment() {
        return !Update.class.getResource(Update.class.getSimpleName() + ".class").toString().toLowerCase().startsWith("jar:");
    }

    private void downloadFile(String urlStr, File destination) throws IOException {
        logger.info("Downloading: " + urlStr);
        URL url = new URL(urlStr);
        FileUtils.copyURLToFile(url, destination, 10000, 10000);
    }

    private boolean update() {
        if (releaseData == null)
            return false;
        if (guessDevEnvironment()) {
            logger.warn("Aris appears to be running in a development environment so automatic updating has been disabled");
            return false;
        }
        logger.info("Starting update");
        logger.info("Getting download list");
        String jarUrl = getArisJar();
        if (jarUrl == null)
            return false;
        try {
            HashMap<String, String> libs = getLibs();
            FileUtils.deleteQuietly(UPDATE_DOWNLOAD_DIR);
            if (!UPDATE_DOWNLOAD_DIR.mkdirs()) {
                logger.error("Failed to create temporary download directory");
                return false;
            }
            downloadFile(jarUrl, new File(UPDATE_DOWNLOAD_DIR, updateStream.assetName));
            File libDir = new File(UPDATE_DOWNLOAD_DIR, "lib");
            for (Map.Entry<String, String> lib : libs.entrySet())
                downloadFile(lib.getValue(), new File(libDir, lib.getKey()));
            logger.info("Download complete");
            return true;
        } catch (IOException e) {
            logger.error("Failed to update aris", e);
            FileUtils.deleteQuietly(UPDATE_DOWNLOAD_DIR);
            return false;
        }
    }

    public enum Stream {
        CLIENT("aris-client.jar"),
        SERVER("aris-server.jar");

        public final String assetName;

        Stream(String assetName) {
            this.assetName = assetName;
        }

    }

}
