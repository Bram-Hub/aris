package edu.rpi.aris;

import edu.rpi.aris.rules.RuleList;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class ConfigurationManager {

    public static final String[] SYMBOL_BUTTONS = new String[]{"∧", "∨", "¬", "→", "↔", "⊥", "∀", "∃", "×", "≠", "⊆", "∈"};
    public static final File CONFIG_DIR = new File(System.getProperty("user.home"), ".aris-java");
    private static final HashMap<String, String> KEY_MAP = new HashMap<>();
    private static final String[][] defaultKeyMap = new String[][]{{"&", "∧"}, {"|", "∨"}, {"!", "≠"}, {"~", "¬"}, {"$", "→"}, {"%", "↔"}, {"^", "⊥"}, {"@", "∀"}, {"#", "∃"}, {"*", "×"}};
    private static final ConfigurationManager configManager;
    private static final Logger logger = LogManager.getLogger(ConfigurationManager.class);

    static {
        for (String[] s : defaultKeyMap)
            KEY_MAP.put(s[0], s[1]);
        configManager = new ConfigurationManager();
        if (!CONFIG_DIR.exists()) {
            if (!CONFIG_DIR.mkdirs())
                logger.error("Failed to create configuration directory");
            Path path = CONFIG_DIR.toPath();
            try {
                Object hidden = Files.getAttribute(path, "dos:hidden", LinkOption.NOFOLLOW_LINKS);
                if (hidden != null && hidden instanceof Boolean && !((Boolean) hidden))
                    Files.setAttribute(path, "dos:hidden", Boolean.TRUE, LinkOption.NOFOLLOW_LINKS);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public final SimpleBooleanProperty hideRulesPanel = new SimpleBooleanProperty(false);
    public final SimpleBooleanProperty hideOperatorsPanel = new SimpleBooleanProperty(false);
    public final SimpleStringProperty username = new SimpleStringProperty(null);
    public final SimpleIntegerProperty selectedCourseId = new SimpleIntegerProperty(0);
    public final SimpleObjectProperty<KeyCombination> newProofLineKey = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+A"));
    public final SimpleObjectProperty<KeyCombination> deleteProofLineKey = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+D"));
    public final SimpleObjectProperty<KeyCombination> newPremiseKey = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+R"));
    public final SimpleObjectProperty<KeyCombination> startSubProofKey = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+P"));
    public final SimpleObjectProperty<KeyCombination> endSubProofKey = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+E"));
    public final SimpleObjectProperty<KeyCombination> verifyLineKey = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+F"));
    public final SimpleObjectProperty<KeyCombination> addGoalKey = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+G"));
    public final SimpleObjectProperty<KeyCombination> verifyProofKey = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+Shift+F"));
    public final SimpleObjectProperty<KeyCombination> newProofKey = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+N"));
    public final SimpleObjectProperty<KeyCombination> openProofKey = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+O"));
    public final SimpleObjectProperty<KeyCombination> saveProofKey = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+S"));
    public final SimpleObjectProperty<KeyCombination> saveAsProofKey = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+Shift+S"));
    public final SimpleObjectProperty<KeyCombination> undoKey = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+Z"));
    public final SimpleObjectProperty<KeyCombination> redoKey = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+Y"));
    public final SimpleObjectProperty<KeyCombination> copyKey = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+C"));
    public final SimpleObjectProperty<KeyCombination> cutKey = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+X"));
    public final SimpleObjectProperty<KeyCombination> pasteKey = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+V"));
    private File saveDirectory = new File(System.getProperty("user.home"));
    private String accessToken = null;
    private SimpleObjectProperty[] accelerators = new SimpleObjectProperty[]{newProofLineKey, deleteProofLineKey,
            startSubProofKey, endSubProofKey, newPremiseKey, verifyLineKey, addGoalKey, verifyProofKey, newProofKey,
            openProofKey, saveProofKey, saveAsProofKey, undoKey, redoKey, copyKey, cutKey, pasteKey};

    private ConfigurationManager() {
    }

    public static ConfigurationManager getConfigManager() {
        return configManager;
    }

    public static String replaceText(String text) {
        for (int i = 0; i < text.length(); ++i) {
            String replace;
            if ((replace = KEY_MAP.get(String.valueOf(text.charAt(i)))) != null)
                text = text.substring(0, i) + replace + text.substring(i + 1);
        }
        return text;
    }

    public boolean ignore(KeyEvent event) {
        for (SimpleObjectProperty a : accelerators)
            if (((KeyCombination) a.get()).match(event))
                return true;
        return false;
    }

    public List<RuleList> getDefaultRuleSet() {
        return Arrays.asList(RuleList.values());
    }

    public File getSaveDirectory() {
        if (!saveDirectory.exists())
            saveDirectory = new File(System.getProperty("user.home"));
        return saveDirectory;
    }

    public void setSaveDirectory(File file) {
        if (file == null)
            saveDirectory = new File(System.getProperty("user.home"));
        saveDirectory = file;
    }

    public synchronized String getAccessToken() {
        return accessToken;
    }

    public synchronized void setAccessToken(String token) {
        accessToken = token;
    }

}
