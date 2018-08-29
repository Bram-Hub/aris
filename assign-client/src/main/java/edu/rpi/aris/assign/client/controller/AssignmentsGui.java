package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.LibAssign;
import edu.rpi.aris.assign.UserType;
import edu.rpi.aris.assign.client.model.Assignments;
import edu.rpi.aris.assign.client.model.UserInfo;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.io.IOException;

public class AssignmentsGui implements TabGui {

    private UserInfo userInfo = UserInfo.getInstance();

    @FXML
    private TableView<Assignments.Assignment> tblAssignments;
    @FXML
    private TableColumn<Assignments.Assignment, Button> deleteColumn;
    @FXML
    private Button btnCreate;

    private Parent root;
    private Assignments assignments = new Assignments();

    public AssignmentsGui() {
        FXMLLoader loader = new FXMLLoader(AssignmentsGui.class.getResource("../view/assignments_view.fxml"));
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
        }, userInfo.loginProperty(), userInfo.selectedClassProperty(), userInfo.loadingProperty()));
        userInfo.userTypeProperty().addListener((observable, oldValue, newValue) -> deleteColumn.setVisible(UserType.hasPermission(userInfo.getUserType(), UserType.INSTRUCTOR)));

        tblAssignments.itemsProperty().set(assignments.getAssignments());

        userInfo.loginProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue)
                assignments.clear();
        });
        userInfo.selectedClassProperty().addListener((observable, oldValue, newValue) -> assignments.clear());
        btnCreate.visibleProperty().bind(Bindings.createBooleanBinding(() -> UserType.hasPermission(userInfo.getUserType(), UserType.INSTRUCTOR), userInfo.userTypeProperty()));
        btnCreate.managedProperty().bind(btnCreate.visibleProperty());
    }

    @FXML
    public void create() {

    }

}
