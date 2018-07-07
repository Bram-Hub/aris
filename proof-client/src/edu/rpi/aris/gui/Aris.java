package edu.rpi.aris.gui;

import edu.rpi.aris.assign.EditMode;
import edu.rpi.aris.proof.Proof;
import javafx.application.Application;
import javafx.stage.Stage;

import java.io.IOException;

public class Aris extends Application {

    public static Aris instance = null;

    private MainWindow mainWindow = null;

    public static MainWindow showProofWindow(Stage stage, Proof p) throws IOException {
        MainWindow window = p == null ? new MainWindow(stage, EditMode.UNRESTRICTED) : new MainWindow(stage, p, EditMode.UNRESTRICTED);
        window.show();
        return window;
    }

    public static Aris getInstance() {
        return instance;
    }

    @Override
    public void start(Stage stage) throws IOException {
        instance = this;
        mainWindow = showProofWindow(stage, null);
    }

    public MainWindow getMainWindow() {
        return mainWindow;
    }
}
