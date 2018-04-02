package edu.rpi.aris.gui.submit;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class ProofList {

    private SimpleBooleanProperty loaded = new SimpleBooleanProperty(false);
    private ObservableList<ProofInfo> proofs = FXCollections.observableArrayList();

    public void load(Runnable runnable, boolean reload) {
        if (reload)
            loaded.set(false);
        if (loaded.get())
            return;
        proofs.clear();
    }

}
