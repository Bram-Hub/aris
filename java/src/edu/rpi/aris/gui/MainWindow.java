package edu.rpi.aris.gui;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Node;
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
import org.apache.commons.collections.BidiMap;
import org.apache.commons.collections.bidimap.DualHashBidiMap;

import java.io.IOException;

public class MainWindow {

    @FXML
    private VBox proofTable;
    @FXML
    private ScrollPane scrollPane;

    private ObjectProperty<Font> fontObjectProperty;
    private BidiMap proofLines = new DualHashBidiMap();
    private SimpleIntegerProperty selectedLine = new SimpleIntegerProperty(-1);
    private Proof proof = new Proof();
    private Stage primaryStage;
    private AcceleratorManager accelerators = new AcceleratorManager();

    public MainWindow(Stage primaryStage) throws IOException {
        this.primaryStage = primaryStage;
        fontObjectProperty = new SimpleObjectProperty<>(new Font(14));
        setupScene();
    }

    private void setupScene() throws IOException {
        FXMLLoader loader = new FXMLLoader(Aris.class.getResource("main_window.fxml"));
        loader.setController(this);
        Parent root = loader.load();
        MenuBar bar = setupMenu();
        BorderPane pane = new BorderPane();
        pane.setTop(bar);
        pane.setCenter(root);
        Scene scene = new Scene(pane/*, 1000, 800*/);
        primaryStage.setScene(scene);
    }

    private MenuBar setupMenu() {
        MenuBar bar = new MenuBar();

        Menu file = new Menu("File");

        MenuItem addLine = new MenuItem("Add Line");

        addLine.setOnAction(actionEvent -> {
            addProofLine(false, 0, selectedLine.get() + 1);
            selectedLine.set(selectedLine.get() + 1);
        });

        addLine.acceleratorProperty().bind(accelerators.newProofLine);

        file.getItems().addAll(addLine);

        bar.getMenus().addAll(file);

        return bar;
    }

    public void show() {
        primaryStage.show();
        selectedLine.set(0);
    }

    @FXML
    public void initialize() {
        scrollPane.getContent().boundsInLocalProperty().addListener((observableValue, oldBounds, newBounds) -> {
            if (oldBounds.getHeight() != newBounds.getHeight()) {
                ProofLine line = (ProofLine) proofLines.get(selectedLine.get());
                if (line != null && line.getRootNode().getHeight() != 0) {
                    double startY = 0;
                    double endY = newBounds.getHeight();
                    for (int i = 0; i < proof.getLines().size(); ++i) {
                        if (i < selectedLine.get()) {
                            startY += ((ProofLine) proofLines.get(i)).getRootNode().getHeight();
                        } else if (i > selectedLine.get())
                            endY -= ((ProofLine) proofLines.get(i)).getRootNode().getHeight();
                    }
                    double startScroll = startY / newBounds.getHeight();
                    double endScroll = (startY + scrollPane.getHeight()) / newBounds.getHeight();
                    System.out.println("Start: " + startScroll);
                    System.out.println("End:   " + endScroll);
                    //TODO: auto scroll if necessary
                }
            }
        });
        scrollPane.getContent().boundsInLocalProperty().addListener(new ChangeListener<Bounds>() {
            @Override
            public void changed(ObservableValue<? extends Bounds> observableValue, Bounds bounds, Bounds t1) {
//                System.out.println(t1);
            }
        });
        addProofLine(true, 0, 0);
    }

    public synchronized void addProofLine(boolean assumption, int proofLevel, int index) {
        FXMLLoader loader = new FXMLLoader(MainWindow.class.getResource("proof_line.fxml"));
        ProofLine controller = new ProofLine(assumption, proofLevel, this, proof.addLine(index));
        loader.setController(controller);
        HBox line = null;
        try {
            line = loader.load();
        } catch (IOException e) {
            e.printStackTrace();
        }
        proofTable.getChildren().add(index, line);
        proofLines.put(index, controller);
    }

    public ObjectProperty<Font> getFontProperty() {
        return fontObjectProperty;
    }

    public synchronized void requestFocus(ProofLine line) {
        int index = (int) proofLines.getKey(line);
        selectedLine.set(index);
    }

    public void requestSelect(ProofLine line) {
        line.setHighlighted(true);
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

}
