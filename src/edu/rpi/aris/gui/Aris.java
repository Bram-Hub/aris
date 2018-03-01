package edu.rpi.aris.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public class Aris extends Application implements Thread.UncaughtExceptionHandler {

    public static final String VERSION = "0.1";

    private static Logger logger = LogManager.getLogger(Aris.class);

    private static boolean GUI = false;

    private MainWindow mainWindow = null;

    public static void main(String[] args) {
        Aris.launch(args);
    }

    public static boolean isGUI() {
        return GUI;
    }

    public static MainWindow showProofWindow(Stage stage) throws IOException {
        MainWindow window = new MainWindow(stage);
        window.show();
        return window;
    }

    @Override
    public void start(Stage stage) throws IOException {
        Thread.setDefaultUncaughtExceptionHandler(this);
        GUI = true;
        mainWindow = showProofWindow(stage);
    }

    private void generateBugReport(Thread t, Throwable e) {
        //TODO
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        logger.error("He's dead, Jim!");
        logger.catching(e);
        Thread bugReportThread = new Thread(() -> generateBugReport(t, e));
        bugReportThread.start();
        if (GUI) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);

                alert.initModality(Modality.APPLICATION_MODAL);
                alert.initOwner(mainWindow.getStage().getScene().getWindow());

                alert.getDialogPane().setPrefHeight(Region.USE_COMPUTED_SIZE);
                alert.getDialogPane().setPrefWidth(Region.USE_COMPUTED_SIZE);

                alert.getDialogPane().setPrefWidth(600);
                alert.getDialogPane().setPrefHeight(500);

                alert.setTitle("Critical Error");
                alert.setHeaderText("He's dead, Jim!");
                alert.setContentText("An error has occurred and Aris has attempted to recover\n" +
                        "A bug report was generated and sent to the Aris developers\n" +
                        "It is recommended to restart the program in case Aris was unable to fully recover");

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
                expContent.setMinHeight(300);
                expContent.setMaxWidth(Double.MAX_VALUE);
                expContent.add(label, 0, 0);
                expContent.add(textArea, 0, 1);

                alert.getDialogPane().setExpandableContent(expContent);
                alert.getDialogPane().setExpanded(true);

                alert.showAndWait();
            });
        }
        try {
            bugReportThread.join();
        } catch (InterruptedException e1) {
            logger.error("Interrupted while sending bug report", e1);
        }
    }
}
