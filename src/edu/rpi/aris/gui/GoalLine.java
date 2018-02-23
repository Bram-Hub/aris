package edu.rpi.aris.gui;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;

import java.util.function.UnaryOperator;

public class GoalLine {


    @FXML
    private HBox root;
    @FXML
    private TextField goalText;
    @FXML
    private ImageView goalValidImg;

    private Proof.Goal goal;
    private MainWindow window;
    private int caretPos = 0;

    GoalLine(MainWindow window, Proof.Goal goal) {
        this.goal = goal;
        this.window = window;
    }

    @FXML
    public void initialize() {
        goal.goalStringProperty().bind(goalText.textProperty());
        goalValidImg.imageProperty().bind(Bindings.createObjectBinding(() -> goal.goalStatusProperty().get().img, goal.goalStatusProperty()));
        goalText.setOnMouseClicked(mouseEvent -> window.requestFocus(this));
        UnaryOperator<TextFormatter.Change> filter = t -> {
            t.setText(ConfigurationManager.replaceText(t.getText()));
            return t;
        };
        goalText.setTextFormatter(new TextFormatter<>(filter));
        goalText.editableProperty().bind(Bindings.createBooleanBinding(() -> {
            int lineNum = window.selectedLineProperty().get() * -1 - 2;
            return goal.goalNumProperty().get() == lineNum;
        }, goal.goalNumProperty(), window.selectedLineProperty()));
        goalText.editableProperty().addListener((observableValue, aBoolean, t1) -> goalText.requestFocus());
        goalText.addEventHandler(KeyEvent.ANY, keyEvent -> {
            if (keyEvent.getEventType() == KeyEvent.KEY_PRESSED) {
                if (window.ignoreKeyEvent(keyEvent)) {
                    goalText.getParent().fireEvent(keyEvent);
                    keyEvent.consume();
                }
                if (window.handleKeyEvent(keyEvent))
                    keyEvent.consume();
            }
        });
        goalText.focusedProperty().addListener((observableValue, oldVal, newVal) -> {
            if (newVal)
                Platform.runLater(() -> {
                    goalText.deselect();
                    goalText.positionCaret(caretPos);
                });
            else {
                caretPos = goalText.getCaretPosition();
                if (goalText.isEditable())
                    goalText.requestFocus();
            }
        });
    }

    public int lineNumber() {
        return goal.goalNumProperty().get();
    }

    public HBox getRootNode() {
        return root;
    }
}
