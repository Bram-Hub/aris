package edu.rpi.aris.gui;

import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public class Aris extends Application implements Thread.UncaughtExceptionHandler {

    public static final String VERSION = "0.1";

    private static Logger logger = LogManager.getLogger(Aris.class);

    private static boolean GUI = false;

    public static void main(String[] args) {
        Aris.launch(args);
    }

    public static boolean isGUI() {
        return GUI;
    }

    @Override
    public void start(Stage stage) throws IOException {
        Thread.setDefaultUncaughtExceptionHandler(this);
        GUI = true;
        MainWindow controller = new MainWindow(stage);
        controller.show();
    }

    private void generateBugReport(Thread t, Throwable e) {
        //TODO
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        logger.fatal("He's dead, Jim!");
        logger.catching(Level.FATAL, e);
        Thread bugReportThread = new Thread(() -> generateBugReport(t, e));
        bugReportThread.start();
        if (GUI) {
            Alert alert = new Alert(Alert.AlertType.ERROR);

            alert.getDialogPane().setPrefHeight(Region.USE_COMPUTED_SIZE);
            alert.getDialogPane().setPrefWidth(Region.USE_COMPUTED_SIZE);

            alert.getDialogPane().setPrefWidth(600);

            alert.setTitle("Fatal Error");
            alert.setHeaderText("He's dead, Jim!");
            alert.setContentText("A fatal error has occurred and Aris was unable to recover\nA bug report was generated and sent to the Aris developers");

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println("ARIS-Java " + VERSION);
            e.printStackTrace(pw);
            String exceptionText = sw.toString();

            Label label = new Label("Error details:");

            TextArea textArea = new TextArea(exceptionText);
            textArea.setEditable(false);
            textArea.setWrapText(false);

            textArea.setMaxWidth(Double.MAX_VALUE);
            textArea.setMaxHeight(Double.MAX_VALUE);
            GridPane.setVgrow(textArea, Priority.ALWAYS);
            GridPane.setHgrow(textArea, Priority.ALWAYS);

            GridPane expContent = new GridPane();
            expContent.setMaxWidth(Double.MAX_VALUE);
            expContent.add(label, 0, 0);
            expContent.add(textArea, 0, 1);

            alert.getDialogPane().setExpandableContent(expContent);

            alert.showAndWait();
        }
        try {
            bugReportThread.join();
        } catch (InterruptedException e1) {
            logger.error("Interrupted while sending bug report", e1);
        }
        System.exit(1);
    }
}
