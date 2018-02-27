package edu.rpi.aris.gui;

import edu.rpi.aris.rules.RuleList;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class ConfigurationManager {

    private static final HashMap<String, String> KEY_MAP = new HashMap<>();
    private static final String[][] keyMap = new String[][]{{"&", "∧"}, {"|", "∨"}, {"!", "≠"}, {"~", "¬"}, {"$", "→"}, {"%", "↔"}, {"^", "⊥"}, {"@", "∀"}, {"#", "∃"}};
    private static final ConfigurationManager configManager;

    static {
        for (String[] s : keyMap)
            KEY_MAP.put(s[0], s[1]);
        configManager = new ConfigurationManager();
    }

    public SimpleObjectProperty<KeyCombination> newProofLineKey = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+A"));
    public SimpleObjectProperty<KeyCombination> deleteProofLineKey = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+D"));
    public SimpleObjectProperty<KeyCombination> newPremiseKey = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+R"));
    public SimpleObjectProperty<KeyCombination> startSubProofKey = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+P"));
    public SimpleObjectProperty<KeyCombination> endSubProofKey = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+E"));
    public SimpleObjectProperty<KeyCombination> verifyLineKey = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+F"));
    public SimpleObjectProperty<KeyCombination> addGoalKey = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+G"));
    public SimpleObjectProperty<KeyCombination> verifyProofKey = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+Shift+F"));

    private SimpleObjectProperty[] accelerators = new SimpleObjectProperty[]{newProofLineKey, deleteProofLineKey, startSubProofKey, endSubProofKey, newPremiseKey, verifyLineKey, addGoalKey, verifyProofKey};

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
}
