package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.LibAssign;
import edu.rpi.aris.assign.UserType;
import edu.rpi.aris.assign.client.AssignClient;
import edu.rpi.aris.assign.client.model.ClassInfo;
import edu.rpi.aris.assign.client.model.Config;
import edu.rpi.aris.assign.client.model.UserInfo;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.text.DateFormat;
import java.util.HashMap;
import java.util.Optional;

public class AssignGui {

    public static final DateFormat DATE_FORMAT = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);

    private static AssignGui instance;
    @FXML
    private ChoiceBox<ClassInfo> classes;
    @FXML
    private ProgressIndicator loading;
    @FXML
    private Label lblUsername;
    @FXML
    private Label lblClass;
    @FXML
    private Button login;
    @FXML
    private Button refreshButton;
    @FXML
    private TabPane tabPane;
    @FXML
    private Tab assignmentTab;
    @FXML
    private Tab userTab;
    @FXML
    private Tab problemTab;
    @FXML
    private MenuItem loginMenu;
    @FXML
    private Menu classMenu;

    private AssignmentsGui assignmentsGui;
    private UsersGui usersGui;
    private ProblemsGui problemsGui;
    private Stage stage;
    private UserInfo userInfo = UserInfo.getInstance();
    private HashMap<Tab, TabGui> tabGuis = new HashMap<>();

    private AssignGui() {
        stage = new Stage();
        FXMLLoader loader = new FXMLLoader(AssignGui.class.getResource("/edu/rpi/aris/assign/client/view/assign_window.fxml"));
        loader.setController(this);
        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, true);
            return;
        }
        Scene scene = new Scene(root, 1000, 800);
        stage.setScene(scene);
        stage.initOwner(AssignClient.getInstance().getMainWindow().getStage());
    }

    public static AssignGui getInstance() {
        if (instance == null)
            instance = new AssignGui();
        return instance;
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
                return;
            }
        }
        userInfo.getUserInfo(false, () -> {
            TabGui gui = tabGuis.get(tabPane.getSelectionModel().getSelectedItem());
            if (gui != null)
                gui.load(false);
        });
    }

    @FXML
    public void initialize() {

        assignmentsGui = new AssignmentsGui();
        usersGui = new UsersGui();
        problemsGui = new ProblemsGui();
        tabGuis.put(assignmentTab, assignmentsGui);
        tabGuis.put(userTab, usersGui);
        tabGuis.put(problemTab, problemsGui);

        tabPane.getTabs().addListener((ListChangeListener<Tab>) c -> {
            while (c.next()) {
                if (c.wasRemoved()) {
                    for (Tab t : c.getRemoved()) {
                        TabGui gui = tabGuis.get(t);
                        if (gui != null && !gui.isPermanentTab())
                            tabGuis.remove(t);
                    }
                }
            }
        });

        tabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            TabGui gui = tabGuis.get(newValue);
            if (gui != null)
                gui.load(false);
        });

        classes.setConverter(new UserInfo.ClassStringConverter());
        classes.itemsProperty().set(userInfo.classesProperty());
        classes.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> userInfo.selectedClassProperty().set(newValue));
        userInfo.selectedClassProperty().addListener((observable, oldValue, newValue) -> {
            classes.getSelectionModel().select(newValue);
            TabGui gui = tabGuis.get(tabPane.getSelectionModel().getSelectedItem());
            if (gui == assignmentsGui)
                gui.load(false);
        });

        loading.visibleProperty().bind(userInfo.loadingBinding());
        loading.managedProperty().bind(userInfo.loadingBinding());

        login.visibleProperty().bind(userInfo.loginProperty().not());
        login.managedProperty().bind(userInfo.loginProperty().not());
        login.disableProperty().bind(userInfo.loadingBinding());

        classes.visibleProperty().bind(userInfo.loginProperty());
        classes.managedProperty().bind(userInfo.loginProperty());

        lblClass.visibleProperty().bind(userInfo.loginProperty());
        lblClass.managedProperty().bind(userInfo.loginProperty());

        refreshButton.visibleProperty().bind(userInfo.loginProperty());
        refreshButton.managedProperty().bind(userInfo.loginProperty());

        lblUsername.textProperty().bind(Bindings.createStringBinding(() -> userInfo.isLoggedIn() ? Config.USERNAME.getValue() + " (" + userInfo.getUserType().readableName + ")" : "Not Logged In", Config.USERNAME.getProperty(), userInfo.userTypeProperty(), userInfo.loginProperty()));

        loginMenu.textProperty().bind(Bindings.createStringBinding(() -> userInfo.loginProperty().get() ? "Logout" : "Login", userInfo.loginProperty()));
        loginMenu.disableProperty().bind(userInfo.loadingBinding());

        assignmentTab.setContent(assignmentsGui.getRoot());
        userTab.setContent(usersGui.getRoot());
        problemTab.setContent(problemsGui.getRoot());

        userInfo.userTypeProperty().addListener((observable, oldValue, newValue) -> setTabs(newValue));
        setTabs(null);

        classMenu.visibleProperty().bind(Bindings.createBooleanBinding(() -> UserType.hasPermission(userInfo.getUserType(), UserType.INSTRUCTOR), userInfo.userTypeProperty()));

    }

    private void setTabs(UserType type) {
        if (UserType.hasPermission(type, UserType.INSTRUCTOR)) {
            if (!tabPane.getTabs().contains(userTab))
                tabPane.getTabs().add(1, userTab);
            if (!tabPane.getTabs().contains(problemTab))
                tabPane.getTabs().add(2, problemTab);
        } else
            tabPane.getTabs().removeAll(userTab, problemTab);
    }

    @FXML
    public void loginOut() {
        if (userInfo.loginProperty().get()) {
            userInfo.logout();
        } else
            refresh();
    }

    @FXML
    public void refresh() {
        userInfo.getUserInfo(true, () -> {
            for (TabGui gui : tabGuis.values())
                gui.unload();
            TabGui gui = tabGuis.get(tabPane.getSelectionModel().getSelectedItem());
            if (gui != null)
                gui.load(true);
        });
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
        ClassInfo info = userInfo.getSelectedClass();
        if (info == null)
            return;
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Delete Class");
        dialog.setContentText("Class Name:");
        dialog.setHeaderText("To delete the class, type the class name below exactly as follows: \"" + info.getClassName() + "\"");
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initOwner(stage);
        ButtonType delete = new ButtonType("Delete");
        dialog.getDialogPane().getButtonTypes().setAll(delete, ButtonType.CANCEL);
        dialog.setOnShowing(event -> {
            Button deleteBtn = (Button) dialog.getDialogPane().lookupButton(delete);
            deleteBtn.disableProperty().bind(dialog.getEditor().textProperty().isNotEqualTo(info.getClassName()));
            deleteBtn.setDefaultButton(true);
        });
        dialog.setResultConverter(buttonType -> {
            if (buttonType == delete)
                return dialog.getEditor().getText();
            return null;
        });
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && result.get().equals(info.getClassName()))
            userInfo.deleteClass(info.getClassId());
    }

    public Window getStage() {
        return stage;
    }

    public void notImplemented(String functionName) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Not Implemented");
        alert.setHeaderText(functionName + " has not been implemented");
        alert.initModality(Modality.WINDOW_MODAL);
        alert.initOwner(stage);
        alert.show();
    }

}
