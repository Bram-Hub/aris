package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.LibAssign;
import edu.rpi.aris.assign.client.model.StudentAssignment;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;

import java.io.IOException;

public class StudentAssignmentGui implements TabGui {

    private final StudentAssignment assignment;
    @FXML
    private TreeTableView<StudentAssignment.Submission> treeTable;
    @FXML
    private TreeTableColumn<StudentAssignment.Submission, String> nameColumn;
    @FXML
    private TreeTableColumn<StudentAssignment.Submission, String> submittedColumn;
    @FXML
    private TreeTableColumn<StudentAssignment.Submission, String> statusColumn;
    @FXML
    private TreeTableColumn<StudentAssignment.Submission, Button> buttonColumn;
    @FXML
    private Label name;
    @FXML
    private Label dueDate;
    @FXML
    private Label status;
    @FXML
    private ImageView statusIcon;
    private Parent root;
    private TreeItem<StudentAssignment.Submission> rootItem = new TreeItem<>();

    public StudentAssignmentGui(String name, int cid, int aid) {
        assignment = new StudentAssignment(name, cid, aid);
        FXMLLoader loader = new FXMLLoader(ProblemsGui.class.getResource("/edu/rpi/aris/assign/client/view/student_assignment.fxml"));
        loader.setController(this);
        try {
            root = loader.load();
        } catch (IOException e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, true);
        }
    }

    @Override
    public void load(boolean reload) {
        assignment.loadAssignment(reload);
    }

    @Override
    public void unload() {
        assignment.clear();
    }

    @Override
    public Parent getRoot() {
        return root;
    }

    @Override
    public boolean isPermanentTab() {
        return false;
    }

    @Override
    public String getName() {
        return assignment.getName();
    }

    @Override
    public SimpleStringProperty nameProperty() {
        return assignment.nameProperty();
    }

    @FXML
    public void initialize() {
        treeTable.setRoot(rootItem);
        treeTable.setShowRoot(false);
        assignment.getProblems().addListener((ListChangeListener<TreeItem<StudentAssignment.Submission>>) c -> {
            while(c.next()) {
                if (c.wasAdded())
                    rootItem.getChildren().addAll(c.getAddedSubList());
                if (c.wasRemoved())
                    rootItem.getChildren().removeAll(c.getRemoved());
            }
        });
        nameColumn.setCellValueFactory(param -> param.getValue().getValue().nameProperty());
        submittedColumn.setCellValueFactory(param -> param.getValue().getValue().submittedOnProperty());
        statusColumn.setCellValueFactory(param -> param.getValue().getValue().statusStrProperty());
        buttonColumn.setCellValueFactory(param -> param.getValue().getValue().buttonProperty());
    }

}
