package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.LibAssign;
import edu.rpi.aris.assign.client.model.StudentAssignment;
import edu.rpi.aris.assign.client.model.UserInfo;
import javafx.beans.binding.Bindings;
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
    private UserInfo userInfo = UserInfo.getInstance();
    private Parent root;
    private TreeItem<StudentAssignment.Submission> rootItem = new TreeItem<>();

    public StudentAssignmentGui(String name, int cid, int aid) {
        assignment = new StudentAssignment(this, name, cid, aid);
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

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof StudentAssignmentGui) {
            StudentAssignment a = ((StudentAssignmentGui) obj).assignment;
            return a.getCid() == assignment.getCid() && a.getAid() == assignment.getAid();
        } else
            return false;
    }

    @FXML
    public void initialize() {
        Label placeHolder = new Label();
        treeTable.setPlaceholder(placeHolder);
        placeHolder.textProperty().bind(Bindings.createStringBinding(() -> {
            if (userInfo.isLoading())
                return "Loading...";
            else if (assignment.isLoadError())
                return "An error occurred loading the assignment";
            else
                return "No problems in assignment";
        }, userInfo.loadingProperty(), assignment.loadErrorProperty()));
        treeTable.setRoot(rootItem);
        treeTable.setShowRoot(false);
        assignment.getProblems().addListener((ListChangeListener<TreeItem<StudentAssignment.Submission>>) c -> {
            while (c.next()) {
                if (c.wasAdded())
                    rootItem.getChildren().addAll(c.getAddedSubList());
                if (c.wasRemoved())
                    rootItem.getChildren().removeAll(c.getRemoved());
            }
        });
        name.textProperty().bind(Bindings.createStringBinding(() -> assignment.getName() + ":", assignment.nameProperty()));
        dueDate.textProperty().bind(assignment.dueDateProperty());
        status.textProperty().bind(assignment.statusProperty());
        nameColumn.setCellValueFactory(param -> param.getValue().getValue().nameProperty());
        nameColumn.setStyle("-fx-alignment: CENTER-LEFT;");
        submittedColumn.setCellValueFactory(param -> param.getValue().getValue().submittedOnProperty());
        submittedColumn.setStyle("-fx-alignment: CENTER;");
        statusColumn.setCellValueFactory(param -> param.getValue().getValue().statusStrProperty());
        statusColumn.setStyle("-fx-alignment: CENTER;");
        buttonColumn.setCellValueFactory(param -> param.getValue().getValue().buttonProperty());
        buttonColumn.setStyle("-fx-alignment: CENTER;");
    }

    public void createSubmission(StudentAssignment.AssignedProblem problem) {
        
    }

    public void viewSubmission(StudentAssignment.Submission submission) {

    }

}
