package edu.rpi.aris.gui;

import edu.rpi.aris.proof.SaveManager;
import edu.rpi.aris.rules.Rule;
import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import org.apache.commons.lang.math.IntRange;

import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Collectors;

public class MainWindow {

    @FXML
    private VBox proofTable;
    @FXML
    private ScrollPane scrollPane;
    @FXML
    private ScrollPane goalScroll;
    @FXML
    private Label statusLbl;
    @FXML
    private Label goalLbl;
    @FXML
    private Label errorRangeLbl;
    @FXML
    private GridPane operatorPane;
    @FXML
    private VBox rulesPane;
    @FXML
    private TitledPane oprTitlePane;

    private ObjectProperty<Font> fontObjectProperty;
    private ArrayList<ProofLine> proofLines = new ArrayList<>();
    private ArrayList<GoalLine> goalLines = new ArrayList<>();
    private SimpleIntegerProperty selectedLine = new SimpleIntegerProperty(-1);
    private Proof proof;
    private Stage primaryStage;
    private ConfigurationManager configuration = ConfigurationManager.getConfigManager();
    private RulesManager rulesManager;
    private File saveFile = null;

    public MainWindow(Stage primaryStage) throws IOException {
        this(primaryStage, new Proof());
    }

    public MainWindow(Stage primaryStage, Proof proof) throws IOException {
        Objects.requireNonNull(primaryStage);
        Objects.requireNonNull(proof);
        this.primaryStage = primaryStage;
        this.proof = proof;
        primaryStage.setTitle("ARIS");
        fontObjectProperty = new SimpleObjectProperty<>(new Font(14));
        rulesManager = new RulesManager();
        rulesManager.addRuleSelectionHandler(ruleSelectEvent -> {
            if (selectedLine.get() > -1) {
                Proof.Line line = proof.getLines().get(selectedLine.get());
                if (!line.isAssumption())
                    line.selectedRuleProperty().set(ruleSelectEvent.getRule());
            }
        });
        setupScene();
        selectedLine.addListener((observableValue, oldVal, newVal) -> {
            statusLbl.textProperty().unbind();
            errorRangeLbl.textProperty().unbind();
            if (selectedLine.get() >= 0) {
                statusLbl.textProperty().bind(proof.getLines().get(selectedLine.get()).statusMsgProperty());
                errorRangeLbl.textProperty().bind(Bindings.createStringBinding(() -> {
                    Proof.Line line = proof.getLines().get(selectedLine.get());
                    if (line.errorRangeProperty().get() == null)
                        return null;
                    IntRange range = line.errorRangeProperty().get();
                    if (range.getMinimumInteger() == range.getMaximumInteger())
                        return String.valueOf(range.getMinimumInteger() + 1);
                    return (range.getMinimumInteger() + 1) + " - " + (range.getMaximumInteger() + 1);
                }, proof.getLines().get(selectedLine.get()).errorRangeProperty()));
                proof.getLines().get(newVal.intValue()).verifyClaim();
            } else if (selectedLine.get() < -1) {
                statusLbl.textProperty().bind(proof.getGoals().get(selectedLine.get() * -1 - 2).statusStringProperty());
                errorRangeLbl.textProperty().bind(Bindings.createStringBinding(() -> {
                    Proof.Goal goal = proof.getGoals().get(selectedLine.get() * -1 - 2);
                    if (goal.errorRangeProperty().get() == null)
                        return null;
                    IntRange range = goal.errorRangeProperty().get();
                    if (range.getMinimumInteger() == range.getMaximumInteger())
                        return String.valueOf(range.getMinimumInteger() + 1);
                    return (range.getMinimumInteger() + 1) + " - " + (range.getMaximumInteger() + 1);
                }, proof.getGoals().get(selectedLine.get() * -1 - 2).errorRangeProperty()));
            }
            updateHighlighting(newVal.intValue());
        });
    }

    private void setupScene() throws IOException {
        FXMLLoader loader = new FXMLLoader(Aris.class.getResource("main_window.fxml"));
        loader.setController(this);
        Parent root = loader.load();
        MenuBar bar = setupMenu();
        BorderPane pane = new BorderPane();
        pane.setTop(bar);
        pane.setCenter(root);
        Scene scene = new Scene(pane, 1000, 800);
        primaryStage.setScene(scene);
        scene.setOnKeyPressed(this::handleKeyEvent);
    }

    public boolean handleKeyEvent(KeyEvent keyEvent) {
        switch (keyEvent.getCode()) {
            case UP:
                lineUp();
                break;
            case DOWN:
                lineDown();
                break;
            default:
                return false;
        }
        return true;
    }

    private synchronized void lineUp() {
        if (selectedLine.get() > 0) {
            requestFocus(selectedLine.get() - 1);
            autoScroll(scrollPane.getContent().getBoundsInLocal());
        } else if (selectedLine.get() < -2) {
            requestFocus(selectedLine.get() + 1);
            autoScroll(goalScroll.getContent().getBoundsInLocal());
        } else if (selectedLine.get() == -2) {
            requestFocus(proof.numLinesProperty().get() - 1);
            autoScroll(scrollPane.getContent().getBoundsInLocal());
        }
    }

    private synchronized void lineDown() {
        if (selectedLine.get() >= 0 && selectedLine.get() + 1 < proof.numLinesProperty().get()) {
            requestFocus(selectedLine.get() + 1);
            autoScroll(scrollPane.getContent().getBoundsInLocal());
        } else if (selectedLine.get() < -1 && selectedLine.get() > proof.getGoals().size() * -1 - 1) {
            requestFocus(selectedLine.get() - 1);
            autoScroll(goalScroll.getContent().getBoundsInLocal());
        }
    }

    private MenuBar setupMenu() {
        MenuBar bar = new MenuBar();

        Menu file = new Menu("File");
        Menu edit = new Menu("Edit");
        Menu proof = new Menu("Proof");
        Menu help = new Menu("Help");

        // File menu items

        MenuItem newProof = new MenuItem("New Proof");
        MenuItem openProof = new MenuItem("Open Proof");
        MenuItem saveProof = new MenuItem("Save Proof");
        MenuItem saveAsProof = new MenuItem("Save Proof As");
        MenuItem quit = new MenuItem("Quit");

        newProof.setOnAction(actionEvent -> newProof());
        openProof.setOnAction(actionEvent -> openProof());
        saveAsProof.setOnAction(actionEvent -> saveProof(true));
        saveProof.setOnAction(actionEvent -> saveProof(false));

        newProof.acceleratorProperty().bind(configuration.newProofKey);
        openProof.acceleratorProperty().bind(configuration.openProofKey);
        saveProof.acceleratorProperty().bind(configuration.saveProofKey);
        saveAsProof.acceleratorProperty().bind(configuration.saveAsProofKey);

        file.getItems().addAll(newProof, openProof, saveProof, saveAsProof, quit);

        // Edit menu items

        MenuItem undo = new MenuItem("Undo");
        MenuItem redo = new MenuItem("Redo");
        MenuItem copy = new MenuItem("Copy");
        MenuItem cut = new MenuItem("Cut");
        MenuItem paste = new MenuItem("Paste");
        MenuItem settings = new MenuItem("Settings");

        undo.acceleratorProperty().bind(configuration.undoKey);
        redo.acceleratorProperty().bind(configuration.redoKey);
        copy.acceleratorProperty().bind(configuration.copyKey);
        cut.acceleratorProperty().bind(configuration.cutKey);
        paste.acceleratorProperty().bind(configuration.pasteKey);

        edit.getItems().addAll(undo, redo, copy, cut, paste, settings);

        // Proof menu items

        MenuItem addLine = new MenuItem("Add Line");
        MenuItem deleteLine = new MenuItem("Delete Line");
        MenuItem startSubProof = new MenuItem("Start Subproof");
        MenuItem endSubProof = new MenuItem("End Subproof");
        MenuItem newPremise = new MenuItem("Add Premise");
        MenuItem addGoal = new MenuItem("Add Goal");
        MenuItem verifyLine = new MenuItem("Verify Line");
        MenuItem verifyProof = new MenuItem("Verify Proof");

        addLine.setOnAction(actionEvent -> {
            if (selectedLine.get() < 0)
                return;
            int line = selectedLine.get() + 1;
            line = line < this.proof.numPremises().get() ? this.proof.numPremises().get() : line;
            addProofLine(false, this.proof.getLines().get(selectedLine.get()).subProofLevelProperty().get(), line);
            selectedLine.set(-1);
            selectedLine.set(line);
        });

        deleteLine.setOnAction(actionEvent -> deleteLine(selectedLine.get()));

        startSubProof.setOnAction(actionEvent -> startSubProof());

        endSubProof.setOnAction(actionEvent -> endSubproof());

        newPremise.setOnAction(actionEvent -> {
            selectedLine.set(-1);
            selectedLine.set(addPremise());
        });

        addGoal.setOnAction(actionEvent -> {
            selectedLine.set(-1);
            selectedLine.set(-2 - addGoal());
        });

        verifyLine.setOnAction(actionEvent -> verifyLine());

        verifyProof.setOnAction(actionEvent -> this.proof.verifyProof());

        addLine.acceleratorProperty().bind(configuration.newProofLineKey);
        deleteLine.acceleratorProperty().bind(configuration.deleteProofLineKey);
        startSubProof.acceleratorProperty().bind(configuration.startSubProofKey);
        endSubProof.acceleratorProperty().bind(configuration.endSubProofKey);
        newPremise.acceleratorProperty().bind(configuration.newPremiseKey);
        addGoal.acceleratorProperty().bind(configuration.addGoalKey);
        verifyLine.acceleratorProperty().bind(configuration.verifyLineKey);
        verifyProof.acceleratorProperty().bind(configuration.verifyProofKey);

        proof.getItems().addAll(addLine, deleteLine, startSubProof, endSubProof, newPremise, addGoal, verifyLine, verifyProof);

        // Help menu items

        MenuItem checkUpdate = new MenuItem("Check for updates");
        MenuItem helpItem = new MenuItem("Aris Help");
        MenuItem about = new MenuItem("About Aris");

        help.getItems().addAll(checkUpdate, helpItem, about);

        bar.getMenus().addAll(file, edit, proof, help);

        return bar;
    }

    private void openProof() {
        try {
            File f = SaveManager.showOpenDialog(primaryStage.getScene().getWindow());
            if (f == null || !f.exists()) {
                // TODO: show error
            } else {
                Proof p = SaveManager.loadFile(f);
                if (p == null) {
                    // TODO: show open error
                } else {
                    Aris.showProofWindow(new Stage(), p);
                }
            }
        } catch (IOException | TransformerException e) {
            // TODO: Show open error
            e.printStackTrace();
        }
    }

    private void saveProof(boolean saveAs) {
        try {
            if (saveAs || saveFile == null) {
                File f = SaveManager.showSaveDialog(primaryStage.getScene().getWindow(), saveFile == null ? "" : saveFile.getName());
                if (f != null)
                    saveFile = f;
            }
            SaveManager.saveProof(proof, saveFile);
        } catch (TransformerException | IOException e) {
            // TODO: Show save error
            e.printStackTrace();
        }
    }

    private void newProof() {
        try {
            Aris.showProofWindow(new Stage(), null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void verifyLine() {
        int lineNum = selectedLine.get();
        if (lineNum >= 0) {
            Proof.Line line = proof.getLines().get(lineNum);
            if (line.expressionStringProperty().get().trim().length() == 0 && line.selectedRuleProperty().get() != null) {
                Rule rule = line.selectedRuleProperty().get().rule;
                if (rule != null && rule.canAutoFill()) {
                    ArrayList<String> candidates = rule.getAutoFillCandidates(line.getClaimPremises());
                    if (candidates != null && candidates.size() > 0) {
                        HashSet<String> existingPremises = proof.getPossiblePremiseLines(line).stream().map(i -> proofLines.get(i).getText().replace(" ", "")).collect(Collectors.toCollection(HashSet::new));
                        for (String s : candidates) {
                            if (!existingPremises.contains(s.replace(" ", ""))) {
                                proofLines.get(lineNum).setText(s);
                                line.verifyClaim();
                                return;
                            }
                        }
                        proofLines.get(lineNum).setText(candidates.get(0));
                    }
                }
            }
            line.verifyClaim();
        }
    }

    private void startSubProof() {
        int level = proof.getLines().get(selectedLine.get()).subProofLevelProperty().get() + 1;
        int lineNum = selectedLine.get();
        selectedLine.set(-1);
        if (lineNum < proof.numPremises().get())
            lineNum = proof.numPremises().get();
        else if (proofLines.get(lineNum).getText().trim().length() == 0 && !proof.getLines().get(lineNum).isAssumption())
            deleteLine(lineNum);
        else
            lineNum++;
        addProofLine(true, level, lineNum);
        selectedLine.set(lineNum);
    }

    public void show() {
        primaryStage.show();
        selectedLine.set(0);
    }

    private synchronized void autoScroll(Bounds contentBounds) {
        HBox root = null;
        ScrollPane scroll = null;
        double startY = 0;
        if (selectedLine.get() >= 0) {
            root = proofLines.get(selectedLine.get()).getRootNode();
            scroll = scrollPane;
            for (int i = 0; i < proof.getLines().size(); ++i) {
                if (i < selectedLine.get()) {
                    startY += proofLines.get(i).getRootNode().getHeight();
                } else
                    break;
            }
        } else if (selectedLine.get() < -1) {
            root = goalLines.get(selectedLine.get() * -1 - 2).getRootNode();
            scroll = goalScroll;
            startY += goalLbl.getHeight();
            for (int i = 0; i < proof.getGoals().size(); ++i) {
                if (i < selectedLine.get() * -1 - 2) {
                    startY += goalLines.get(i).getRootNode().getHeight();
                } else
                    break;
            }
        }
        if (root != null && scroll != null && root.getHeight() != 0) {
            double downScroll = (startY + root.getHeight() - scroll.getHeight()) / (contentBounds.getHeight() - scroll.getHeight());
            double upScroll = (startY) / (contentBounds.getHeight() - scroll.getHeight());
            double currentScroll = scroll.getVvalue();
            if (currentScroll < downScroll) {
                scroll.setVvalue(downScroll);
            } else if (currentScroll > upScroll) {
                scroll.setVvalue(upScroll);
            }
        }
    }

    @FXML
    public void initialize() {
        scrollPane.getContent().boundsInLocalProperty().addListener((observableValue, oldBounds, newBounds) -> {
            if (oldBounds.getHeight() != newBounds.getHeight() && selectedLine.get() >= 0)
                autoScroll(newBounds);
        });
        goalScroll.getContent().boundsInLocalProperty().addListener((ov, oldVal, newBounds) -> {
            if (oldVal.getHeight() != newBounds.getHeight() && selectedLine.get() < -1)
                autoScroll(newBounds);
        });
        goalScroll.maxHeightProperty().bind(Bindings.createDoubleBinding(() -> Math.max(100, goalScroll.getContent().getBoundsInLocal().getHeight()), goalScroll.getContent().boundsInLocalProperty()));
        statusLbl.fontProperty().bind(fontObjectProperty);
        errorRangeLbl.fontProperty().bind(Bindings.createObjectBinding(() -> Font.font(fontObjectProperty.get().getFamily(), FontWeight.BOLD, fontObjectProperty.get().getSize()), fontObjectProperty));
        errorRangeLbl.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getEventType() == MouseEvent.MOUSE_CLICKED) {
                if (selectedLine.get() >= 0)
                    proofLines.get(selectedLine.get()).selectError();
                else if (selectedLine.get() < -1) {
                    goalLines.get(selectedLine.get() * -1 - 2).selectError();
                }
            }
        });
        rulesPane.getChildren().add(rulesManager.getRulesTable());
        VBox.setVgrow(rulesManager.getRulesTable(), Priority.ALWAYS);
        populateOperatorPane();
        if (proof.numLinesProperty().get() == 0) {
            addPremise();
            addGoal();
        } else {
            proof.getLines().forEach(this::addProofLine);
            proof.getGoals().forEach(this::addGoal);
            proof.verifyProof();
        }
        selectedLine.set(-1);
    }

    private void populateOperatorPane() {
        oprTitlePane.visibleProperty().bind(configuration.hideOperatorsPanel.not());
        oprTitlePane.managedProperty().bind(configuration.hideOperatorsPanel.not());
        oprTitlePane.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getEventType() == MouseEvent.MOUSE_CLICKED)
                oprTitlePane.setExpanded(!oprTitlePane.isExpanded());
        });
        operatorPane.setOnMouseClicked(Event::consume);
        operatorPane.setPadding(new Insets(3));
        operatorPane.setVgap(3);
        operatorPane.setHgap(3);
        for (int i = 0; i < ConfigurationManager.SYMBOL_BUTTONS.length; i++) {
            String sym = ConfigurationManager.SYMBOL_BUTTONS[i];
            Button btn = new Button(sym);
            operatorPane.add(btn, i % 6, i / 6);
            btn.setMaxWidth(Double.MAX_VALUE);
            GridPane.setHgrow(btn, Priority.ALWAYS);
            GridPane.setFillWidth(btn, true);
            btn.setOnAction(actionEvent -> insertString(sym));
        }
    }

    private void insertString(String str) {
        if (selectedLine.get() > -1) {
            ProofLine line = proofLines.get(selectedLine.get());
            line.insertText(str);
        } else if (selectedLine.get() < -1) {
            GoalLine line = goalLines.get(selectedLine.get() * -1 - 2);
            line.insertText(str);
        }
    }

    private synchronized void addProofLine(boolean assumption, int proofLevel, int index) {
        if (proofLevel < 0 || index < 0)
            return;
        addProofLine(proof.addLine(index, assumption, proofLevel));
    }

    private synchronized void addProofLine(Proof.Line line) {
        FXMLLoader loader = new FXMLLoader(MainWindow.class.getResource("proof_line.fxml"));
        ProofLine controller = new ProofLine(this, line);
        loader.setController(controller);
        HBox box = null;
        try {
            box = loader.load();
        } catch (IOException e) {
            e.printStackTrace();
        }
        int index = line.lineNumberProperty().get();
        proofTable.getChildren().add(index, box);
        proofLines.add(index, controller);
    }

    private synchronized int addPremise() {
        Proof.Line line = proof.addPremise();
        addProofLine(line);
        return line.lineNumberProperty().get();
    }

    private synchronized int addGoal() {
        Proof.Goal goal = proof.addGoal();
        return addGoal(goal);
    }

    private int addGoal(Proof.Goal goal) {
        FXMLLoader loader = new FXMLLoader(MainWindow.class.getResource("goal_line.fxml"));
        GoalLine controller = new GoalLine(this, goal);
        loader.setController(controller);
        HBox box = null;
        try {
            box = loader.load();
        } catch (IOException e) {
            e.printStackTrace();
        }
        int index = goal.goalNumProperty().get();
        VBox content = (VBox) goalScroll.getContent();
        content.getChildren().add(index + 1, box);
        goalLines.add(index, controller);
        return index;
    }

    public ObjectProperty<Font> getFontProperty() {
        return fontObjectProperty;
    }

    public synchronized void requestFocus(ProofLine line) {
        int index = proofLines.indexOf(line);
        requestFocus(index);
    }

    public void requestFocus(GoalLine line) {
        if (selectedLine.get() == -2 - line.lineNumber())
            return;
        selectedLine.set(-1);
        selectedLine.set(-2 - line.lineNumber());
    }

    private synchronized void requestFocus(int lineNum) {
        if (selectedLine.get() == lineNum)
            return;
        selectedLine.set(-1);
        selectedLine.set(lineNum);
    }

    public void requestSelect(ProofLine line) {
        proof.togglePremise(selectedLine.get(), line.getModel());
        updateHighlighting(selectedLine.get());
    }

    public IntegerProperty numLines() {
        return proof.numLinesProperty();
    }

    public boolean ignoreKeyEvent(KeyEvent event) {
        return configuration.ignore(event);
    }

    public IntegerProperty selectedLineProperty() {
        return selectedLine;
    }

    private synchronized void updateHighlighting(int selectedLine) {
        if (selectedLine >= 0) {
            Proof.Line line = proof.getLines().get(selectedLine);
            HashSet<Proof.Line> highlighted = proof.getHighlighted(line);
            if (line != null)
                for (ProofLine p : proofLines)
                    p.setHighlighted(highlighted.contains(p.getModel()) && p.getModel() != line);
        } else {
            for (ProofLine p : proofLines)
                p.setHighlighted(false);
        }
    }

    private synchronized void deleteLine(int lineNum) {
        if (lineNum > 0 || (proof.numPremises().get() > 1 && lineNum >= 0)) {
            if (lineNum >= proof.numPremises().get()) {
                Proof.Line line = proof.getLines().get(lineNum);
                if (line.isAssumption() && lineNum + 1 < proof.getLines().size()) {
                    int indent = line.subProofLevelProperty().get();
                    Proof.Line l = proof.getLines().get(lineNum + 1);
                    while (l != null && (l.subProofLevelProperty().get() > indent || (l.subProofLevelProperty().get() == indent && !l.isAssumption()))) {
                        removeLine(l.lineNumberProperty().get());
                        if (lineNum + 1 == proof.getLines().size())
                            l = null;
                        else
                            l = proof.getLines().get(lineNum + 1);
                    }
                }
            }
            removeLine(lineNum);
        } else if (lineNum < -1) {
            if (proof.getGoals().size() <= 1)
                return;
            lineNum = lineNum * -1 - 2;
            selectedLine.set(-1);
            proof.removeGoal(lineNum);
            goalLines.remove(lineNum);
            ((VBox) goalScroll.getContent()).getChildren().remove(lineNum + 1);
            if (lineNum >= proof.getGoals().size())
                lineNum = proof.getGoals().size() - 1;
            selectedLine.set(-2 - lineNum);
        }
    }

    private synchronized void removeLine(int lineNum) {
        int selected = selectedLine.get();
        selectedLine.set(-1);
        proofLines.remove(lineNum);
        proofTable.getChildren().remove(lineNum);
        proof.delete(lineNum);
        if (selected == proof.numLinesProperty().get())
            --selected;
        selectedLine.set(selected < 0 ? 0 : selected);
    }

    private void endSubproof() {
        int level = proof.getLines().get(selectedLine.get()).subProofLevelProperty().get();
        if (level == 0)
            return;
        int newLine = proof.getLines().size();
        for (int i = selectedLine.get() + 1; i < newLine; ++i) {
            Proof.Line l = proof.getLines().get(i);
            if (l.subProofLevelProperty().get() < level || (l.subProofLevelProperty().get() == level && l.isAssumption()))
                newLine = i;
        }
        addProofLine(false, level - 1, newLine);
        selectedLine.set(newLine);
    }

    public Stage getStage() {
        return primaryStage;
    }


    public RulesManager getRulesManager() {
        return rulesManager;
    }
}
