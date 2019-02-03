package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.LibAssign;
import edu.rpi.aris.assign.Perm;
import edu.rpi.aris.assign.ServerPermissions;
import edu.rpi.aris.assign.ServerRole;
import edu.rpi.aris.assign.client.AssignClient;
import edu.rpi.aris.assign.client.model.ClassInfo;
import edu.rpi.aris.assign.client.model.CurrentUser;
import edu.rpi.aris.assign.client.model.LocalConfig;
import edu.rpi.aris.assign.client.model.ServerConfig;
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
    private Tab classTab;
    @FXML
    private Tab permissionTab;
    @FXML
    private Tab problemTab;
    @FXML
    private MenuItem loginMenu;
    @FXML
    private Label noClasses;

    private AssignmentsGui assignmentsGui;
    private UsersGui usersGui;
    private ClassGui classGui;
    private PermissionsGui permissionsGui;
    private ProblemsGui problemsGui;
    private Stage stage;
    private CurrentUser userInfo = CurrentUser.getInstance();
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
        assignmentsGui.load(true);
        String server = LocalConfig.SERVER_ADDRESS.getValue();
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
                LocalConfig.SERVER_ADDRESS.setValue(result.get());
            } else {
                stage.hide();
                return;
            }
        }
        userInfo.connectionInit(null);
    }

    @FXML
    public void initialize() {

        assignmentsGui = new AssignmentsGui();
        usersGui = new UsersGui();
        classGui = new ClassGui();
        permissionsGui = new PermissionsGui();
        problemsGui = new ProblemsGui();
        tabGuis.put(assignmentTab, assignmentsGui);
        tabGuis.put(userTab, usersGui);
        tabGuis.put(classTab, classGui);
        tabGuis.put(permissionTab, permissionsGui);
        tabGuis.put(problemTab, problemsGui);

        tabPane.getTabs().addListener((ListChangeListener<Tab>) c -> {
            while (c.next()) {
                for (Tab t : c.getRemoved()) {
                    TabGui gui = tabGuis.get(t);
                    if (gui != null) {
                        gui.closed();
                        if (!gui.isPermanentTab())
                            tabGuis.remove(t);
                    }
                }
            }
        });

        tabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            TabGui gui = tabGuis.get(newValue);
            if (gui != null && (userInfo.isLoggedIn() || !gui.requiresOnline()))
                gui.load(false);
        });

        classes.setConverter(new CurrentUser.ClassStringConverter());
        classes.itemsProperty().set(userInfo.classesProperty());
        classes.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> userInfo.selectedClassProperty().set(newValue));
        userInfo.selectedClassProperty().addListener((observable, oldValue, newValue) -> {
            if (!userInfo.isLoggedIn())
                return;
            classes.getSelectionModel().select(newValue);
            if (newValue != null)
                setTabs(userInfo.getDefaultRole(), newValue.getUserRole());
            tabPane.getTabs().removeIf(t -> {
                TabGui gui = tabGuis.get(t);
                if (gui instanceof SingleAssignmentGui) {
                    ClassInfo info = userInfo.getSelectedClass();
                    if (info.getClassId() != ((SingleAssignmentGui) gui).getCid()) {
                        tabGuis.remove(t);
                        return true;
                    }
                }
                return false;
            });
            TabGui gui = tabGuis.get(tabPane.getSelectionModel().getSelectedItem());
            if (gui == assignmentsGui || gui == classGui)
                gui.load(false);
        });

        loading.visibleProperty().bind(userInfo.loadingBinding());
        loading.managedProperty().bind(userInfo.loadingBinding());

        login.visibleProperty().bind(userInfo.loginProperty().not());
        login.managedProperty().bind(userInfo.loginProperty().not());
        login.disableProperty().bind(userInfo.loadingBinding());

        classes.visibleProperty().bind(userInfo.loginProperty().and(Bindings.isNotEmpty(userInfo.classesProperty())));
        classes.managedProperty().bind(classes.visibleProperty());

        noClasses.visibleProperty().bind(userInfo.loginProperty().and(Bindings.isEmpty(userInfo.classesProperty())));
        noClasses.managedProperty().bind(noClasses.visibleProperty());

        lblClass.visibleProperty().bind(userInfo.loginProperty());
        lblClass.managedProperty().bind(userInfo.loginProperty());

        refreshButton.visibleProperty().bind(userInfo.loginProperty());
        refreshButton.managedProperty().bind(userInfo.loginProperty());

        lblUsername.textProperty().bind(Bindings.createStringBinding(() -> userInfo.isLoggedIn() ? LocalConfig.USERNAME.getValue() + " (" + userInfo.getClassRole().getName() + ")" : "Not Logged In", LocalConfig.USERNAME.getProperty(), userInfo.classRoleProperty(), userInfo.loginProperty()));

        loginMenu.textProperty().bind(Bindings.createStringBinding(() -> userInfo.loginProperty().get() ? "Logout" : "Login", userInfo.loginProperty()));
        loginMenu.disableProperty().bind(userInfo.loadingBinding());

        assignmentTab.setContent(assignmentsGui.getRoot());
        userTab.setContent(usersGui.getRoot());
        classTab.setContent(classGui.getRoot());
        permissionTab.setContent(permissionsGui.getRoot());
        problemTab.setContent(problemsGui.getRoot());

        userInfo.defaultRoleProperty().addListener((observable, oldValue, newValue) -> setTabs(newValue, userInfo.getClassRole()));
        userInfo.classRoleProperty().addListener(((observable, oldValue, newValue) -> setTabs(userInfo.getDefaultRole(), newValue)));

        userInfo.loginProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) {
                for (TabGui gui : tabGuis.values())
                    gui.unload();
                TabGui gui = tabGuis.get(tabPane.getSelectionModel().getSelectedItem());
                if (userInfo.isLoggedIn() || !gui.requiresOnline())
                    gui.load(true);
            }
        });

        setTabs(null, null);

    }

    private void setTabs(ServerRole defaultRole, ServerRole classRole) {
        ServerPermissions permissions = ServerConfig.getPermissions();
        if (permissions == null || defaultRole == null) {
            tabPane.getTabs().removeIf(tab -> tabGuis.get(tab).requiresOnline());
            tabGuis.values().removeIf(gui -> !gui.isPermanentTab() && gui.requiresOnline());
            if (!tabPane.getTabs().contains(assignmentTab))
                tabPane.getTabs().add(0, assignmentTab);
            return;
        }
        int tabIndex = conditionalAddTab(defaultRole, Perm.USER_LIST, userTab, 0);
        tabIndex = conditionalAddTab(classRole, Perm.CLASS_EDIT, classTab, tabIndex);
        tabIndex = conditionalAddTab(defaultRole, Perm.PERMISSIONS_EDIT, permissionTab, tabIndex);
        tabIndex = conditionalAddTab(defaultRole, Perm.PROBLEMS_GET, problemTab, tabIndex);
        conditionalAddTab(classRole, Perm.ASSIGNMENT_GET, assignmentTab, tabIndex);
    }

    private int conditionalAddTab(ServerRole role, Perm permission, Tab tab, int index) {
        if (ServerConfig.getPermissions().hasPermission(role, permission)) {
            if (!tabPane.getTabs().contains(tab))
                tabPane.getTabs().add(index, tab);
            index++;
        } else
            tabPane.getTabs().remove(tab);
        return index;
    }

    public void addTabGui(TabGui gui) {
        if (tabGuis.values().contains(gui))
            return;
        Tab tab = new Tab(gui.getName(), gui.getRoot());
        tab.textProperty().bind(gui.nameProperty());
        tabGuis.put(tab, gui);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
    }

    @FXML
    public void loginOut() {
        if (userInfo.isLoggedIn()) {
            userInfo.logout();
        } else {
            for (TabGui gui : tabGuis.values())
                if (gui.requiresOnline())
                    gui.unload();
            userInfo.connectionInit(null);
        }
    }

    @FXML
    public void refresh() {
        userInfo.connectionInit(() -> {
            for (TabGui gui : tabGuis.values())
                gui.unload();
            TabGui gui = tabGuis.get(tabPane.getSelectionModel().getSelectedItem());
            if (gui != null)
                gui.load(true);
        });
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

    public ProblemsGui getProblemsGui() {
        if (tabPane.getTabs().contains(problemTab))
            return problemsGui;
        return null;
    }
}
