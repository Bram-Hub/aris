package edu.rpi.aris.assign;

import java.io.PrintWriter;
import java.io.StringWriter;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JavaFXDialogArisExceptionHandler implements ArisExceptionHandler {
    private static final Logger logger = LogManager.getLogger(JavaFXDialogArisExceptionHandler.class);

    @Override
    public void uncaughtException(Thread t, Throwable e, boolean fatal) {
        Platform.runLater(() -> {
            try {
                Alert alert = new Alert(Alert.AlertType.ERROR);

                //if (mainWindow != null) {
                //    alert.initModality(Modality.APPLICATION_MODAL);
                //    alert.initOwner(mainWindow.getStage().getScene().getWindow());
                //}

                alert.getDialogPane().setPrefHeight(Region.USE_COMPUTED_SIZE);
                alert.getDialogPane().setPrefWidth(Region.USE_COMPUTED_SIZE);

                alert.getDialogPane().setPrefWidth(600);
                alert.getDialogPane().setPrefHeight(500);

                String[] messages = new String[]{
                    "An error has occurred and Aris was unable to recover",
                    //"A bug report was generated and sent to the Aris developers",
                    "Please submit the content of the error to the Aris developers",
                    "It is recommended to restart the program in case Aris was unable to fully recover"
                };
                alert.setTitle("Critical Error");
                if (fatal) {
                    alert.setHeaderText("He's dead, Jim!");
                    alert.setContentText(String.format("%s\n%s", messages[0], messages[1]));
                } else {
                    alert.setHeaderText("99 little bugs in the code\n" +
                            "99 little bugs\n" +
                            "Take one down, patch it around\n" +
                            "137 little bugs in the code");
                    alert.setContentText(String.format("%s\n%s\n%s", messages[0], messages[1], messages[2]));
                }

                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                pw.println(LibAssign.NAME + " " + LibAssign.VERSION);
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
            } catch (Throwable e1) {
                logger.fatal("An error has occurred while attempting to show the error dialog");
                logger.catching(Level.FATAL, e1);
                logger.fatal("The program will now exit");
                System.exit(1);
            }
        });
    }
}
