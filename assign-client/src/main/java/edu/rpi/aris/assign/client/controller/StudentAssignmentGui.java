package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.LibAssign;
import edu.rpi.aris.assign.client.model.StudentAssignment;
import edu.rpi.aris.assign.client.model.UserInfo;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;

import javax.swing.text.html.ImageView;
import java.io.IOException;

public class StudentAssignmentGui implements TabGui {

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
    private StudentAssignment assignment;

    public StudentAssignmentGui(int cid, int aid) {
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

    }

    @Override
    public void unload() {

    }

    @Override
    public Parent getRoot() {
        return root;
    }

    @Override
    public boolean isPermanentTab() {
        return false;
    }

    @FXML
    public void initialize() {
    }

}
