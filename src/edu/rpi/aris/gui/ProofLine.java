package edu.rpi.aris.gui;

import edu.rpi.aris.gui.event.SentenceChangeEvent;
import edu.rpi.aris.proof.Line;
import edu.rpi.aris.proof.LineChangeListener;
import edu.rpi.aris.proof.Proof;
import edu.rpi.aris.rules.RuleList;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.UnaryOperator;

public class ProofLine implements LineChangeListener {

    private static final int SUB_PROOF_INDENT = 25;
    private static final Image SELECTED_IMAGE = new Image(ProofLine.class.getResourceAsStream("right_arrow.png"));
    private static final String HIGHLIGHT_STYLE = "highlight-premise";
    private static final String UNDERLINE = "underline";

    @FXML
    private HBox root;
    @FXML
    private HBox selectedHBox;
    @FXML
    private HBox subProofIndent;
    @FXML
    private VBox textVBox;
    @FXML
    private TextField textField;
    @FXML
    private Label ruleChoose;
    @FXML
    private Label numberLbl;
    @FXML
    private ImageView validImage;
    @FXML
    private ImageView selectedLine;

    private MainWindow window;
    private Line proofLine;
    private int caretPos = 0;
    private String lastVal = "";
    private Timer historyTimer;

    private EventHandler<MouseEvent> highlightListener = new EventHandler<MouseEvent>() {
        @Override
        public void handle(MouseEvent mouseEvent) {
            if (!textField.isEditable())
                window.requestSelect(ProofLine.this);
        }
    };

    ProofLine(MainWindow window, Line proofLine) {
        this.window = window;
        this.proofLine = proofLine;
        proofLine.setChangeListener(this);
    }

    @FXML
    public void initialize() {
        lastVal = proofLine.getExpressionString();
        validImage.fitHeightProperty().bind(selectedLine.fitHeightProperty());
        validImage.fitWidthProperty().bind(validImage.fitHeightProperty());
        textField.fontProperty().bind(window.getFontProperty());
        ruleChoose.fontProperty().bind(window.getFontProperty());
        numberLbl.fontProperty().bind(window.getFontProperty());
        ruleChoose.setOnMouseClicked(e -> {
            requestFocus(e);
            if (e.getButton() == MouseButton.PRIMARY)
                ruleChoose.getContextMenu().show(ruleChoose, e.getScreenX(), e.getScreenY());
            else
                e.consume();
        });
        ruleChoose.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, Event::consume);
        selectedLine.setOnMouseClicked(this::requestFocus);
        selectedHBox.setOnMouseClicked(this::requestFocus);
        numberLbl.setOnMouseClicked(this::requestFocus);
        textField.focusedProperty().addListener((observableValue, oldVal, newVal) -> {
            if (newVal)
                Platform.runLater(() -> {
                    textField.deselect();
                    textField.positionCaret(caretPos);
                });
            else {
                caretPos = textField.getCaretPosition();
                if (textField.isEditable())
                    textField.requestFocus();
            }
        });
        textField.editableProperty().addListener((observableValue, oldVal, newVal) -> {
            if (newVal)
                textField.requestFocus();
            selectedLine.imageProperty().setValue(newVal ? SELECTED_IMAGE : null);
        });
        textField.setOnMouseClicked(this::requestFocus);
        textField.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, Event::consume);
        UnaryOperator<TextFormatter.Change> filter = t -> {
            t.setText(GuiConfig.getConfigManager().replaceText(t.getText()));
            return t;
        };
        textField.setTextFormatter(new TextFormatter<>(filter));
        textField.addEventFilter(KeyEvent.ANY, keyEvent -> {
            if (keyEvent.getEventType() == KeyEvent.KEY_PRESSED) {
                if (window.ignoreKeyEvent(keyEvent)) {
                    textField.getParent().fireEvent(keyEvent);
                    keyEvent.consume();
                }
                if (window.handleKeyEvent(keyEvent)) {
                    keyEvent.consume();
                }
            }
        });
        textField.addEventHandler(KeyEvent.ANY, keyEvent -> {
            if (keyEvent.getEventType() == KeyEvent.KEY_PRESSED) {
                KeyCode key = keyEvent.getCode();
                if (key == KeyCode.BACK_SPACE || key == KeyCode.SPACE || key == KeyCode.ENTER)
                    commitSentenceChange();
                else {
                    startHistoryTimer();
                    window.getHistory().upcomingHistoryEvent(!textField.getText().equals(lastVal));
                }
            }
        });
        window.selectedLineProperty().addListener((observable, oldValue, newValue) -> textField.setEditable(proofLine.getLineNum() == newValue.intValue()));
        textField.setText(proofLine.getExpressionString());
        textField.textProperty().addListener((observable, oldValue, newValue) -> proofLine.setExpressionString(newValue));
        if (proofLine.isUnderlined())
            textVBox.getStyleClass().add(UNDERLINE);
        if (proofLine.isAssumption()) {
            ruleChoose.setVisible(false);
            ruleChoose.setManaged(false);
            if (proofLine.getSubProofLevel() != 0)
                ((Region) textVBox.getChildren().get(0)).setPrefHeight(9);
        }
        String total = String.valueOf(window.numLines());
        String num = String.valueOf(proofLine.getLineNum() + 1);
        int spaces = (total.length() - num.length());
        numberLbl.setText(spaces < 0 ? "" : StringUtils.repeat("  ", spaces) + num + '.');
        setIndent(proofLine.getSubProofLevel());
        root.setOnMouseClicked(highlightListener);
        ruleChoose.setContextMenu(window.getRulesManager().getRulesDropdown());
        textField.positionCaret(textField.getText().length());
        caretPos = textField.getText().length();
    }

    public void setHighlighted(boolean highlighted) {
        if (highlighted) {
            if (!root.getStyleClass().contains(HIGHLIGHT_STYLE)) {
                root.getStyleClass().add(HIGHLIGHT_STYLE);
                textVBox.getStyleClass().add(HIGHLIGHT_STYLE);
            }
        } else {
            root.getStyleClass().remove(HIGHLIGHT_STYLE);
            textVBox.getStyleClass().remove(HIGHLIGHT_STYLE);
        }
    }

    private void setIndent(int level) {
        if (proofLine.getSubProofLevel() != 0)
            ((Region) textVBox.getChildren().get(0)).setPrefHeight(9);
        else
            ((Region) textVBox.getChildren().get(0)).setPrefHeight(4);
        subProofIndent.getChildren().clear();
        for (int i = 0; i < level; ++i) {
            Region spacer = new Region();
            spacer.setOnMouseClicked(highlightListener);
            spacer.maxHeightProperty().bind(root.heightProperty().subtract(proofLine.isAssumption() && i == level - 1 ? 5 : 0));
            spacer.setPrefWidth(SUB_PROOF_INDENT);
            spacer.setMinWidth(SUB_PROOF_INDENT);
            spacer.getStyleClass().add("proof-left-border");
            subProofIndent.getChildren().add(spacer);
            subProofIndent.setAlignment(Pos.BOTTOM_LEFT);
        }
    }

    public HBox getRootNode() {
        return root;
    }

    public Line getModel() {
        return proofLine;
    }

    public String getText() {
        return textField.getText();
    }

    public void setText(String text) {
        textField.setText(text);
        textField.positionCaret(text.length());
    }

    public void selectError() {
        Range<Integer> error = proofLine.getErrorRange();
        if (error != null)
            textField.selectRange(error.getMinimum(), error.getMaximum() + 1);
    }

    public void insertText(String str) {
        textField.insertText(textField.getCaretPosition(), str);
    }

    private void requestFocus(MouseEvent e) {
        if (e.getButton() == MouseButton.PRIMARY)
            window.requestFocus(this);
        else if (e.getButton() == MouseButton.SECONDARY && !textField.isEditable())
            window.requestSelect(this);
        e.consume();
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

    public synchronized void commitSentenceChange() {
        if (historyTimer != null) {
            historyTimer.cancel();
            historyTimer = null;
        }
        String currentVal = textField.getText();
        if (!currentVal.equals(lastVal)) {
            SentenceChangeEvent event = new SentenceChangeEvent(proofLine.getLineNum(), lastVal, currentVal);
            window.getHistory().addHistoryEvent(event);
            lastVal = currentVal;
        }
    }

    public synchronized void resetLastString() {
        lastVal = textField.getText();
    }

    @Override
    public void expressionString(String str) {

    }

    @Override
    public void status(Proof.Status status) {
        validImage.setImage(MainWindow.STATUS_ICONS.get(status));
    }

    @Override
    public void lineNumber(int lineNum) {
        String total = String.valueOf(window.numLines());
        String num = String.valueOf(proofLine.getLineNum() + 1);
        int spaces = (total.length() - num.length());
        numberLbl.setText(spaces < 0 ? "" : StringUtils.repeat("  ", spaces) + num + '.');
        textField.setEditable(lineNum == window.selectedLineProperty().get());
    }

    @Override
    public void premises(HashSet<Line> premises) {

    }

    @Override
    public void subProofLevel(int level) {
        setIndent(level);
    }

    @Override
    public void selectedRule(RuleList rule) {
        ruleChoose.setText(rule == null ? "▼ Rule" : rule.simpleName);
    }

    @Override
    public void statusString(String statusString) {

    }

    @Override
    public void errorRange(Range<Integer> range) {

    }

    @Override
    public void underlined(boolean underlined) {
        if (underlined && !textVBox.getStyleClass().contains(UNDERLINE)) {
            textVBox.getStyleClass().add(UNDERLINE);
        } else {
            textVBox.getStyleClass().remove(UNDERLINE);
        }
    }

    public void requestFocus() {
        textField.requestFocus();
    }
}
