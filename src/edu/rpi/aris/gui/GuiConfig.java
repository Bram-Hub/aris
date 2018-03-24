package edu.rpi.aris.gui;

import edu.rpi.aris.rules.RuleList;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.commons.collections.BidiMap;
import org.apache.commons.collections.bidimap.DualHashBidiMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;
import java.util.prefs.Preferences;

public class GuiConfig {

    public static final String[] SYMBOL_BUTTONS = new String[]{"∧", "∨", "¬", "→", "↔", "⊥", "∀", "∃", "×", "≠", "⊆", "∈"};
    private static final Logger logger = LogManager.getLogger(GuiConfig.class);
    private static final Preferences preferences = Preferences.userNodeForPackage(GuiConfig.class);
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
    private static final String HIDE_RULES_PANEL = "HIDE_RULES_PANEL";
    private static final String HIDE_OPERATOR_PANEL = "HIDE_OPERATOR_PANEL";
    private static final String USERNAME_KEY = "USERNAME_KEY";
    private static final String SELECTED_COURSE_ID = "SELECTED_COURSE_ID";
    private static final String LAST_SAVE_DIR = "LAST_SAVE_DIR";
    private static final String ACCESS_TOKEN = "ACCESS_TOKEN";
    private static final String AND_KEY = "AND_KEY";
    private static final String[][] defaultKeyMap = new String[][]{{preferences.get(AND_KEY, "&"), "∧"},
            {preferences.get(OR_KEY, "|"), "∨"},
            {preferences.get(NOT_KEY, "~"), "¬"},
            {preferences.get(COND_KEY, "$"), "→"},
            {preferences.get(BICONDITIONAL_KEY, "%"), "↔"},
            {preferences.get(CONTRA_KEY, "^"), "⊥"},
            {preferences.get(UNIVERSAL_KEY, "@"), "∀"},
            {preferences.get(EXISTENTIAL_KEY, "?"), "∃"},
            {preferences.get(NOT_EQUALS_KEY, "#"), "≠"},
            {preferences.get(MULTIPLICATION_KEY, "*"), "×"}};
    private static final HashMap<String, String> keyPreferenceMap = new HashMap<>();
    public static File CLIENT_CONFIG_DIR = new File(System.getProperty("user.home"), ".aris-java");
    private static GuiConfig configManager;

    static {
        keyPreferenceMap.put("∧", AND_KEY);
        keyPreferenceMap.put("∨", OR_KEY);
        keyPreferenceMap.put("¬", NOT_KEY);
        keyPreferenceMap.put("→", COND_KEY);
        keyPreferenceMap.put("↔", BICONDITIONAL_KEY);
        keyPreferenceMap.put("⊥", CONTRA_KEY);
        keyPreferenceMap.put("∀", UNIVERSAL_KEY);
        keyPreferenceMap.put("∃", EXISTENTIAL_KEY);
        keyPreferenceMap.put("≠", NOT_EQUALS_KEY);
        keyPreferenceMap.put("×", MULTIPLICATION_KEY);
    }

    static {
        if (!CLIENT_CONFIG_DIR.exists()) {
            if (!CLIENT_CONFIG_DIR.mkdirs())
                logger.error("Failed to create configuration directory");
            Path path = CLIENT_CONFIG_DIR.toPath();
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

    @FXML
    private VBox aliasBox;

    private BidiMap<String, String> aliasKeyMap = new DualHashBidiMap<>();
    private HashMap<String, TextField> aliasMap = new HashMap<>();
    private Stage stage;
    private File saveDirectory = new File(preferences.get(LAST_SAVE_DIR, System.getProperty("user.home")));
    private String accessToken = preferences.get(ACCESS_TOKEN, null);
    private SimpleObjectProperty[] accelerators = new SimpleObjectProperty[]{newProofLineKey, deleteProofLineKey,
            startSubProofKey, endSubProofKey, newPremiseKey, verifyLineKey, addGoalKey, verifyProofKey, newProofKey,
            openProofKey, saveProofKey, saveAsProofKey, undoKey, redoKey, copyKey, cutKey, pasteKey};

    private GuiConfig() throws IOException {
        for (String[] s : defaultKeyMap)
            aliasKeyMap.put(s[0], s[1]);
        if (!saveDirectory.exists())
            saveDirectory = new File(System.getProperty("user.home"));
        hideRulesPanel.addListener((observable, oldValue, newValue) -> preferences.putBoolean(HIDE_RULES_PANEL, newValue));
        hideOperatorsPanel.addListener((observable, oldValue, newValue) -> preferences.putBoolean(HIDE_OPERATOR_PANEL, newValue));
        username.addListener((observable, oldValue, newValue) -> {
            if (newValue == null)
                preferences.remove(USERNAME_KEY);
            else
                preferences.put(USERNAME_KEY, newValue);
        });
        selectedCourseId.addListener((observable, oldValue, newValue) -> preferences.putInt(SELECTED_COURSE_ID, newValue.intValue()));
        FXMLLoader loader = new FXMLLoader(GuiConfig.class.getResource("config.fxml"));
        loader.setController(this);
        Scene scene = new Scene(loader.load(), 500, 400);
        stage = new Stage();
        stage.setScene(scene);
    }

    public static void setClientConfigDir(File clientConfigDir) {
        if (clientConfigDir == null)
            clientConfigDir = new File(System.getProperty("user.home"), ".aris-java");
        if (!clientConfigDir.exists())
            //noinspection ResultOfMethodCallIgnored
            clientConfigDir.mkdirs();
        CLIENT_CONFIG_DIR = clientConfigDir;
    }

    public static GuiConfig getConfigManager() {
        if (configManager == null) {
            try {
                configManager = new GuiConfig();
            } catch (IOException e) {
                logger.error("Failed to load config screen", e);
            }
        }
        return configManager;
    }

    public String replaceText(String text) {
        for (int i = 0; i < text.length(); ++i) {
            String replace;
            if ((replace = aliasKeyMap.get(String.valueOf(text.charAt(i)))) != null)
                text = text.substring(0, i) + replace + text.substring(i + 1);
        }
        return text;
    }

    @FXML
    private void initialize() {
        ArrayList<Map.Entry<String, String>> sortedAliases = new ArrayList<>(aliasKeyMap.entrySet());
        sortedAliases.sort((o1, o2) -> {
            int i1 = 0, i2 = 0;
            for (int i = 0; i < defaultKeyMap.length; ++i) {
                if (defaultKeyMap[i][1].equals(o1.getValue()))
                    i1 = i;
                if (defaultKeyMap[i][1].equals(o2.getValue()))
                    i2 = i;
            }
            return Integer.compare(i1, i2);
        });
        for (Map.Entry<String, String> alias : sortedAliases) {
            HBox box = new HBox();
            Label lbl = new Label("Key mapping for \"" + alias.getValue() + "\"");
            Separator separator = new Separator(Orientation.HORIZONTAL);
            separator.setVisible(false);
            HBox.setHgrow(separator, Priority.ALWAYS);
            TextField textField = new TextField();
            textField.setPrefWidth(30);
            textField.setTextFormatter(new TextFormatter<String>(change -> {
                if (change.getControlNewText().length() > 1) {
                    change.setText(change.getControlNewText().substring(0, 1));
                    change.setRange(0, 1);
                }
                return change;
            }));
            aliasMap.put(alias.getValue(), textField);
            box.getChildren().addAll(lbl, separator, textField);
            aliasBox.getChildren().add(box);
        }
        populateConfig();
    }

    @FXML
    private void applyConfig() {
        BidiMap<String, String> newAliases = new DualHashBidiMap<>();
        for (Map.Entry<String, TextField> alias : aliasMap.entrySet()) {
            String key = alias.getKey();
            TextField tf = alias.getValue();
            String a = tf.getText();
            if (a.length() == 1) {
                if (newAliases.containsKey(a)) {
                    configAlert("The alias set for the \"" + key + "\" symbol is the same as for the \"" + newAliases.get(a) + "\" symbol");
                    return;
                }
                newAliases.put(a, key);
            }
        }
        for (Map.Entry<String, String> prefMap : keyPreferenceMap.entrySet()) {
            if (newAliases.containsValue(prefMap.getKey()))
                preferences.put(prefMap.getValue(), newAliases.getKey(prefMap.getKey()));
            else
                preferences.remove(prefMap.getValue());
        }
        aliasKeyMap = newAliases;
        stage.hide();
    }

    @FXML
    private void cancelConfig() {
        stage.hide();
        populateConfig();
    }

    private void populateConfig() {
        for (Map.Entry<String, String> alias : aliasKeyMap.entrySet())
            aliasMap.get(alias.getValue()).setText(alias.getKey());
    }

    private void configAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Configuration Error");
        alert.setHeaderText("There is an error with the current configuration");
        alert.setContentText(message);
        alert.show();
    }

    public void showConfig() {
        Platform.runLater(() -> stage.show());
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
        if (!saveDirectory.exists()) {
            saveDirectory = new File(System.getProperty("user.home"));
            preferences.put(LAST_SAVE_DIR, saveDirectory.getAbsolutePath());
        }
        return saveDirectory;
    }

    public void setSaveDirectory(File file) {
        if (file == null)
            saveDirectory = new File(System.getProperty("user.home"));
        else
            saveDirectory = file;
        preferences.put(LAST_SAVE_DIR, saveDirectory.getAbsolutePath());
    }

    public synchronized String getAccessToken() {
        return accessToken;
    }

    public synchronized void setAccessToken(String token) {
        accessToken = token;
        if (token == null)
            preferences.remove(ACCESS_TOKEN);
        else
            preferences.put(ACCESS_TOKEN, token);
    }

}
