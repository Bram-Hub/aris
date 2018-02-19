package edu.rpi.aris.gui;

import javafx.application.Application;
import javafx.stage.Stage;

import java.io.IOException;

public class Aris extends Application {

    public static void main(String[] args) {
        Aris.launch(args);
    }

    @Override
    public void start(Stage stage) throws IOException {
        MainWindow controller = new MainWindow(stage);
        controller.show();
    }
}
