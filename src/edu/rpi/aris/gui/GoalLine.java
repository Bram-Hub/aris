package edu.rpi.aris.gui;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;

import java.util.Objects;

public class GoalLine {

    @FXML
    private TextField goalText;
    @FXML
    private ImageView goalValidImg;

    private SimpleStringProperty goalProp;

    public GoalLine(SimpleStringProperty goal) {
        Objects.requireNonNull(goal);
        goalProp = goal;
    }

    @FXML
    public void initialize() {
        goalProp.bind(goalText.textProperty());
    }

    public void setStatus(Proof.Status status) {
        goalValidImg.setImage(status.img);
    }

}
