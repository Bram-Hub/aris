package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.*;
import edu.rpi.aris.assign.client.model.CurrentUser;
import edu.rpi.aris.assign.client.model.StudentAssignment;
import edu.rpi.aris.assign.spi.ArisModule;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.io.IOException;
import java.util.Optional;

public class StudentAssignmentGui implements TabGui {

    private static final ModuleUIOptions SUBMIT_OPTIONS = new ModuleUIOptions(EditMode.RESTRICTED_EDIT, "Create Submission", true, false, true, true, false);
    private static final ModuleUIOptions READ_ONLY_OPTIONS = new ModuleUIOptions(EditMode.READ_ONLY, "View Submission", false, false, false, false, false);
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
    private TreeTableColumn<StudentAssignment.Submission, Node> buttonColumn;
    @FXML
    private Label name;
    @FXML
    private Label dueDate;
    @FXML
    private Label status;
    @FXML
    private ImageView statusIcon;
    private CurrentUser userInfo = CurrentUser.getInstance();
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
        buttonColumn.setCellValueFactory(param -> param.getValue().getValue().controlNodeProperty());
        buttonColumn.setStyle("-fx-alignment: CENTER;");
    }

    public <T extends ArisModule> void createAttempt(StudentAssignment.Attempt problemInfo, String problemName, Problem<T> problem, ArisModule<T> module) throws Exception {
        ArisClientModule<T> clientModule = module.getClientModule();
        ModuleUI<T> moduleUI = clientModule.createModuleGui(SUBMIT_OPTIONS, problem);
        moduleUI.setDescription("Modify attempt for problem: \"" + problemName + "\"");
        moduleUI.setModuleUIListener(new ModuleUIAdapter() {

            @Override
            public boolean guiCloseRequest(boolean hasUnsavedChanges) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Submit Problem?");
                alert.setHeaderText("Would you like to submit this problem?");
                alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
                alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
                Window window = moduleUI.getUIWindow();
                if (window != null) {
                    alert.initOwner(window);
                    alert.initModality(Modality.WINDOW_MODAL);
                } else {
                    alert.initOwner(AssignGui.getInstance().getStage());
                    alert.initModality(Modality.APPLICATION_MODAL);
                }
                Optional<ButtonType> result = alert.showAndWait();
                if (result.isPresent() && result.get() == ButtonType.YES) {
                    uploadProblem();
                } else if (hasUnsavedChanges) {
                    alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Unsaved Changes");
                    alert.setHeaderText("You have unsaved changes that will be lost. Are you sure you want to exit?");
                    alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
                    alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
                    if (window != null) {
                        alert.initOwner(window);
                        alert.initModality(Modality.WINDOW_MODAL);
                    } else {
                        alert.initOwner(AssignGui.getInstance().getStage());
                        alert.initModality(Modality.APPLICATION_MODAL);
                    }
                    result = alert.showAndWait();
                    return result.isPresent() && result.get() == ButtonType.YES;
                }
                return true;
            }

            @Override
            public boolean saveProblemLocally() {
                return assignment.saveAttempt(problemInfo, problem, module, false);
            }

            @Override
            public void uploadProblem() {
                assignment.uploadAttempt(problemInfo, problem);
                try {
                    moduleUI.hide();
                } catch (Exception e) {
                    LibAssign.showExceptionError(e);
                }
            }
        });
        moduleUI.setModal(Modality.WINDOW_MODAL, AssignGui.getInstance().getStage());
        moduleUI.show();
    }

    public <T extends ArisModule> void viewSubmission(StudentAssignment.Submission submission, String problemName, Problem<T> problem, ArisModule<T> module) throws Exception {
        ArisClientModule<T> clientModule = module.getClientModule();
        ModuleUI<T> moduleUI = clientModule.createModuleGui(READ_ONLY_OPTIONS, problem);
        moduleUI.setDescription("Viewing " + submission.getName() + (problemName == null ? "" : " for problem: \"" + problemName + "\"") + " (read only)");
        moduleUI.setModal(Modality.WINDOW_MODAL, AssignGui.getInstance().getStage());
        moduleUI.show();
    }

}
