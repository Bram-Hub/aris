package edu.rpi.aris.gui;

import edu.rpi.aris.gui.event.SentenceChangeEvent;
import edu.rpi.aris.proof.Goal;
import edu.rpi.aris.proof.Line;
import edu.rpi.aris.proof.LineChangeListener;
import edu.rpi.aris.proof.Proof;
import edu.rpi.aris.rules.RuleList;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import org.apache.commons.lang3.Range;

import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.UnaryOperator;

public class GoalLine implements LineChangeListener {


    @FXML
    private HBox root;
    @FXML
    private TextField goalText;
    @FXML
    private ImageView goalValidImg;

    private Goal goal;
    private MainWindow window;
    private int caretPos = 0;
    private String lastVal = "";
    private Timer historyTimer;

    GoalLine(MainWindow window, Goal goal) {
        this.goal = goal;
        this.window = window;
        goal.setChangeListener(this);
    }

    @FXML
    public void initialize() {
        lastVal = goal.getGoalString();
        goalText.setText(goal.getGoalString());
        goalText.textProperty().addListener((observable, oldValue, newValue) -> goal.setGoalString(newValue));
        goalText.setOnMouseClicked(mouseEvent -> window.requestFocus(this));
        UnaryOperator<TextFormatter.Change> filter = t -> {
            t.setText(GuiConfig.getConfigManager().replaceText(t.getText()));
            return t;
        };
        goalText.setTextFormatter(new TextFormatter<>(filter));
        window.selectedLineProperty().addListener((observable, oldValue, newValue) -> {
            int lineNum = newValue.intValue() * -1 - 2;
            goalText.setEditable(goal.getGoalNum() == lineNum);
        });
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
        return goal.getGoalNum();
    }

    public HBox getRootNode() {
        return root;
    }

    public void selectError() {
        Range<Integer> error = goal.getErrorRange();
        if (error != null)
            goalText.selectRange(error.getMinimum(), error.getMaximum() + 1);
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
            SentenceChangeEvent event = new SentenceChangeEvent(goal.getGoalNum(), lastVal, currentVal);
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

    @Override
    public void expressionString(String str) {

    }

    @Override
    public void status(Proof.Status status) {
        goalValidImg.setImage(MainWindow.STATUS_ICONS.get(status));
    }

    @Override
    public void lineNumber(int lineNum) {
        int goalNum = window.selectedLineProperty().get() * -1 - 2;
        goalText.setEditable(goalNum == lineNum);
    }

    @Override
    public void premises(HashSet<Line> premises) {

    }

    @Override
    public void subProofLevel(int level) {

    }

    @Override
    public void selectedRule(RuleList rule) {

    }

    @Override
    public void statusString(String statusString) {

    }

    @Override
    public void errorRange(Range<Integer> range) {

    }

    @Override
    public void underlined(boolean underlined) {

    }
}
