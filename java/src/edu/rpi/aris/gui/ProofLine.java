package edu.rpi.aris.gui;

import edu.rpi.aris.rules.RuleList;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
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

public class ProofLine implements LineDeletionListener {

    public static final int SUBPROOF_INDENT = 25;
    public static final Image SELECTED_IMAGE = new Image(ProofLine.class.getResourceAsStream("right_arrow.png"));
    public static final String HIGHLIGHT_STYLE = "highlight-premise";

    @FXML
    private HBox root;
    @FXML
    private HBox selectedHbox;
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

    private boolean isAssumption;
    private int level;
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

    public ProofLine(boolean isAssumption, int level, MainWindow window, Proof.Line proofLine) {
        this.isAssumption = isAssumption;
        this.level = level;
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
            spacer.setOnMouseClicked(highlightListener);
            spacer.maxHeightProperty().bind(root.heightProperty().subtract(isAssumption && i == 0 ? 5 : 0));
            spacer.setPrefWidth(SUBPROOF_INDENT);
            spacer.setMinWidth(SUBPROOF_INDENT);
            spacer.getStyleClass().add("proof-left-border");
            root.getChildren().add(1, spacer);
            root.setAlignment(Pos.BOTTOM_LEFT);
        }
    }

    public void setHighlighted(boolean highlighted) {
        if (highlighted) {
            root.getStyleClass().add(HIGHLIGHT_STYLE);
            textVbox.getStyleClass().add(HIGHLIGHT_STYLE);
        } else {
            root.getStyleClass().remove(HIGHLIGHT_STYLE);
            textVbox.getStyleClass().remove(HIGHLIGHT_STYLE);
        }
    }

    private void setUpRules() {
        //TODO: Change this to separate rules into sections and allow choosing logic system
        ruleMenu.getItems().addAll(Arrays.stream(RuleList.values()).map(this::getRuleMenu).collect(Collectors.toList()));
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

    @Override
    public void lineDeleted() {

    }

}
