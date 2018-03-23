package edu.rpi.aris.gui.submit;

import edu.rpi.aris.ConfigurationManager;
import edu.rpi.aris.Main;
import edu.rpi.aris.net.NetUtil;
import edu.rpi.aris.net.client.Client;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Optional;

public class AssignmentWindow {

    public static final AssignmentWindow instance = new AssignmentWindow();

    @FXML
    private Label lblUsername;
    @FXML
    private ChoiceBox<Course> classes;
    @FXML
    private Accordion assignments;
    @FXML
    private Button login;
    @FXML
    private ProgressIndicator loading;
    @FXML
    private Label lblClass;

    private Stage stage;
    private ClientInfo clientInfo;

    public AssignmentWindow() {
        Main.setAllowInsecure(true); //TODO: remove
        clientInfo = new ClientInfo();
        FXMLLoader loader = new FXMLLoader(AssignmentWindow.class.getResource("assignment_window.fxml"));
        loader.setController(this);
        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            Main.instance.showExceptionError(Thread.currentThread(), e, true);
            return;
        }
        VBox vbox = new VBox();
        vbox.setFillWidth(true);
        vbox.getChildren().addAll(setupMenu(), root);
        VBox.setVgrow(root, Priority.ALWAYS);
        Scene scene = new Scene(vbox, 800, 800);
        stage = new Stage();
        stage.setResizable(true);
        stage.setScene(scene);
    }

    @FXML
    private void initialize() {
        lblUsername.textProperty().bind(Bindings.createStringBinding(() -> {
            String user = ConfigurationManager.getConfigManager().username.get();
            boolean isInstructor = clientInfo.isInstructorProperty().get();
            if (user == null)
                return "Not logged in";
            return user + " (" + (isInstructor ? NetUtil.USER_INSTRUCTOR : NetUtil.USER_STUDENT) + ")";
        }, clientInfo.isInstructorProperty(), ConfigurationManager.getConfigManager().username));
        Bindings.bindContent(classes.getItems(), clientInfo.getCourses());
        classes.getSelectionModel().selectedItemProperty().addListener((observableValue, oldCourse, newCourse) -> {
            Platform.runLater(() -> {
                assignments.getPanes().clear();
                if (newCourse == null)
                    return;
                ConfigurationManager.getConfigManager().selectedCourseId.set(newCourse.getId());
                newCourse.load(() -> Platform.runLater(() -> {
                    for (Assignment assignment : newCourse.getAssignments())
                        assignments.getPanes().add(assignment.getPane());
                }), false);
            });
        });
        classes.visibleProperty().bind(clientInfo.loadedProperty());
        classes.managedProperty().bind(clientInfo.loadedProperty());
        lblClass.visibleProperty().bind(clientInfo.loadedProperty());
        lblClass.managedProperty().bind(clientInfo.loadedProperty());
        login.visibleProperty().bind(Bindings.createBooleanBinding(() -> ConfigurationManager.getConfigManager().username.get() == null, ConfigurationManager.getConfigManager().username));
        login.managedProperty().bind(login.visibleProperty());
        login.setOnAction(actionEvent -> load(true));
        loading.visibleProperty().bind(Main.getClient().getConnectionStatusProperty().isNotEqualTo(Client.ConnectionStatus.DISCONNECTED));
        loading.managedProperty().bind(loading.visibleProperty());
    }

    private MenuBar setupMenu() {
        MenuBar menuBar = new MenuBar();

        Menu account = new Menu("Account");

        MenuItem loginOut = new MenuItem();

        loginOut.setOnAction(actionEvent -> loginOut());
        loginOut.textProperty().bind(Bindings.createStringBinding(() -> clientInfo.loadedProperty().get() ? "Logout" : "Login", clientInfo.loadedProperty()));

        account.getItems().addAll(loginOut);

        Menu classMenu = new Menu("Class");

        MenuItem createClass = new MenuItem("Create Class");
        MenuItem deleteClass = new MenuItem("Delete Class");

        createClass.setOnAction(actionEvent -> createClass());
        deleteClass.setOnAction(actionEvent -> deleteClass());

        classMenu.visibleProperty().bind(clientInfo.isInstructorProperty());

        classMenu.getItems().addAll(createClass, deleteClass);

        menuBar.getMenus().addAll(account, classMenu);

        return menuBar;
    }

    private void loginOut() {
        if (clientInfo.loadedProperty().get())
            clientInfo.logout();
        else
            load(true);
    }

    private void createClass() {
        if (!clientInfo.isInstructorProperty().get())
            return;
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create Class");
        dialog.setHeaderText("Enter the name of the class to create");
        dialog.setContentText("Class name:");
        Optional<String> result = dialog.showAndWait();
        String name;
        if (result.isPresent() && (name = result.get()).length() > 0) {
            new Thread(() -> {
                Client client = Main.getClient();
                int classId = -1;
                try {
                    client.connect();
                    client.sendMessage(NetUtil.CREATE_CLASS);
                    client.sendMessage(name);
                    String res = client.readMessage();
                    String[] split = res.split(" ");
                    if (!split[0].equals(NetUtil.OK))
                        throw new IOException(res);
                    if (split.length != 2)
                        throw new IOException("Invalid response from create class");
                    classId = Integer.parseInt(split[1]);
                } catch (NumberFormatException ignored) {
                } catch (IOException e) {
                    System.out.println("Connection failed");
                    e.printStackTrace();
                    //TODO: show error to client
                } finally {
                    client.disconnect();
                }
                final int id = classId;
                Platform.runLater(() -> {
                    clientInfo.load(() -> Platform.runLater(() -> {
                        if (id >= 0)
                            selectClass(id);
                    }), true);
                });
            }).start();
        }
    }

    private void deleteClass() {
        Course course = classes.getSelectionModel().getSelectedItem();
        if (course == null)
            return;
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
        alert.setTitle("Delete Class");
        alert.setHeaderText("Are you sure you want to delete the class \"" + course.getName() + "\"?");
        alert.setContentText("This will also delete all the class's\nassociated assignments and submissions");

        Optional<ButtonType> response = alert.showAndWait();
        if (response.isPresent() && response.get() == ButtonType.YES) {
            new Thread(() -> {
                Client client = Main.getClient();
                try {
                    client.connect();
                    client.sendMessage(NetUtil.DELETE_CLASS);
                    client.sendMessage(String.valueOf(course.getId()));
                    String res = client.readMessage();
                    if (!res.equals(NetUtil.OK))
                        throw new IOException(res);
                } catch (IOException e) {
                    //TODO: show error
                } finally {
                    client.disconnect();
                }
                load(true);
            }).start();
        }
    }

    public void load(boolean reload) {
        clientInfo.load(() -> Platform.runLater(() -> selectClass(ConfigurationManager.getConfigManager().selectedCourseId.get())), reload);
    }

    private void selectClass(int classId) {
        int id = -1;
        for (int i = 0; i < clientInfo.getCourses().size(); ++i)
            if (clientInfo.getCourses().get(i).getId() == classId) {
                id = i;
                break;
            }
        if (id == -1 && classes.getItems().size() > 0)
            id = 0;
        if (id > -1)
            classes.getSelectionModel().select(id);
    }

    public void show() {
        stage.show();
        if (!clientInfo.loadedProperty().get())
            load(false);
    }

    public void hide() {
        stage.hide();
    }

    public ClientInfo getClientInfo() {
        return clientInfo;
    }
}
