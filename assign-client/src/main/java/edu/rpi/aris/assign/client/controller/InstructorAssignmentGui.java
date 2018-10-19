package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.*;
import edu.rpi.aris.assign.client.model.CurrentUser;
import edu.rpi.aris.assign.client.model.InstructorAssignment;
import edu.rpi.aris.assign.spi.ArisModule;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.stage.Modality;

import java.io.IOException;

public class InstructorAssignmentGui implements TabGui {

    private static final ModuleUIOptions READ_ONLY_OPTIONS = new ModuleUIOptions(EditMode.READ_ONLY, "View Problem", false, false, false, false, false);

    private final InstructorAssignment assignment;
    @FXML
    private TreeTableView<InstructorAssignment.Submission> treeTable;
    @FXML
    private TreeTableColumn<InstructorAssignment.Submission, String> nameColumn;
    @FXML
    private TreeTableColumn<InstructorAssignment.Submission, String> submittedColumn;
    @FXML
    private TreeTableColumn<InstructorAssignment.Submission, String> statusColumn;
    @FXML
    private TreeTableColumn<InstructorAssignment.Submission, Node> buttonColumn;
    @FXML
    private Label name;
    @FXML
    private Label dueDate;
    private CurrentUser userInfo = CurrentUser.getInstance();
    private Parent root;
    private TreeItem<InstructorAssignment.Submission> rootItem = new TreeItem<>();

    public InstructorAssignmentGui(String name, int cid, int aid) {
        assignment = new InstructorAssignment(this, name, cid, aid);
        FXMLLoader loader = new FXMLLoader(ProblemsGui.class.getResource("/edu/rpi/aris/assign/client/view/instructor_assignment.fxml"));
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
        if (obj instanceof InstructorAssignmentGui) {
            InstructorAssignment a = ((InstructorAssignmentGui) obj).assignment;
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
        assignment.getStudents().addListener((ListChangeListener<TreeItem<InstructorAssignment.Submission>>) c -> {
            while (c.next()) {
                if (c.wasAdded())
                    rootItem.getChildren().addAll(c.getAddedSubList());
                if (c.wasRemoved())
                    rootItem.getChildren().removeAll(c.getRemoved());
            }
        });
        name.textProperty().bind(Bindings.createStringBinding(() -> assignment.getName() + ":", assignment.nameProperty()));
        dueDate.textProperty().bind(assignment.dueDateProperty());
        nameColumn.setCellValueFactory(param -> param.getValue().getValue().nameProperty());
        nameColumn.setStyle("-fx-alignment: CENTER-LEFT;");
        submittedColumn.setCellValueFactory(param -> param.getValue().getValue().submittedOnProperty());
        submittedColumn.setStyle("-fx-alignment: CENTER;");
        statusColumn.setCellValueFactory(param -> param.getValue().getValue().statusStrProperty());
        statusColumn.setStyle("-fx-alignment: CENTER;");
        buttonColumn.setCellValueFactory(param -> param.getValue().getValue().controlNodeProperty());
        buttonColumn.setStyle("-fx-alignment: CENTER;");
    }

    public <T extends ArisModule> void viewProblem(String name, Problem<T> problem, ArisModule<T> module) throws Exception {
        ArisClientModule<T> clientModule = module.getClientModule();
        ModuleUI<T> moduleUI = clientModule.createModuleGui(READ_ONLY_OPTIONS, problem);
        moduleUI.setDescription("Viewing " + name + " (read only)");
        moduleUI.setModal(Modality.WINDOW_MODAL, AssignGui.getInstance().getStage());
        moduleUI.show();
    }

}
