package edu.rpi.aris.assign.client.gui;

import edu.rpi.aris.assign.ArisModuleException;
import edu.rpi.aris.assign.LibAssign;
import edu.rpi.aris.assign.NetUtil;
import edu.rpi.aris.assign.Problem;
import edu.rpi.aris.assign.client.Client;
import edu.rpi.aris.assign.client.ClientModuleService;
import edu.rpi.aris.assign.message.*;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public class AssignmentWindow {

    public static final AssignmentWindow instance = new AssignmentWindow();

    private static final String HIDE_TAB = "hide-tab-bar";
    private static final Logger logger = LogManager.getLogger(AssignmentWindow.class);
    @FXML
    private Label lblUsername;
    @FXML
    private ChoiceBox<Course> classes;
    @FXML
    private Accordion assignments;
    @FXML
    private Button login;
    @FXML
    private Button refreshButton;
    @FXML
    private Button createAssignment;
    @FXML
    private ProgressIndicator loading;
    @FXML
    private Label lblClass;
    @FXML
    private TabPane tabPane;
    @FXML
    private Tab assignmentTab;
    @FXML
    private Tab studentTab;
    @FXML
    private Tab proofTab;
    @FXML
    private TableView<ProblemInfo> proofTable;
    @FXML
    private TableColumn<ProblemInfo, String> proofName;
    @FXML
    private TableColumn<ProblemInfo, String> proofBy;
    @FXML
    private TableColumn<ProblemInfo, String> proofOn;
    @FXML
    private TableColumn<ProblemInfo, Button> proofEdit;
    @FXML
    private TableColumn<ProblemInfo, Button> proofDelete;
    private Stage stage;
    private ClientInfo clientInfo;
    private ProblemList problemList;

    public AssignmentWindow() {
        clientInfo = new ClientInfo();
        problemList = new ProblemList();
        FXMLLoader loader = new FXMLLoader(AssignmentWindow.class.getResource("assignment_window.fxml"));
        loader.setController(this);
        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, true);
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
            String user = Config.USERNAME.getValue();
            boolean isInstructor = clientInfo.isInstructorProperty().get();
            if (user == null)
                return "Not logged in";
            return user + " (" + (isInstructor ? NetUtil.USER_INSTRUCTOR : NetUtil.USER_STUDENT) + ")";
        }, clientInfo.isInstructorProperty(), Config.USERNAME.getProperty()));
        Bindings.bindContent(classes.getItems(), clientInfo.getCourses());
        classes.getSelectionModel().selectedItemProperty().addListener((observableValue, oldCourse, newCourse) -> loadAssignments(newCourse, false));
        classes.visibleProperty().bind(clientInfo.loadedProperty());
        classes.managedProperty().bind(clientInfo.loadedProperty());
        lblClass.visibleProperty().bind(clientInfo.loadedProperty());
        lblClass.managedProperty().bind(clientInfo.loadedProperty());
        login.visibleProperty().bind(Bindings.createBooleanBinding(() -> Config.USERNAME.getValue() == null || !clientInfo.loadedProperty().get(), Config.USERNAME.getProperty(), clientInfo.loadedProperty()));
        login.managedProperty().bind(login.visibleProperty());
        login.setOnAction(actionEvent -> load(true));
        refreshButton.visibleProperty().bind(clientInfo.loadedProperty());
        refreshButton.managedProperty().bind(clientInfo.loadedProperty());
        refreshButton.disableProperty().bind(Client.getInstance().getConnectionStatusProperty().isNotEqualTo(Client.ConnectionStatus.DISCONNECTED));
        refreshButton.setPadding(new Insets(5));
        createAssignment.visibleProperty().bind(clientInfo.isInstructorProperty().and(classes.getSelectionModel().selectedItemProperty().isNotNull()));
        createAssignment.managedProperty().bind(clientInfo.isInstructorProperty().and(classes.getSelectionModel().selectedItemProperty().isNotNull()));
        loading.visibleProperty().bind(Client.getInstance().getConnectionStatusProperty().isNotEqualTo(Client.ConnectionStatus.DISCONNECTED));
        loading.managedProperty().bind(loading.visibleProperty());
        clientInfo.isInstructorProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                tabPane.getStyleClass().remove(HIDE_TAB);
            } else {
                tabPane.getSelectionModel().select(assignmentTab);
                if (!tabPane.getStyleClass().contains(HIDE_TAB))
                    tabPane.getStyleClass().add(HIDE_TAB);
            }
        });
        tabPane.getStyleClass().add(HIDE_TAB);
        studentTab.disableProperty().bind(clientInfo.isInstructorProperty().not());
        proofTab.disableProperty().bind(clientInfo.isInstructorProperty().not());
        tabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == proofTab)
                problemList.load(false);
        });
        proofName.setCellValueFactory(param -> param.getValue().nameProperty());
        proofName.setOnEditCommit(event -> new Thread(() -> {
            Client client = Client.getInstance();
            try {
                client.connect();
                ProblemEditMsg reply = (ProblemEditMsg) new ProblemEditMsg(event.getRowValue().getProblemId(), event.getNewValue()).sendAndGet(client);
                if (reply == null)
                    return;
                event.getRowValue().setName(event.getNewValue());
            } catch (IOException e) {
                e.printStackTrace();
                //TODO
                int index = proofTable.getItems().indexOf(event.getRowValue());
                event.getRowValue().setName(event.getOldValue());
                proofTable.getItems().set(index, event.getRowValue());
            } finally {
                client.disconnect();
            }
        }).start());
        proofName.setStyle("-fx-alignment: CENTER;");
        proofBy.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getCreatedBy()));
        proofName.setCellFactory(TextFieldTableCell.forTableColumn());
        proofBy.setStyle("-fx-alignment: CENTER;");
        proofOn.setCellValueFactory(param -> new SimpleStringProperty(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(param.getValue().getCreatedOn())));
        proofOn.setStyle("-fx-alignment: CENTER;");
        proofEdit.setCellValueFactory(param -> {
            Button btn = new Button("View/Edit");
            btn.setOnAction(event -> editProblem(param.getValue()));
            return new SimpleObjectProperty<>(btn);
        });
        proofEdit.setStyle("-fx-alignment: CENTER;");
        proofDelete.setCellValueFactory(param -> {
            Button btn = new Button("Delete");
            btn.setOnAction(event -> deleteProblem(param.getValue()));
            return new SimpleObjectProperty<>(btn);
        });
        proofDelete.setStyle("-fx-alignment: CENTER;");
        Bindings.bindContent(proofTable.getItems(), problemList.getProofs());
    }

    public void loadAssignments(Course newCourse, boolean reload) {
        Platform.runLater(() -> {
            assignments.getPanes().clear();
            if (newCourse == null)
                return;
            Config.SELECTED_COURSE_ID.setValue(newCourse.getId());
            newCourse.load(() -> Platform.runLater(() -> {
                for (Assignment assignment : newCourse.getAssignments())
                    assignments.getPanes().add(assignment.getPane());
            }), reload);
        });
    }

    private MenuBar setupMenu() {
        MenuBar menuBar = new MenuBar();

        Menu account = new Menu("Account");

        MenuItem loginOut = new MenuItem();
        MenuItem changePassword = new MenuItem("Change Password");

        loginOut.setOnAction(actionEvent -> loginOut());
        loginOut.textProperty().bind(Bindings.createStringBinding(() -> clientInfo.loadedProperty().get() ? "Logout" : "Login", clientInfo.loadedProperty()));

        changePassword.setOnAction(actionEvent -> changePassword(null));
        changePassword.visibleProperty().bind(clientInfo.loadedProperty());

        account.getItems().addAll(loginOut, changePassword);

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

    private void changePassword(String username) {
        Dialog<Pair<String, String>> enterPass = new Dialog<>();
        enterPass.setTitle("Change Password");
        enterPass.setHeaderText("Change password for user: " + (username == null ? Config.USERNAME.getValue() : username));
        GridPane gridPane = new GridPane();
        PasswordField oldPass = new PasswordField();
        oldPass.setPromptText("Current Password");
        PasswordField newPass = new PasswordField();
        newPass.setPromptText("New Password");
        PasswordField retypePass = new PasswordField();
        retypePass.setPromptText("Retype New Password");
        int i = 0;
        gridPane.setHgap(5);
        gridPane.setVgap(5);
        if (username == null)
            gridPane.addRow(i++, new Label("Current Password:"), oldPass);
        gridPane.addRow(i++, new Label("New Password:"), newPass);
        gridPane.addRow(i, new Label("Retype Password:"), retypePass);
        enterPass.getDialogPane().setContent(gridPane);
        enterPass.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        Button okBtn = (Button) enterPass.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.disableProperty().bind(newPass.textProperty().isNotEqualTo(retypePass.textProperty()).or(newPass.textProperty().length().isEqualTo(0)));
        enterPass.setResultConverter(param -> {
            if (param != ButtonType.OK)
                return null;
            return new Pair<>(oldPass.getText(), newPass.getText());
        });
        Optional<Pair<String, String>> newPassPair = enterPass.showAndWait();
        if (!newPassPair.isPresent())
            return;
        if (username != null) {
            //TODO get instructor's password
        }
        new Thread(() -> {
            Client client = Client.getInstance();
            try {
                client.connect();
                String type = clientInfo.isInstructorProperty().get() ? NetUtil.USER_INSTRUCTOR : NetUtil.USER_STUDENT;
                new UserEditMsg(username, type, newPassPair.get().getValue(), newPassPair.get().getKey(), true).sendAndGet(client);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                client.disconnect();
            }
        }).start();
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
                Client client = Client.getInstance();
                int classId = -1;
                try {
                    client.connect();
                    ClassCreateMsg reply = (ClassCreateMsg) new ClassCreateMsg(name).sendAndGet(client);
                    if (reply == null)
                        return;
                    classId = reply.getClassId();
                } catch (NumberFormatException ignored) {
                } catch (IOException e) {
                    System.out.println("Connection failed");
                    e.printStackTrace();
                    //TODO: show error to client
                } finally {
                    client.disconnect();
                }
                final int id = classId;
                Platform.runLater(() -> clientInfo.load(() -> Platform.runLater(() -> {
                    if (id >= 0)
                        selectClass(id);
                }), true));
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
        alert.initOwner(stage);
        alert.initModality(Modality.WINDOW_MODAL);
        Button yes = (Button) alert.getDialogPane().lookupButton(ButtonType.YES);
        yes.setDefaultButton(false);
        yes.setDisable(true);
        ((Button) alert.getDialogPane().lookupButton(ButtonType.NO)).setDefaultButton(true);
        VBox box = new VBox(5);
        box.getChildren().add(new Label("WARNING! Cannot be undone\nThis will also delete all the class's\nassociated assignments and submissions\nTo confirm please type the class name below"));
        TextField confirmText = new TextField();
        confirmText.setPromptText("Class name");
        confirmText.textProperty().addListener((observable, oldValue, newValue) -> yes.setDisable(!newValue.equals(course.getName())));
        box.getChildren().add(confirmText);
        alert.getDialogPane().setContent(box);

        Optional<ButtonType> response = alert.showAndWait();
        if (response.isPresent() && response.get() == ButtonType.YES) {
            new Thread(() -> {
                Client client = Client.getInstance();
                try {
                    client.connect();
                    ClassDeleteMsg reply = (ClassDeleteMsg) new ClassDeleteMsg(course.getId()).sendAndGet(client);
                    if (reply == null)
                        return;
                } catch (IOException e) {
                    //TODO: show error
                } finally {
                    client.disconnect();
                }
                Platform.runLater(() -> load(true));
            }).start();
        }
    }

    public void load(boolean reload) {
        Tab selected = tabPane.getSelectionModel().getSelectedItem();
        clientInfo.load(() -> Platform.runLater(() -> {
            selectClass(Config.SELECTED_COURSE_ID.getValue());
            new Thread(() -> {
                if (clientInfo.isInstructorProperty().get())
                    Platform.runLater(() -> tabPane.getSelectionModel().select(selected));
            }).start();
        }), reload);
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

    private void addProblem(String name, String moduleName, Problem problem) {
        new Thread(() -> {
            Client client = Client.getInstance();
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ClientModuleService.getService().getModule(moduleName).getProblemConverter().convertProblem(problem, baos, false);
                byte[] data = baos.toByteArray();
                client.connect();
                ProblemCreateMsg reply = (ProblemCreateMsg) new ProblemCreateMsg(name, moduleName, data).sendAndGet(client);
                if (reply == null)
                    return;
                problemList.load(true);
            } catch (IOException | ArisModuleException e) {
                System.out.println("Error");
                //TODO
                e.printStackTrace();
            } finally {
                client.disconnect();
            }
        }).start();
    }

    private void addAssignment(String name, LocalDateTime date, Collection<ProblemInfo> proofs) {
        Course course = classes.getSelectionModel().getSelectedItem();
        new Thread(() -> {
            Client client = Client.getInstance();
            try {
                client.connect();
                AssignmentCreateMsg msg = new AssignmentCreateMsg(classes.getSelectionModel().getSelectedItem().getId(), name, NetUtil.localToUTC(date));
                proofs.forEach(p -> msg.addProof(p.getProblemId()));
                AssignmentCreateMsg reply = (AssignmentCreateMsg) msg.sendAndGet(client);
                if (reply == null)
                    return;
                loadAssignments(course, true);
            } catch (IOException e) {
                System.out.println("Error");
            } finally {
                client.disconnect();
            }
        }).start();
    }

    private void deleteProblem(ProblemInfo proof) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.initOwner(stage);
        alert.initModality(Modality.WINDOW_MODAL);
        alert.setTitle("Delete Proof");
        alert.setHeaderText("Confirm Deletion");
        Label lbl = new Label("WARNING! Cannot be undone\nAre you sure you want to delete the proof titled \"" + proof.getName() + "\" from the server? \nThis will also remove the proof from any associated assignments and delete any associated submissions.");
        lbl.setWrapText(true);
        alert.getDialogPane().setContent(lbl);
        alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
        ((Button) alert.getDialogPane().lookupButton(ButtonType.YES)).setDefaultButton(false);
        ((Button) alert.getDialogPane().lookupButton(ButtonType.NO)).setDefaultButton(true);
        alert.showAndWait().ifPresent(type -> {
            if (type == ButtonType.YES) {
                new Thread(() -> {
                    Client client = Client.getInstance();
                    try {
                        client.connect();
                        ProblemDeleteMsg reply = (ProblemDeleteMsg) new ProblemDeleteMsg(proof.getProblemId()).sendAndGet(client);
                        if (reply == null)
                            return;
                        Platform.runLater(() -> problemList.remove(proof));
                    } catch (IOException e) {
                        System.out.println("Connection error");
                        //TODO
                    } finally {
                        client.disconnect();
                    }
                }).start();
            }
        });
    }

    private void editProblem(ProblemInfo problem) {
        //TODO
    }

    @FXML
    private void createProblem() {
        try {
            AddProblemDialog dialog = new AddProblemDialog(stage);
            Optional<Triple<String, String, Problem>> result = dialog.showAndWait();
            result.ifPresent(r -> addProblem(r.getLeft(), r.getMiddle(), r.getRight()));
        } catch (IOException e) {
            logger.error("Failed to show add proof dialog");
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, false);
        }
    }

    @FXML
    private void importProblem() {
        //TODO
    }

    @FXML
    private void refresh() {
        load(true);
    }

    @FXML
    private void createAssignment() throws IOException {
        problemList.load(false);
        AssignmentDialog dialog = new AssignmentDialog(stage, problemList);
        Optional<Triple<String, LocalDateTime, Collection<ProblemInfo>>> result = dialog.showAndWait();
        if (result.isPresent()) {
            Triple<String, LocalDateTime, Collection<ProblemInfo>> r = result.get();
            addAssignment(r.getLeft(), r.getMiddle(), r.getRight());
        }
    }

//    public void transmitMessage(final Message msg, final Consumer<Message> call) {
//        new Thread(() -> {
//            Client client = Main.getClient();
//            Message response = null;
//            try {
//                client.connect();
//                msg.send(client);
//                response = Message.parseReply(client);
//                if (response == null || response instanceof ErrorMsg) {
//                    processError((ErrorMsg) response);
//                } else if (!response.getClass().equals(msg.getClass())) {
//                    //TODO
//                }
//            } catch (IOException | MessageBuildException e) {
//                //TODO
//                System.out.println("Network error");
//            } finally {
//                client.disconnect();
//            }
//            call.accept(response);
//        }).start();
//    }

    public void processError(ErrorMsg error) {
        //TODO
    }

    public Stage getStage() {
        return stage;
    }

    public ProblemList getProblemList() {
        return problemList;
    }
}
