package edu.rpi.aris.gui;

import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

import java.util.HashMap;

public class ConfigurationManager {

    public SimpleObjectProperty<KeyCombination> newProofLine = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+A"));
    public SimpleObjectProperty<KeyCombination> deleteProofLine = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+D"));
    public static final HashMap<String, String> KEY_MAP = new HashMap<>();
    private static final String[][] keyMap = new String[][]{{"&", "∧"}};
    public SimpleObjectProperty<KeyCombination> newPremise = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+R"));

    static {
        for (String[] s : keyMap)
            KEY_MAP.put(s[0], s[1]);
    }

    public SimpleObjectProperty<KeyCombination> startSubProof = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+P"));
    public SimpleObjectProperty<KeyCombination> endSubProof = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+E"));
    private SimpleObjectProperty[] accelerators = new SimpleObjectProperty[]{newProofLine, deleteProofLine, startSubProof, endSubProof, newPremise};

    public boolean ignore(KeyEvent event) {
        for (SimpleObjectProperty a : accelerators)
            if (((KeyCombination) a.get()).match(event))
                return true;
        return false;
    }

}
