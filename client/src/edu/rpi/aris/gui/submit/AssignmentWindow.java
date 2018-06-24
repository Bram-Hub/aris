package edu.rpi.aris.gui.submit;

import edu.rpi.aris.Main;
import edu.rpi.aris.gui.GuiConfig;
import edu.rpi.aris.net.MessageBuildException;
import edu.rpi.aris.net.NetUtil;
import edu.rpi.aris.net.client.Client;
import edu.rpi.aris.net.message.*;
import edu.rpi.aris.proof.Proof;
import edu.rpi.aris.proof.SaveInfoListener;
import edu.rpi.aris.proof.SaveManager;
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

import javax.xml.transform.TransformerException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;

public class AssignmentWindow implements SaveInfoListener {

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
    private TableView<ProofInfo> proofTable;
    @FXML
    private TableColumn<ProofInfo, String> proofName;
    @FXML
    private TableColumn<ProofInfo, String> proofBy;
    @FXML
    private TableColumn<ProofInfo, String> proofOn;
    @FXML
    private TableColumn<ProofInfo, Button> proofEdit;
    @FXML
    private TableColumn<ProofInfo, Button> proofDelete;
    private Stage stage;
    private ClientInfo clientInfo;
    private ProofList proofList;
    private SaveManager saveManager;

    public AssignmentWindow() {
        clientInfo = new ClientInfo();
        proofList = new ProofList();
        saveManager = new SaveManager(this);
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
            String user = GuiConfig.getConfigManager().username.get();
            boolean isInstructor = clientInfo.isInstructorProperty().get();
            if (user == null)
                return "Not logged in";
            return user + " (" + (isInstructor ? NetUtil.USER_INSTRUCTOR : NetUtil.USER_STUDENT) + ")";
        }, clientInfo.isInstructorProperty(), GuiConfig.getConfigManager().username));
        Bindings.bindContent(classes.getItems(), clientInfo.getCourses());
        classes.getSelectionModel().selectedItemProperty().addListener((observableValue, oldCourse, newCourse) -> loadAssignments(newCourse, false));
        classes.visibleProperty().bind(clientInfo.loadedProperty());
        classes.managedProperty().bind(clientInfo.loadedProperty());
        lblClass.visibleProperty().bind(clientInfo.loadedProperty());
        lblClass.managedProperty().bind(clientInfo.loadedProperty());
        login.visibleProperty().bind(Bindings.createBooleanBinding(() -> GuiConfig.getConfigManager().username.get() == null || !clientInfo.loadedProperty().get(), GuiConfig.getConfigManager().username, clientInfo.loadedProperty()));
        login.managedProperty().bind(login.visibleProperty());
        login.setOnAction(actionEvent -> load(true));
        refreshButton.visibleProperty().bind(clientInfo.loadedProperty());
        refreshButton.managedProperty().bind(clientInfo.loadedProperty());
        refreshButton.disableProperty().bind(Main.getClient().getConnectionStatusProperty().isNotEqualTo(Client.ConnectionStatus.DISCONNECTED));
        refreshButton.setPadding(new Insets(5));
        createAssignment.visibleProperty().bind(clientInfo.isInstructorProperty().and(classes.getSelectionModel().selectedItemProperty().isNotNull()));
        createAssignment.managedProperty().bind(clientInfo.isInstructorProperty().and(classes.getSelectionModel().selectedItemProperty().isNotNull()));
        loading.visibleProperty().bind(Main.getClient().getConnectionStatusProperty().isNotEqualTo(Client.ConnectionStatus.DISCONNECTED));
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
                proofList.load(false);
        });
        proofName.setCellValueFactory(param -> param.getValue().nameProperty());
        proofName.setOnEditCommit(event -> new Thread(() -> {
            Client client = Main.getClient();
            try {
                client.connect();
                ProofEditMsg reply = (ProofEditMsg) new ProofEditMsg(event.getRowValue().getProofId(), event.getNewValue()).sendAndGet(client);
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
            btn.setOnAction(event -> editProof(param.getValue()));
            return new SimpleObjectProperty<>(btn);
        });
        proofEdit.setStyle("-fx-alignment: CENTER;");
        proofDelete.setCellValueFactory(param -> {
            Button btn = new Button("Delete");
            btn.setOnAction(event -> deleteProof(param.getValue()));
            return new SimpleObjectProperty<>(btn);
        });
        proofDelete.setStyle("-fx-alignment: CENTER;");
        Bindings.bindContent(proofTable.getItems(), proofList.getProofs());
    }

    public void loadAssignments(Course newCourse, boolean reload) {
        Platform.runLater(() -> {
            assignments.getPanes().clear();
            if (newCourse == null)
                return;
            GuiConfig.getConfigManager().selectedCourseId.set(newCourse.getId());
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
        enterPass.setHeaderText("Change password for user: " + (username == null ? GuiConfig.getConfigManager().username.get() : username));
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
            Client client = Main.getClient();
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
                Client client = Main.getClient();
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
                Client client = Main.getClient();
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
            selectClass(GuiConfig.getConfigManager().selectedCourseId.get());
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

    private void addProof(String name, Proof proof) {
        new Thread(() -> {
            Client client = Main.getClient();
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                saveManager.saveProof(proof, baos, false);
                byte[] data = baos.toByteArray();
                client.connect();
                ProofCreateMsg reply = (ProofCreateMsg) new ProofCreateMsg(name, data).sendAndGet(client);
                if (reply == null)
                    return;
                proofList.load(true);
            } catch (IOException | TransformerException e) {
                System.out.println("Error");
                //TODO
            } finally {
                client.disconnect();
            }
        }).start();
    }

    private void addAssignment(String name, LocalDateTime date, Collection<ProofInfo> proofs) {
        Course course = classes.getSelectionModel().getSelectedItem();
        new Thread(() -> {
            Client client = Main.getClient();
            try {
                client.connect();
                AssignmentCreateMsg msg = new AssignmentCreateMsg(classes.getSelectionModel().getSelectedItem().getId(), name, NetUtil.localToUTC(date));
                proofs.forEach(p -> msg.addProof(p.getProofId()));
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

    private void deleteProof(ProofInfo proof) {
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
                    Client client = Main.getClient();
                    try {
                        client.connect();
                        ProofDeleteMsg reply = (ProofDeleteMsg) new ProofDeleteMsg(proof.getProofId()).sendAndGet(client);
                        if (reply == null)
                            return;
                        Platform.runLater(() -> proofList.remove(proof));
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

    private void editProof(ProofInfo proof) {
        //TODO
    }

    @FXML
    private void createProof() {
        try {
            AddProofDialog dialog = new AddProofDialog(stage);
            Optional<Pair<String, Proof>> result = dialog.showAndWait();
            result.ifPresent(r -> addProof(r.getKey(), r.getValue()));
        } catch (IOException e) {
            logger.error("Failed to show add proof dialog");
            Main.instance.showExceptionError(Thread.currentThread(), e, false);
        }
    }

    @FXML
    private void importProof() {
        //TODO
    }

    @FXML
    private void refresh() {
        load(true);
    }

    @FXML
    private void createAssignment() throws IOException {
        proofList.load(false);
        AssignmentDialog dialog = new AssignmentDialog(stage, proofList);
        Optional<Triple<String, LocalDateTime, Collection<ProofInfo>>> result = dialog.showAndWait();
        if (result.isPresent()) {
            Triple<String, LocalDateTime, Collection<ProofInfo>> r = result.get();
            addAssignment(r.getLeft(), r.getMiddle(), r.getRight());
        }
    }

    @Override
    public boolean notArisFile(String filename, String programName, String programVersion) {
        Alert noAris = new Alert(Alert.AlertType.CONFIRMATION);
        noAris.setTitle("Not Aris File");
        noAris.setHeaderText("Not Aris File");
        noAris.setContentText("The given file \"" + filename + "\" was written by " + programName + " version " + programVersion + "\n" +
                "Aris may still be able to read this file with varying success\n" +
                "Would you like to attempt to load this file?");
        Optional<ButtonType> option = noAris.showAndWait();
        return option.isPresent() && option.get() == ButtonType.YES;
    }

    @Override
    public void integrityCheckFailed(String filename) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("File integrity check failed");
        alert.setHeaderText("File integrity check failed");
        alert.setContentText("This file may be corrupted or may have been tampered with.\n" +
                "If this file successfully loads the author will be marked as UNKNOWN.\n" +
                "This will show up if this file is submitted and may affect your grade.");
        alert.getDialogPane().setPrefWidth(500);
        alert.showAndWait();
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

    public ProofList getProofList() {
        return proofList;
    }
}
