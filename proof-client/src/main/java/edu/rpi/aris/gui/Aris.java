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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class Aris extends Application implements ArisClientModule<LibAris>, SaveInfoListener {

    private static final Logger log = LogManager.getLogger();
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

    private <T> T onFXThread(Supplier<T> runnable) throws Exception {
        if (Platform.isFxApplicationThread()) {
            log.warn("onFXThread called while already on the FX application thread", new Exception("Stack Trace"));
            return runnable.get();
        }
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> exception = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(runnable.get());
            } catch (Exception e) {
                exception.set(e);
            } finally {
                synchronized (runnable) {
                    runnable.notifyAll();
                }
            }
        });
        synchronized (runnable) {
            while (result.get() == null && exception.get() == null) {
                try {
                    runnable.wait();
                } catch (InterruptedException e) {
                    log.error(e);
                }
            }
        }
        if (exception.get() != null)
            throw exception.get();
        return result.get();
    }

    @Override
    public void start(Stage stage) throws IOException {
        instance = this;
        LibAssign libAssign = LibAssign.getInstance();
        JavaFXDialogArisExceptionHandler exceptionHandler = new JavaFXDialogArisExceptionHandler();
        libAssign.setArisExceptionHandler(exceptionHandler);
        Thread.setDefaultUncaughtExceptionHandler(libAssign);
        /*mainWindow = */
        showProofWindow(stage, null);
    }

    @NotNull
    @Override
    public MainWindow createModuleGui(@NotNull ModuleUIOptions options) throws Exception {
        return createModuleGui(options, null);
    }

    @NotNull
    @Override
    public MainWindow createModuleGui(@NotNull ModuleUIOptions options, @NotNull Problem<LibAris> problem) throws Exception {
        return onFXThread(() -> {
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
                throw new ArisRuntimeException("Failed to create " + LibAris.NAME + " window", e);
            }
        });
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
