package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.LibAssign;
import edu.rpi.aris.assign.client.AssignClient;
import edu.rpi.aris.assign.client.model.Config;
import edu.rpi.aris.assign.client.model.UserInfo;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Pair;

import java.io.IOException;
import java.util.Optional;

public class AssignGui {

    private static AssignGui instance;
    @FXML
    private ChoiceBox<Pair<String, Integer>> classes;
    @FXML
    private ProgressIndicator loading;
    @FXML
    private Label lblUsername;
    @FXML
    private Button login;
    @FXML
    private Tab assignmentTab;
    @FXML
    private Tab studentTab;
    @FXML
    private Tab problemTab;

    private UserInfo userInfo = new UserInfo();
    private Stage stage;

    private AssignGui() {
        stage = new Stage();
        FXMLLoader loader = new FXMLLoader(ModuleRow.class.getResource("../view/assignment_window.fxml"));
        loader.setController(this);
        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, true);
            return;
        }
        Scene scene = new Scene(root, 600, 400);
        stage.setScene(scene);
        stage.initOwner(AssignClient.getInstance().getMainWindow().getStage());
    }

    public static AssignGui getInstance() {
        if (instance == null)
            instance = new AssignGui();
        return instance;
    }

    public UserInfo getUserInfo() {
        return userInfo;
    }

    public void show() {
        if (stage.isShowing())
            stage.requestFocus();
        else
            stage.show();
        checkServer();
    }

    public void checkServer() {
        String server = Config.SERVER_ADDRESS.getValue();
        if (server == null || server.trim().length() == 0) {
            TextInputDialog serverDialog = new TextInputDialog();
            serverDialog.setTitle("Server Address");
            serverDialog.setHeaderText("Enter the address of the server");
            serverDialog.setContentText("Server:");
            serverDialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
            serverDialog.initOwner(stage);
            serverDialog.initModality(Modality.APPLICATION_MODAL);
            Optional<String> result = serverDialog.showAndWait();
            if (result.isPresent()) {
                Config.SERVER_ADDRESS.setValue(result.get());
            } else {
                stage.hide();
            }
        }
        userInfo.getUserInfo(false);
    }

    @FXML
    public void initialize() {
        classes.setConverter(new UserInfo.ClassStringConverter());
        classes.itemsProperty().set(userInfo.classesProperty());

        loading.visibleProperty().bind(userInfo.loadingProperty());
        loading.managedProperty().bind(userInfo.loadingProperty());

        login.visibleProperty().bind(userInfo.loginProperty().not());
        login.managedProperty().bind(userInfo.loginProperty().not());

        lblUsername.textProperty().bind(Bindings.createStringBinding(() -> userInfo.isLoggedIn() ? Config.USERNAME.getValue() + " (" + userInfo.getUserType().readableName + ")" : "Not Logged In", Config.USERNAME.getProperty(), userInfo.userTypeProperty(), userInfo.loginProperty()));

    }

    @FXML
    public void refresh() {

    }

    @FXML
    public void createClass() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create Class");
        dialog.setHeaderText("Create a new class");
        dialog.setContentText("Class Name:");
        dialog.initOwner(stage);
        dialog.initModality(Modality.WINDOW_MODAL);
        Optional<String> result = dialog.showAndWait();
        new Thread(() -> result.ifPresent(name -> userInfo.createClass(name)), "Create class thread").start();
    }

    @FXML
    public void deleteClass() {
//        TextInputDialog dialog = new TextInputDialog();
    }

}
