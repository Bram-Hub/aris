package edu.rpi.aris.gui;

import edu.rpi.aris.gui.event.SentenceChangeEvent;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
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
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.IntRange;

import java.util.Timer;
import java.util.TimerTask;
import java.util.function.UnaryOperator;

public class ProofLine {

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
    private Proof.Line proofLine;
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

    ProofLine(MainWindow window, Proof.Line proofLine) {
        this.window = window;
        this.proofLine = proofLine;
    }

    @FXML
    public void initialize() {
        lastVal = proofLine.expressionStringProperty().get();
        validImage.fitHeightProperty().bind(selectedLine.fitHeightProperty());
        validImage.fitWidthProperty().bind(validImage.fitHeightProperty());
        textField.fontProperty().bind(window.getFontProperty());
        ruleChoose.fontProperty().bind(window.getFontProperty());
        ruleChoose.textProperty().bind(Bindings.createStringBinding(() -> proofLine.selectedRuleProperty().get() == null ? "▼ Rule" : proofLine.selectedRuleProperty().get().simpleName, proofLine.selectedRuleProperty()));
        numberLbl.fontProperty().bind(window.getFontProperty());
        numberLbl.textProperty().bind(Bindings.createStringBinding(() -> {
            String total = String.valueOf(window.numLines().get());
            String num = String.valueOf(proofLine.lineNumberProperty().get() + 1);
            int spaces = (total.length() - num.length());
            if (spaces < 0)
                return "";
            return StringUtils.repeat("  ", spaces) + num + '.';
        }, window.numLines(), proofLine.lineNumberProperty()));
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
            t.setText(ConfigurationManager.replaceText(t.getText()));
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
        textField.editableProperty().bind(Bindings.createBooleanBinding(() -> proofLine.lineNumberProperty().get() == window.selectedLineProperty().get(), proofLine.lineNumberProperty(), window.selectedLineProperty()));
        textField.setText(proofLine.expressionStringProperty().get());
        proofLine.expressionStringProperty().bind(textField.textProperty());
        proofLine.isUnderlined().addListener((observableValue, oldVal, newVal) -> {
            if (newVal && !textVBox.getStyleClass().contains(UNDERLINE)) {
                textVBox.getStyleClass().add(UNDERLINE);
            } else {
                textVBox.getStyleClass().remove(UNDERLINE);
            }
        });
        if (proofLine.isUnderlined().get())
            textVBox.getStyleClass().add(UNDERLINE);
        if (proofLine.isAssumption()) {
            ruleChoose.setVisible(false);
            ruleChoose.setManaged(false);
            if (proofLine.subProofLevelProperty().get() != 0)
                ((Region) textVBox.getChildren().get(0)).setPrefHeight(9);
        }
        setIndent(proofLine.subProofLevelProperty().get());
        proofLine.subProofLevelProperty().addListener((observableValue, oldVal, newVal) -> setIndent(newVal.intValue()));
        root.setOnMouseClicked(highlightListener);
        validImage.imageProperty().bind(Bindings.createObjectBinding(() -> proofLine.statusProperty().get().img, proofLine.statusProperty()));
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
        if (proofLine.subProofLevelProperty().get() != 0)
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

    public Proof.Line getModel() {
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
        IntRange error = proofLine.errorRangeProperty().get();
        if (error != null)
            textField.selectRange(error.getMinimumInteger(), error.getMaximumInteger() + 1);
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
            SentenceChangeEvent event = new SentenceChangeEvent(proofLine.lineNumberProperty().get(), lastVal, currentVal);
            window.getHistory().addHistoryEvent(event);
            lastVal = currentVal;
        }
    }

    public synchronized void resetLastString() {
        lastVal = textField.getText();
    }

}
