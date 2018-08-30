package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.ArisModuleException;
import edu.rpi.aris.assign.LibAssign;
import edu.rpi.aris.assign.client.ClientModuleService;
import edu.rpi.aris.assign.client.guiold.AssignmentWindow;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;

public class ModuleSelect {

    private final Stage stage;

    @FXML
    private VBox moduleBox;
    private AssignmentWindow assignmentWindow;

    public ModuleSelect(Stage stage) {
        this.stage = stage;
        FXMLLoader loader = new FXMLLoader(ModuleRow.class.getResource("../view/module_select.fxml"));
        loader.setController(this);
        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, true);
            return;
        }
        Scene scene = new Scene(root);
        stage.setScene(scene);
    }

    private void loadModules() {
        for (String moduleName : ClientModuleService.getService().moduleNames())
            moduleBox.getChildren().add(new ModuleRow(moduleName).getRoot());
    }

    public void displayErrorMsg(String title, String msg) {
        displayErrorMsg(title, msg, false);
    }

    public void displayErrorMsg(String title, String msg, boolean wait) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(title);
            alert.setContentText(msg);
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            alert.initOwner(stage);
            alert.initModality(Modality.APPLICATION_MODAL);
            if (wait) {
                alert.showAndWait();
                synchronized (title) {
                    title.notify();
                }
            } else
                alert.show();
        });
        if (wait)
            synchronized (title) {
                try {
                    title.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
    }

    @FXML
    public void initialize() {
        stage.setTitle("Aris Assign - Module Select");
        stage.getIcons().add(new Image(LibAssign.class.getResourceAsStream("aris-assign.png")));
        loadModules();
    }

    @FXML
    public void showSettings() {
        ConfigGui.getInstance().show();
    }

    @FXML
    public void showAssignments() {
        AssignGui.getInstance().show();
    }

    @FXML
    public void refreshModules() {
        moduleBox.getChildren().clear();
        try {
            ClientModuleService.getService().reloadModules();
        } catch (Exception e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, false);
        }
        loadModules();
    }

    @FXML
    public void addModule() {

    }

    public Stage getStage() {
        return stage;
    }

    public void show() {
        stage.show();
    }

}
