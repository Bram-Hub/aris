package edu.rpi.aris.gui;

import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

public class AcceleratorManager {

    public SimpleObjectProperty<KeyCombination> newProofLine = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+A"));
    public SimpleObjectProperty<KeyCombination> deleteProofLine = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+D"));
    public SimpleObjectProperty<KeyCombination> startSubproof = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+P"));

    private SimpleObjectProperty[] accelerators = new SimpleObjectProperty[]{newProofLine, deleteProofLine, startSubproof};

    public boolean ignore(KeyEvent event) {
        for (SimpleObjectProperty a : accelerators)
            if (((KeyCombination) a.get()).match(event))
                return true;
        return false;
    }

}
