package edu.rpi.aris.gui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.util.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class MainWindow {

    @FXML
    private VBox proofTable;

    private ObjectProperty<Font> fontObjectProperty;
    private ArrayList<Pair<ProofLine, BooleanProperty>> lines = new ArrayList<>();
    private HashMap<ProofLine, Integer> indexLookup = new HashMap<>();
    private SimpleIntegerProperty selectedLine = new SimpleIntegerProperty(-1);

    public MainWindow() {
        fontObjectProperty = new SimpleObjectProperty<>(new Font(14));
        selectedLine.addListener((observableValue, oldValue, newValue) -> {
            if (oldValue.intValue() >= 0 && oldValue.intValue() < lines.size())
                lines.get(oldValue.intValue()).getValue().set(false);
            if (newValue.intValue() >= 0 && newValue.intValue() < lines.size())
                lines.get(newValue.intValue()).getValue().set(true);
        });
    }

    @FXML
    public void initialize() {
        try {
            addProofLine(true, 0, 0);
            selectedLine.set(0);
            addProofLine(false, 0, 1);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addProofLine(boolean assumption, int proofLevel, int index) throws IOException {
        FXMLLoader loader = new FXMLLoader(MainWindow.class.getResource("proof_line.fxml"));
        ProofLine controller = new ProofLine(assumption, proofLevel, this);
        loader.setController(controller);
        HBox line = loader.load();
        proofTable.getChildren().add(index, line);
        lines.add(index, new Pair<>(controller, controller.getEditableProperty()));
        indexLookup.put(controller, index);
    }

    public ObjectProperty<Font> getFontProperty() {
        return fontObjectProperty;
    }

    public void requestFocus(ProofLine line) {
        int index = indexLookup.get(line);
        selectedLine.set(index);
    }
}
