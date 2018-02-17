package edu.rpi.aris.gui;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class ProofLine {

    public static final int SUBPROOF_INDENT = 25;

    @FXML
    private HBox root;
    @FXML
    private VBox textVbox;
    @FXML
    private TextField textField;
    @FXML
    private ChoiceBox ruleChoose;
    @FXML
    private ImageView validImage;
    @FXML
    private ImageView selectedLine;

    private boolean isAssumption;
    private int level;
    private MainWindow window;

    public ProofLine(boolean isAssumption, int level, MainWindow window) {
        this.isAssumption = isAssumption;
        this.level = level;
        this.window = window;
    }

    @FXML
    public void initialize() {
        selectedLine.fitWidthProperty().bind(root.heightProperty().subtract(isAssumption && level != 0 ? 5 : 0));
        selectedLine.fitHeightProperty().bind(selectedLine.fitWidthProperty());
        validImage.fitHeightProperty().bind(selectedLine.fitHeightProperty());
        validImage.fitWidthProperty().bind(validImage.fitHeightProperty());
        textField.fontProperty().bind(window.getFontProperty());
        if (isAssumption) {
            ruleChoose.setVisible(false);
            ruleChoose.setManaged(false);
            textVbox.getStyleClass().add("underline");
            if (level != 0) {
                Region region = new Region();
                region.setPrefHeight(5);
                textVbox.getChildren().add(0, region);
            }
        }
        for (int i = 0; i < level; ++i) {
            Region spacer = new Region();
            spacer.maxHeightProperty().bind(root.heightProperty().subtract(isAssumption && i == 0 ? 5 : 0));
            spacer.setPrefWidth(SUBPROOF_INDENT);
            spacer.getStyleClass().add("proof-left-border");
            root.getChildren().add(1, spacer);
            root.setAlignment(Pos.BOTTOM_LEFT);
        }
    }

}
