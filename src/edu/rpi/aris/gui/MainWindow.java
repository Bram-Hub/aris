package edu.rpi.aris.gui;

import edu.rpi.aris.rules.Rule;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.Collectors;

public class MainWindow {

    @FXML
    private VBox proofTable;
    @FXML
    private ScrollPane scrollPane;
    @FXML
    private Label statusLbl;

    private ObjectProperty<Font> fontObjectProperty;
    private ArrayList<ProofLine> proofLines = new ArrayList<>();
    private SimpleIntegerProperty selectedLine = new SimpleIntegerProperty(-1);
    private Proof proof = new Proof();
    private Stage primaryStage;
    private ConfigurationManager configuration = new ConfigurationManager();

    public MainWindow(Stage primaryStage) throws IOException {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("ARIS");
        fontObjectProperty = new SimpleObjectProperty<>(new Font(14));
        setupScene();
        selectedLine.addListener((observableValue, oldVal, newVal) -> {
            if (selectedLine.get() >= 0) {
                statusLbl.textProperty().bind(proof.getLines().get(selectedLine.get()).statusMsgProperty());
                proof.getLines().get(newVal.intValue()).verifyClaim();
            } else
                statusLbl.textProperty().unbind();
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
        if (keyEvent.getCode() == KeyCode.UP)
            lineUp();
        else if (keyEvent.getCode() == KeyCode.DOWN)
            lineDown();
        else
            return false;
        return true;
    }

    private synchronized void lineUp() {
        if (selectedLine.get() > 0) {
            requestFocus(selectedLine.get() - 1);
            autoScroll(scrollPane.getContent().getBoundsInLocal());
        }
    }

    private synchronized void lineDown() {
        if (selectedLine.get() + 1 < proof.numLinesProperty().get()) {
            requestFocus(selectedLine.get() + 1);
            autoScroll(scrollPane.getContent().getBoundsInLocal());
        }
    }

    private MenuBar setupMenu() {
        MenuBar bar = new MenuBar();

        Menu file = new Menu("File");

        MenuItem addLine = new MenuItem("Add Line");
        MenuItem deleteLine = new MenuItem("Delete Line");
        MenuItem startSubproof = new MenuItem("Start Subproof");
        MenuItem endSubproof = new MenuItem("End Subproof");
        MenuItem newPremise = new MenuItem("Add Premise");
        MenuItem verifyLine = new MenuItem("Verify Line");

        addLine.setOnAction(actionEvent -> {
            addProofLine(false, proof.getLines().get(selectedLine.get()).subProofLevelProperty().get(), selectedLine.get() + 1);
            selectedLine.set(selectedLine.get() + 1);
        });

        deleteLine.setOnAction(actionEvent -> deleteLine(selectedLine.get()));

        startSubproof.setOnAction(actionEvent -> startSubproof());

        endSubproof.setOnAction(actionEvent -> endSubproof());

        newPremise.setOnAction(actionEvent -> {
            selectedLine.set(-1);
            selectedLine.set(addPremise());
        });

        verifyLine.setOnAction(actionEvent -> verifyLine());

        addLine.acceleratorProperty().bind(configuration.newProofLineKey);
        deleteLine.acceleratorProperty().bind(configuration.deleteProofLineKey);
        startSubproof.acceleratorProperty().bind(configuration.startSubProofKey);
        endSubproof.acceleratorProperty().bind(configuration.endSubProofKey);
        newPremise.acceleratorProperty().bind(configuration.newPremiseKey);
        verifyLine.acceleratorProperty().bind(configuration.verifyLineKey);

        file.getItems().addAll(addLine, deleteLine, startSubproof, endSubproof, newPremise, verifyLine);

        bar.getMenus().addAll(file);

        return bar;
    }

    private synchronized void verifyLine() {
        int lineNum = selectedLine.get();
        if (lineNum >= 0) {
            Proof.Line line = proof.getLines().get(lineNum);
            if (line.expressionStringProperty().get().trim().length() == 0) {
                Rule rule = line.selectedRuleProperty().get().rule;
                if (rule != null && rule.canAutoFill()) {
                    ArrayList<String> candidates = rule.getAutoFillCandidates(line.getClaimPremises());
                    if (candidates != null && candidates.size() > 0) {
                        HashSet<String> existingPremises = proof.getPossiblePremiseLines(line).stream().map(i -> proofLines.get(i).getText().replace(" ", "")).collect(Collectors.toCollection(HashSet::new));
                        for(String s : candidates) {
                            if(!existingPremises.contains(s.replace(" ", ""))) {
                                proofLines.get(lineNum).setText(s);
                                proof.getLines().get(lineNum).verifyClaim();
                                return;
                            }
                        }
                        proofLines.get(lineNum).setText(candidates.get(0));
                    }
                }
            }
            proof.getLines().get(lineNum).verifyClaim();
        }
    }

    private void startSubproof() {
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
        if (selectedLine.get() >= 0) {
            ProofLine line = proofLines.get(selectedLine.get());
            if (line != null && line.getRootNode().getHeight() != 0) {
                double startY = 0;
                for (int i = 0; i < proof.getLines().size(); ++i) {
                    if (i < selectedLine.get()) {
                        startY += proofLines.get(i).getRootNode().getHeight();
                    } else
                        break;
                }
                double downScroll = (startY + line.getRootNode().getHeight() - scrollPane.getHeight()) / (contentBounds.getHeight() - scrollPane.getHeight());
                double upScroll = (startY) / (contentBounds.getHeight() - scrollPane.getHeight());
                double currentScroll = scrollPane.getVvalue();
                if (currentScroll < downScroll) {
                    scrollPane.setVvalue(downScroll);
                } else if (currentScroll > upScroll) {
                    scrollPane.setVvalue(upScroll);
                }
            }
        }
    }

    @FXML
    public void initialize() {
        scrollPane.getContent().boundsInLocalProperty().addListener((observableValue, oldBounds, newBounds) -> {
            if (oldBounds.getHeight() != newBounds.getHeight())
                autoScroll(newBounds);
        });
        addPremise();
        selectedLine.set(-1);
        statusLbl.fontProperty().bind(fontObjectProperty);
    }

    private synchronized void addProofLine(boolean assumption, int proofLevel, int index) {
        if (proofLevel < 0)
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

    public ObjectProperty<Font> getFontProperty() {
        return fontObjectProperty;
    }

    public synchronized void requestFocus(ProofLine line) {
        int index = proofLines.indexOf(line);
        requestFocus(index);
    }

    private synchronized void requestFocus(int lineNum) {
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

}
