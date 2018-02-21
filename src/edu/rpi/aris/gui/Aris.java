package edu.rpi.aris.gui;

import javafx.application.Application;
import javafx.stage.Stage;

import java.io.IOException;

public class Aris extends Application {

    private static boolean GUI = false;

    public static void main(String[] args) {
        Aris.launch(args);
    }

    public static boolean isGUI() {
        return GUI;
    }

    @Override
    public void start(Stage stage) throws IOException {
        GUI = true;
        MainWindow controller = new MainWindow(stage);
        controller.show();
    }
}
