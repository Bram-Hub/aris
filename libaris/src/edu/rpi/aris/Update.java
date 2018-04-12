package edu.rpi.aris;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import edu.rpi.aris.net.NetUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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

    private static final int UPDATE_EXIT_CODE = 52;

    private static final Logger logger = LogManager.getLogger(Update.class);
    private Stream updateStream;
    private JsonObject releaseData;
    private String updateVersion;

    public Update(Stream updateStream) {
        Objects.requireNonNull(updateStream);
        this.updateStream = updateStream;
    }

    public boolean checkUpdate() {
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

    private String getAssetUrl(String assetName) {
        JsonArray array = releaseData.get("assets").getAsJsonArray();
        if (array == null)
            return null;
        for (int i = 0; i < array.size(); ++i) {
            JsonObject asset = array.get(i).getAsJsonObject();
            if (asset.get("name").getAsString().equals(assetName))
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

    public boolean update() {
        if (releaseData == null)
            return false;
        if (guessDevEnvironment()) {
            logger.warn("Aris appears to be running in a development environment so automatic updating has been disabled");
            return false;
        }
        logger.info("Starting update");
        logger.info("Getting download list");
        String jarUrl = getAssetUrl(updateStream.assetName);
        String extraUrl = getAssetUrl(updateStream.extraName);
        if (jarUrl == null || extraUrl == null)
            return false;
        try {
            HashMap<String, String> libs = getLibs();
            FileUtils.deleteQuietly(UPDATE_DOWNLOAD_DIR);
            if (!UPDATE_DOWNLOAD_DIR.mkdirs()) {
                logger.error("Failed to create temporary download directory");
                return false;
            }
            downloadFile(jarUrl, new File(UPDATE_DOWNLOAD_DIR, updateStream.assetName));
            File extraZip = new File(UPDATE_DOWNLOAD_DIR, updateStream.extraName);
            downloadFile(extraUrl, extraZip);
            if (!extraZip.exists())
                throw new IOException("Failed to download extras zip");
            unzipFile(extraZip);
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

    private void unzipFile(File file) throws IOException {
        try (ZipFile zipFile = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File entryDestination = new File(Update.UPDATE_DOWNLOAD_DIR, entry.getName());
                if (entry.isDirectory()) {
                    if (!entryDestination.exists() && !entryDestination.mkdirs())
                        throw new IOException("Failed to unzip file: " + file.getCanonicalPath());
                } else {
                    if (!entryDestination.getParentFile().exists() && !entryDestination.getParentFile().mkdirs())
                        throw new IOException("Failed to unzip file: " + file.getCanonicalPath());
                    try (InputStream in = zipFile.getInputStream(entry);
                         OutputStream out = new FileOutputStream(entryDestination)) {
                        IOUtils.copy(in, out);
                    }
                }
            }
        }
    }

    public void exit() {
        System.exit(UPDATE_EXIT_CODE);
    }

    public enum Stream {
        CLIENT("aris-client.jar", "client-update.zip"),
        SERVER("aris-server.jar", "server-update.zip");

        public final String assetName;
        public final String extraName;

        Stream(String assetName, String extraName) {
            this.assetName = assetName;
            this.extraName = extraName;
        }

    }

}
