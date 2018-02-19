package edu.rpi.aris.gui;

import edu.rpi.aris.rules.RuleList;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.apache.commons.lang.StringUtils;

import java.util.Arrays;
import java.util.stream.Collectors;

public class ProofLine {

    public static final int SUBPROOF_INDENT = 25;
    public static final Image SELECTED_IMAGE = new Image(ProofLine.class.getResourceAsStream("right_arrow.png"));
    public static final String HIGHLIGHT_STYLE = "highlight-premise";

    @FXML
    private HBox root;
    @FXML
    private HBox selectedHbox;
    @FXML
    private HBox subproofIndent;
    @FXML
    private VBox textVbox;
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
    @FXML
    private ContextMenu ruleMenu;

    private MainWindow window;
    private RuleList selectedRule = null;
    private Proof.Line proofLine;
    private SimpleStringProperty numberString = new SimpleStringProperty();

    private EventHandler<MouseEvent> highlightListener = new EventHandler<MouseEvent>() {
        @Override
        public void handle(MouseEvent mouseEvent) {
            if (!textField.isEditable())
                window.requestSelect(ProofLine.this);
        }
    };

    public ProofLine(MainWindow window, Proof.Line proofLine) {
        this.window = window;
        this.proofLine = proofLine;
    }

    @FXML
    public void initialize() {
        validImage.fitHeightProperty().bind(selectedLine.fitHeightProperty());
        validImage.fitWidthProperty().bind(validImage.fitHeightProperty());
        textField.fontProperty().bind(window.getFontProperty());
        ruleChoose.fontProperty().bind(window.getFontProperty());
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
            window.requestFocus(this);
            if (e.getButton() == MouseButton.PRIMARY)
                ruleChoose.getContextMenu().show(ruleChoose, e.getScreenX(), e.getScreenY());
        });
        selectedLine.setOnMouseClicked(e -> window.requestFocus(this));
        selectedHbox.setOnMouseClicked(e -> window.requestFocus(this));
        numberLbl.setOnMouseClicked(e -> window.requestFocus(this));
        textField.focusedProperty().addListener((observableValue, oldVal, newVal) -> {
            if (textField.isEditable() && !newVal)
                textField.requestFocus();
        });
        textField.editableProperty().addListener((observableValue, oldVal, newVal) -> {
            if (newVal)
                textField.requestFocus();
            selectedLine.imageProperty().setValue(newVal ? SELECTED_IMAGE : null);
        });
        textField.setOnMouseClicked(highlightListener);
        textField.setOnKeyPressed(keyEvent -> {
            if (window.ignoreKeyEvent(keyEvent)) {
                textField.getParent().fireEvent(keyEvent);
                keyEvent.consume();
            }
        });
        textField.editableProperty().bind(Bindings.createBooleanBinding(() -> proofLine.lineNumberProperty().get() == window.selectedLineProperty().get(), proofLine.lineNumberProperty(), window.selectedLineProperty()));
        setUpRules();
        if (proofLine.isAssumption()) {
            ruleChoose.setVisible(false);
            ruleChoose.setManaged(false);
            textVbox.getStyleClass().add("underline");
            if (proofLine.subproofLevelProperty().get() != 0)
                ((Region) textVbox.getChildren().get(0)).setPrefHeight(9);
        }
        setIndent(proofLine.subproofLevelProperty().get());
        proofLine.subproofLevelProperty().addListener((observableValue, oldVal, newVal) -> setIndent(newVal.intValue()));
    }

    public void setHighlighted(boolean highlighted) {
        if (highlighted) {
            if (!root.getStyleClass().contains(HIGHLIGHT_STYLE)) {
                root.getStyleClass().add(HIGHLIGHT_STYLE);
                textVbox.getStyleClass().add(HIGHLIGHT_STYLE);
            }
        } else {
            root.getStyleClass().remove(HIGHLIGHT_STYLE);
            textVbox.getStyleClass().remove(HIGHLIGHT_STYLE);
        }
    }

    private void setUpRules() {
        //TODO: Change this to separate rules into sections and allow choosing logic system
        ruleMenu.getItems().addAll(Arrays.stream(RuleList.values()).map(this::getRuleMenu).collect(Collectors.toList()));
    }

    private void setIndent(int level) {
        if (proofLine.subproofLevelProperty().get() != 0)
            ((Region) textVbox.getChildren().get(0)).setPrefHeight(9);
        else
            ((Region) textVbox.getChildren().get(0)).setPrefHeight(4);
        subproofIndent.getChildren().clear();
        for (int i = 0; i < level; ++i) {
            Region spacer = new Region();
            spacer.setOnMouseClicked(highlightListener);
            spacer.maxHeightProperty().bind(root.heightProperty().subtract(proofLine.isAssumption() && i == level-1 ? 5 : 0));
            spacer.setPrefWidth(SUBPROOF_INDENT);
            spacer.setMinWidth(SUBPROOF_INDENT);
            spacer.getStyleClass().add("proof-left-border");
            subproofIndent.getChildren().add(spacer);
            subproofIndent.setAlignment(Pos.BOTTOM_LEFT);
        }
    }

    private MenuItem getRuleMenu(RuleList rule) {
        MenuItem menuItem = new MenuItem(rule.name);
        menuItem.setOnAction(actionEvent -> {
            selectedRule = rule;
            ruleChoose.setText(rule.simpleName);
        });
        return menuItem;
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

}
