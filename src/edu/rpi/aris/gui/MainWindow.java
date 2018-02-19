package edu.rpi.aris.gui;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;

public class MainWindow {

    @FXML
    private VBox proofTable;
    @FXML
    private ScrollPane scrollPane;

    private ObjectProperty<Font> fontObjectProperty;
    private ArrayList<ProofLine> proofLines = new ArrayList<>();
    private SimpleIntegerProperty selectedLine = new SimpleIntegerProperty(-1);
    private Proof proof = new Proof();
    private Stage primaryStage;
    private AcceleratorManager accelerators = new AcceleratorManager();

    public MainWindow(Stage primaryStage) throws IOException {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("ARIS");
        fontObjectProperty = new SimpleObjectProperty<>(new Font(14));
        setupScene();
        selectedLine.addListener((observableValue, oldVal, newVal) -> updateHighlighting(newVal.intValue()));
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
    }

    private MenuBar setupMenu() {
        MenuBar bar = new MenuBar();

        Menu file = new Menu("File");

        MenuItem addLine = new MenuItem("Add Line");
        MenuItem deleteLine = new MenuItem("Delete Line");
        MenuItem startSubproof = new MenuItem("Start Subproof");
        MenuItem endSubproof = new MenuItem("End Subproof");
        MenuItem newPremise = new MenuItem("");

        addLine.setOnAction(actionEvent -> {
            addProofLine(false, proof.getLines().get(selectedLine.get()).subproofLevelProperty().get(), selectedLine.get() + 1);
            selectedLine.set(selectedLine.get() + 1);
        });

        deleteLine.setOnAction(actionEvent -> deleteLine(selectedLine.get()));

        startSubproof.setOnAction(actionEvent -> startSubproof());

        endSubproof.setOnAction(actionEvent -> endSubproof());

        newPremise.setOnAction(actionEvent -> addPremise());

        addLine.acceleratorProperty().bind(accelerators.newProofLine);
        deleteLine.acceleratorProperty().bind(accelerators.deleteProofLine);
        startSubproof.acceleratorProperty().bind(accelerators.startSubproof);
        endSubproof.acceleratorProperty().bind(accelerators.endSubproof);
        newPremise.acceleratorProperty().bind(accelerators.newPremise);

        file.getItems().addAll(addLine, deleteLine, startSubproof, endSubproof, newPremise);

        bar.getMenus().addAll(file);

        return bar;
    }

    private void startSubproof() {
        int level = proof.getLines().get(selectedLine.get()).subproofLevelProperty().get() + 1;
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

    @FXML
    public void initialize() {
        scrollPane.getContent().boundsInLocalProperty().addListener((observableValue, oldBounds, newBounds) -> {
            if (oldBounds.getHeight() != newBounds.getHeight()) {
                synchronized (MainWindow.class) {
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
                            double downScroll = (startY + line.getRootNode().getHeight() - scrollPane.getHeight()) / (newBounds.getHeight() - scrollPane.getHeight());
                            double upScroll = (startY) / (newBounds.getHeight() - scrollPane.getHeight());
                            double currentScroll = scrollPane.getVvalue();
                            if (currentScroll < downScroll) {
                                scrollPane.setVvalue(downScroll);
                            } else if (currentScroll > upScroll) {
                                scrollPane.setVvalue(upScroll);
                            }
                        }
                    }
                }
            }
        });
        addPremise();
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

    private synchronized void addPremise() {
        Proof.Line line = proof.addPremise();
        addProofLine(line);
        selectedLine.set(line.lineNumberProperty().get());
    }

    public ObjectProperty<Font> getFontProperty() {
        return fontObjectProperty;
    }

    public synchronized void requestFocus(ProofLine line) {
        int index = proofLines.indexOf(line);
        selectedLine.set(-1);
        selectedLine.set(index);
    }

    public void requestSelect(ProofLine line) {
        proof.togglePremise(selectedLine.get(), line.getModel());
        updateHighlighting(selectedLine.get());
    }

    public IntegerProperty numLines() {
        return proof.numLinesProperty();
    }

    public boolean ignoreKeyEvent(KeyEvent event) {
        return accelerators.ignore(event);
    }

    public IntegerProperty selectedLineProperty() {
        return selectedLine;
    }

    private synchronized void updateHighlighting(int selectedLine) {
        if (selectedLine >= 0) {
            Proof.Line line = proof.getLines().get(selectedLine);
            if (line != null)
                for (ProofLine p : proofLines)
                    p.setHighlighted(line.getHighlightLines().contains(p.getModel()) && p.getModel() != line);
        }
    }

    private synchronized void deleteLine(int lineNum) {
        if (lineNum > 0 || (proof.numPremises().get() > 1 && lineNum >= 0)) {
            Proof.Line line = proof.getLines().get(lineNum);
            if (line.isAssumption() && lineNum + 1 < proof.getLines().size()) {
                int indent = line.subproofLevelProperty().get();
                Proof.Line l = proof.getLines().get(lineNum + 1);
                while (l != null && (l.subproofLevelProperty().get() > indent || (l.subproofLevelProperty().get() == indent && !l.isAssumption()))) {
                    removeLine(l.lineNumberProperty().get());
                    if (lineNum + 1 == proof.getLines().size())
                        l = null;
                    else
                        l = proof.getLines().get(lineNum + 1);
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
        int level = proof.getLines().get(selectedLine.get()).subproofLevelProperty().get();
        if (level == 0)
            return;
        int newLine = proof.getLines().size();
        for (int i = selectedLine.get() + 1; i < newLine; ++i) {
            Proof.Line l = proof.getLines().get(i);
            if (l.subproofLevelProperty().get() < level || (l.subproofLevelProperty().get() == level && l.isAssumption()))
                newLine = i;
        }
        addProofLine(false, level - 1, newLine);
        selectedLine.set(newLine);
    }

}
