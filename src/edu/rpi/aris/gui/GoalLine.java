package edu.rpi.aris.gui;

import edu.rpi.aris.ConfigurationManager;
import edu.rpi.aris.gui.event.SentenceChangeEvent;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import org.apache.commons.lang.math.IntRange;

import java.util.Timer;
import java.util.TimerTask;
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
    private String lastVal = "";
    private Timer historyTimer;

    GoalLine(MainWindow window, Proof.Goal goal) {
        this.goal = goal;
        this.window = window;
    }

    @FXML
    public void initialize() {
        lastVal = goal.goalStringProperty().get();
        goalText.setText(goal.goalStringProperty().get());
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
        goalText.addEventFilter(KeyEvent.ANY, keyEvent -> {
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
        goalText.addEventHandler(KeyEvent.ANY, keyEvent -> {
            if (keyEvent.getEventType() == KeyEvent.KEY_PRESSED) {
                KeyCode key = keyEvent.getCode();
                if (key == KeyCode.BACK_SPACE || key == KeyCode.SPACE || key == KeyCode.ENTER)
                    commitSentenceChange();
                else {
                    startHistoryTimer();
                    window.getHistory().upcomingHistoryEvent(!goalText.getText().equals(lastVal));
                }
            }
        });
    }

    private synchronized void startHistoryTimer() {
        if (historyTimer != null) {
            historyTimer.cancel();
            historyTimer = null;
        }
        historyTimer = new Timer();
        historyTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                commitSentenceChange();
            }
        }, 500);
    }

    public int lineNumber() {
        return goal.goalNumProperty().get();
    }

    public HBox getRootNode() {
        return root;
    }

    public void selectError() {
        IntRange error = goal.errorRangeProperty().get();
        if (error != null)
            goalText.selectRange(error.getMinimumInteger(), error.getMaximumInteger() + 1);
    }

    public void insertText(String str) {
        goalText.insertText(goalText.getCaretPosition(), str);
    }

    public synchronized void commitSentenceChange() {
        if (historyTimer != null) {
            historyTimer.cancel();
            historyTimer = null;
        }
        String currentVal = goalText.getText();
        if (!currentVal.equals(lastVal)) {
            SentenceChangeEvent event = new SentenceChangeEvent(goal.goalNumProperty().get(), lastVal, currentVal);
            window.getHistory().addHistoryEvent(event);
            lastVal = currentVal;
        }
    }

    public synchronized void resetLastString() {
        lastVal = goalText.getText();
    }

    public void setText(String text) {
        goalText.setText(text);
    }
}
