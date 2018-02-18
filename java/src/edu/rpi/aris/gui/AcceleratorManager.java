package edu.rpi.aris.gui;

import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

public class AcceleratorManager {

    public SimpleObjectProperty<KeyCombination> newProofLine = new SimpleObjectProperty<>(KeyCombination.keyCombination("Ctrl+A"));

    public boolean ignore(KeyEvent event) {
        return newProofLine.get().match(event);
    }

}
