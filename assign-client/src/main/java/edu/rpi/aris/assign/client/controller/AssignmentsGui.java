package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.LibAssign;
import edu.rpi.aris.assign.NetUtil;
import edu.rpi.aris.assign.UserType;
import edu.rpi.aris.assign.client.AssignClient;
import edu.rpi.aris.assign.client.dialog.AssignmentDialog;
import edu.rpi.aris.assign.client.model.Assignments;
import edu.rpi.aris.assign.client.model.Problems;
import edu.rpi.aris.assign.client.model.UserInfo;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import org.apache.commons.lang3.tuple.Triple;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class AssignmentsGui implements TabGui {

    private UserInfo userInfo = UserInfo.getInstance();

    @FXML
    private TableView<Assignments.Assignment> tblAssignments;
    @FXML
    private TableColumn<Assignments.Assignment, String> name;
    @FXML
    private TableColumn<Assignments.Assignment, String> dueDate;
    @FXML
    private TableColumn<Assignments.Assignment, String> status;
    @FXML
    private TableColumn<Assignments.Assignment, Button> deleteColumn;
    @FXML
    private Button btnCreate;

    private Parent root;
    private Assignments assignments = new Assignments(this);

    public AssignmentsGui() {
        FXMLLoader loader = new FXMLLoader(AssignmentsGui.class.getResource("/edu/rpi/aris/assign/client/view/assignments_view.fxml"));
        loader.setController(this);
        try {
            root = loader.load();
        } catch (IOException e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, true);
        }
    }

    public Parent getRoot() {
        return root;
    }

    @Override
    public boolean isPermanentTab() {
        return true;
    }

    @Override
    public void load(boolean reload) {
        assignments.loadAssignments(reload);
    }

    @Override
    public void unload() {
        assignments.clear();
    }

    @FXML
    public void initialize() {
        Label placeHolderLbl = new Label();
        tblAssignments.setPlaceholder(placeHolderLbl);
        placeHolderLbl.textProperty().bind(Bindings.createStringBinding(() -> {
            if (userInfo.isLoading())
                return "Loading...";
            else if (!userInfo.isLoggedIn())
                return "Not Logged In";
            else if (userInfo.getSelectedClass() == null)
                return "No Class Selected";
            else if (assignments.isLoadError())
                return "Error Loading Assignments";
            else
                return "No Assignments!";
        }, userInfo.loginProperty(), userInfo.selectedClassProperty(), userInfo.loadingProperty(), assignments.loadErrorProperty()));
        userInfo.userTypeProperty().addListener((observable, oldValue, newValue) -> deleteColumn.setVisible(UserType.hasPermission(userInfo.getUserType(), UserType.INSTRUCTOR)));

        tblAssignments.itemsProperty().set(assignments.getAssignments());

        userInfo.loginProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue)
                assignments.clear();
        });
        userInfo.selectedClassProperty().addListener((observable, oldValue, newValue) -> assignments.clear());
        btnCreate.visibleProperty().bind(Bindings.createBooleanBinding(() -> UserType.hasPermission(userInfo.getUserType(), UserType.INSTRUCTOR), userInfo.userTypeProperty()));
        btnCreate.managedProperty().bind(btnCreate.visibleProperty());

        name.setCellValueFactory(param -> param.getValue().nameProperty());
        dueDate.setCellValueFactory(param -> param.getValue().dueDateProperty());
        status.setCellValueFactory(param -> param.getValue().statusProperty());

        name.setStyle("-fx-alignment: CENTER-LEFT;");
        dueDate.setStyle("-fx-alignment: CENTER;");
        status.setStyle("-fx-alignment: CENTER;");
        deleteColumn.setStyle("-fx-alignment: CENTER;");

    }

    @FXML
    public void create() {
        new Thread(() -> {
            Problems problems = AssignGui.getInstance().getProblemsGui().getProblems();
            if (!problems.isLoaded()) {
                AtomicBoolean done = new AtomicBoolean(false);
                Consumer<Boolean> consumer = new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean aBoolean) {
                        problems.removeOnLoadComplete(this);
                        synchronized (done) {
                            done.set(true);
                            done.notify();
                        }
                    }
                };
                problems.addOnLoadComplete(consumer);
                Platform.runLater(() -> problems.loadProblems(false));
                synchronized (done) {
                    while (!done.get()) {
                        try {
                            done.wait();
                        } catch (InterruptedException e) {
                            LibAssign.showExceptionError(e);
                        }
                    }
                }
                if (!problems.isLoaded()) {
                    AssignClient.getInstance().getMainWindow().displayErrorMsg("Load Error", "Unable to load the problems list");
                    return;
                }
            }
            Platform.runLater(() -> {
                try {
                    AssignmentDialog dialog = new AssignmentDialog(AssignGui.getInstance().getStage(), problems.getProblems());
                    dialog.initModality(Modality.WINDOW_MODAL);
                    dialog.initOwner(AssignGui.getInstance().getStage());
                    Optional<Triple<String, LocalDateTime, Collection<Problems.Problem>>> result = dialog.showAndWait();
                    result.ifPresent(r -> assignments.createAssignment(userInfo.getSelectedClass().getClassId(), r.getLeft(), NetUtil.localToUTC(r.getMiddle()), r.getRight().stream().map(Problems.Problem::getPid).collect(Collectors.toList())));
                } catch (IOException e) {
                    LibAssign.showExceptionError(e);
                }
            });
        }).start();
    }

}
