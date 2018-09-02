package edu.rpi.aris.gui;

import edu.rpi.aris.LibAris;
import edu.rpi.aris.assign.*;
import edu.rpi.aris.proof.ArisProofProblem;
import edu.rpi.aris.proof.Proof;
import edu.rpi.aris.proof.SaveInfoListener;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class Aris extends Application implements ArisClientModule<LibAris>, SaveInfoListener {

    private static Aris instance = null;

//    private MainWindow mainWindow = null;

    public Aris() {
    }

    public static void main(String[] args) {
        launch(args);
    }

    public static MainWindow showProofWindow(Stage stage, Proof p) throws IOException {
        MainWindow window = p == null ? new MainWindow(stage, EditMode.UNRESTRICTED_EDIT) : new MainWindow(stage, p, EditMode.UNRESTRICTED_EDIT);
        window.show();
        return window;
    }

    public static Aris getInstance() {
        if (instance == null)
            instance = new Aris();
        return instance;
    }

    @Override
    public void start(Stage stage) throws IOException {
        instance = this;
        /*mainWindow = */
        showProofWindow(stage, null);
    }

//    public MainWindow getMainWindow() {
//        return mainWindow;
//    }

    @Override
    public MainWindow createModuleGui(ModuleUIOptions options) throws Exception {
        return createModuleGui(options, null);
    }

    @Override
    public MainWindow createModuleGui(ModuleUIOptions options, Problem<LibAris> problem) throws Exception {
        try {
            EditMode editMode = options.getEditMode();
            if (editMode == EditMode.CREATE_EDIT_PROBLEM)
                editMode = EditMode.UNRESTRICTED_EDIT;
            MainWindow window;
            if (problem instanceof ArisProofProblem)
                window = new MainWindow(new Stage(), ((ArisProofProblem) problem).getProof(), editMode);
            else
                window = new MainWindow(new Stage(), editMode);
            window.setUIOptions(options);
            return window;
        } catch (IOException e) {
            throw new ArisModuleException("Failed to create " + LibAris.NAME + " window", e);
        }
    }

    @Override
    public boolean notArisFile(String filename, String programName, String programVersion) {
        AtomicInteger result = new AtomicInteger(-1);
        Platform.runLater(() -> {
            Alert noAris = new Alert(Alert.AlertType.CONFIRMATION);
            noAris.setTitle("Not Aris File");
            noAris.setHeaderText("Not Aris File");
            noAris.setContentText("The given file \"" + filename + "\" was written by " + programName + " version " + programVersion + "\n" +
                    "Aris may still be able to read this file with varying success\n" +
                    "Would you like to attempt to load this file?");
            Optional<ButtonType> option = noAris.showAndWait();
            synchronized (result) {
                result.set(option.isPresent() && option.get() == ButtonType.YES ? 1 : 0);
                result.notify();
            }
        });
        while (result.get() == -1) {
            synchronized (result) {
                try {
                    result.wait();
                } catch (InterruptedException e) {
                }
            }
        }
        return result.get() == 1;
    }

    @Override
    public void integrityCheckFailed(String filename) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("File integrity check failed");
            alert.setHeaderText("File integrity check failed");
            alert.setContentText("This file may be corrupted or may have been tampered with.\n" +
                    "If this file successfully loads the author will be marked as UNKNOWN.\n" +
                    "This will show up if this file is submitted and may affect your grade.");
            alert.getDialogPane().setPrefWidth(500);
            alert.showAndWait();
        });
    }

}
