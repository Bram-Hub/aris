package edu.rpi.aris.assign.client.gui;

import edu.rpi.aris.assign.ArisModuleException;
import edu.rpi.aris.assign.LibAssign;
import edu.rpi.aris.assign.client.ClientModuleService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;

public class ModuleSelect {

    private final Stage stage;

    @FXML
    private VBox moduleBox;
    private AssignmentWindow assignmentWindow;

    public ModuleSelect(Stage stage) {
        this.stage = stage;
        FXMLLoader loader = new FXMLLoader(ModuleRow.class.getResource("module_select.fxml"));
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

    @FXML
    public void initialize() {
        stage.setTitle("Aris Assign - Module Select");
        stage.getIcons().add(new Image(LibAssign.class.getResourceAsStream("aris-assign.png")));
        loadModules();
    }

    @FXML
    public void showSettings() {
        Config.getInstance().show();
    }

    @FXML
    public void showAssignments() {
        if (assignmentWindow == null)
            assignmentWindow = new AssignmentWindow();
        assignmentWindow.show();
    }

    @FXML
    public void refreshModules() {
        moduleBox.getChildren().clear();
        try {
            ClientModuleService.getService().reloadModules();
        } catch (ArisModuleException e) {
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
