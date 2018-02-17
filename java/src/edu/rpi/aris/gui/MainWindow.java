package edu.rpi.aris.gui;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.io.IOException;

public class MainWindow {

    @FXML
    private VBox proofTable;

    private ObjectProperty<Font> fontObjectProperty;

    public MainWindow() {
        fontObjectProperty = new SimpleObjectProperty<>(new Font(14));
    }

    @FXML
    public void initialize() {
        try {
            addProofLine(true, 0);
            addProofLine(true, 1);
            addProofLine(true, 2);
            addProofLine(false, 2);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addProofLine(boolean assumption, int proofLevel) throws IOException {
        FXMLLoader loader = new FXMLLoader(MainWindow.class.getResource("proof_line.fxml"));
        loader.setController(new ProofLine(assumption, proofLevel, this));
        HBox line = loader.load();
        proofTable.getChildren().add(line);
    }

    public ObjectProperty<Font> getFontProperty() {
        return fontObjectProperty;
    }

}
