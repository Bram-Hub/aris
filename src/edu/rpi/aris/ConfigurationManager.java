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
import java.util.prefs.Preferences;

public class ConfigurationManager {

    public static final String[] SYMBOL_BUTTONS = new String[]{"∧", "∨", "¬", "→", "↔", "⊥", "∀", "∃", "×", "≠", "⊆", "∈"};
    public static final File CONFIG_DIR = new File(System.getProperty("user.home"), ".aris-java");
    public static final String HIDE_RULES_PANEL = "HIDE_RULES_PANEL";
    public static final String HIDE_OPERATOR_PANEL = "HIDE_OPERATOR_PANEL";
    public static final String USERNAME_KEY = "USERNAME_KEY";
    public static final String SELECTED_COURSE_ID = "SELECTED_COURSE_ID";
    private static final HashMap<String, String> KEY_MAP = new HashMap<>();
    private static final ConfigurationManager configManager;
    private static final Logger logger = LogManager.getLogger(ConfigurationManager.class);
    public static final String LAST_SAVE_DIR = "LAST_SAVE_DIR";
    public static final String ACCESS_TOKEN = "ACCESS_TOKEN";
    private static final Preferences preferences = Preferences.userNodeForPackage(ConfigurationManager.class);
    //Java preferences api storage constants
    private static final String OR_KEY = "OR_KEY";
    private static final String NOT_EQUALS_KEY = "NOT_EQUALS_KEY";
    private static final String NOT_KEY = "NOT_KEY";
    private static final String COND_KEY = "COND_KEY";
    private static final String BICONDITIONAL_KEY = "BICONDITIONAL_KEY";
    private static final String CONTRA_KEY = "CONTRA_KEY";
    private static final String UNIVERSAL_KEY = "UNIVERSAL_KEY";
    private static final String EXISTENTIAL_KEY = "EXISTENTIAL_KEY";
    private static final String MULTIPLICATION_KEY = "MULTIPLICATION_KEY";
    private static final String PASTE_KEY = "PASTE_KEY";
    private static final String CUT_KEY = "CUT_KEY";
    private static final String COPY_KEY = "COPY_KEY";
    private static final String REDO_KEY = "REDO_KEY";
    private static final String UNDO_KEY = "UNDO_KEY";
    private static final String SAVE_AS_KEY = "SAVE_AS_KEY";
    private static final String SAVE_KEY = "SAVE_KEY";
    private static final String OPEN_PROOF_KEY = "OPEN_PROOF_KEY";
    private static final String NEW_PROOF_KEY = "NEW_PROOF_KEY";
    private static final String VERIFY_PROOF_KEY = "VERIFY_PROOF_KEY";
    private static final String ADD_GOAL_KEY = "ADD_GOAL_KEY";
    private static final String VERIFY_LINE_KEY = "VERIFY_LINE_KEY";
    private static final String END_SUB_KEY = "END_SUB_KEY";
    private static final String START_SUB_KEY = "START_SUB_KEY";
    private static final String NEW_PREMISE_KEY = "NEW_PREMISE_KEY";
    private static final String DELETE_LINE_KEY = "DELETE_LINE_KEY";
    private static final String NEW_LINE_KEY = "NEW_LINE_KEY";
    private static final String[][] defaultKeyMap = new String[][]{{"&", "∧"},
            {preferences.get(OR_KEY, "|"), "∨"},
            {preferences.get(NOT_EQUALS_KEY, "!"), "≠"},
            {preferences.get(NOT_KEY, "~"), "¬"},
            {preferences.get(COND_KEY, "$"), "→"},
            {preferences.get(BICONDITIONAL_KEY, "%"), "↔"},
            {preferences.get(CONTRA_KEY, "^"), "⊥"},
            {preferences.get(UNIVERSAL_KEY, "@"), "∀"},
            {preferences.get(EXISTENTIAL_KEY, "#"), "∃"},
            {preferences.get(MULTIPLICATION_KEY, "*"), "×"}};

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

    public final SimpleBooleanProperty hideRulesPanel = new SimpleBooleanProperty(preferences.getBoolean(HIDE_RULES_PANEL, false));
    public final SimpleBooleanProperty hideOperatorsPanel = new SimpleBooleanProperty(preferences.getBoolean(HIDE_OPERATOR_PANEL, false));
    public final SimpleStringProperty username = new SimpleStringProperty(preferences.get(USERNAME_KEY, null));
    public final SimpleIntegerProperty selectedCourseId = new SimpleIntegerProperty(preferences.getInt(SELECTED_COURSE_ID, 0));
    public final SimpleObjectProperty<KeyCombination> newProofLineKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(NEW_LINE_KEY, "Ctrl+A")));
    public final SimpleObjectProperty<KeyCombination> deleteProofLineKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(DELETE_LINE_KEY, "Ctrl+D")));
    public final SimpleObjectProperty<KeyCombination> newPremiseKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(NEW_PREMISE_KEY, "Ctrl+R")));
    public final SimpleObjectProperty<KeyCombination> startSubProofKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(START_SUB_KEY, "Ctrl+P")));
    public final SimpleObjectProperty<KeyCombination> endSubProofKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(END_SUB_KEY, "Ctrl+E")));
    public final SimpleObjectProperty<KeyCombination> verifyLineKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(VERIFY_LINE_KEY, "Ctrl+F")));
    public final SimpleObjectProperty<KeyCombination> addGoalKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(ADD_GOAL_KEY, "Ctrl+G")));
    public final SimpleObjectProperty<KeyCombination> verifyProofKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(VERIFY_PROOF_KEY, "Ctrl+Shift+F")));
    public final SimpleObjectProperty<KeyCombination> newProofKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(NEW_PROOF_KEY, "Ctrl+N")));
    public final SimpleObjectProperty<KeyCombination> openProofKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(OPEN_PROOF_KEY, "Ctrl+O")));
    public final SimpleObjectProperty<KeyCombination> saveProofKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(SAVE_KEY, "Ctrl+S")));
    public final SimpleObjectProperty<KeyCombination> saveAsProofKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(SAVE_AS_KEY, "Ctrl+Shift+S")));
    public final SimpleObjectProperty<KeyCombination> undoKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(UNDO_KEY, "Ctrl+Z")));
    public final SimpleObjectProperty<KeyCombination> redoKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(REDO_KEY, "Ctrl+Y")));
    public final SimpleObjectProperty<KeyCombination> copyKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(COPY_KEY, "Ctrl+C")));
    public final SimpleObjectProperty<KeyCombination> cutKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(CUT_KEY, "Ctrl+X")));
    public final SimpleObjectProperty<KeyCombination> pasteKey = new SimpleObjectProperty<>(KeyCombination.keyCombination(preferences.get(PASTE_KEY, "Ctrl+V")));
    private File saveDirectory = new File(preferences.get(LAST_SAVE_DIR, System.getProperty("user.home")));
    private String accessToken = preferences.get(ACCESS_TOKEN, null);
    private SimpleObjectProperty[] accelerators = new SimpleObjectProperty[]{newProofLineKey, deleteProofLineKey,
            startSubProofKey, endSubProofKey, newPremiseKey, verifyLineKey, addGoalKey, verifyProofKey, newProofKey,
            openProofKey, saveProofKey, saveAsProofKey, undoKey, redoKey, copyKey, cutKey, pasteKey};

    private ConfigurationManager() {
        if (!saveDirectory.exists())
            saveDirectory = new File(System.getProperty("user.home"));
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
        preferences.put(ACCESS_TOKEN, token);
    }

}
