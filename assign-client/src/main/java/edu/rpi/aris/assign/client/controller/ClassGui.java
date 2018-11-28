package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.LibAssign;
import edu.rpi.aris.assign.ServerRole;
import edu.rpi.aris.assign.client.model.ClassInfo;
import edu.rpi.aris.assign.client.model.ClassModel;
import edu.rpi.aris.assign.client.model.CurrentUser;
import edu.rpi.aris.assign.client.model.ServerConfig;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.layout.Region;
import javafx.stage.Modality;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ClassGui implements TabGui {

    @FXML
    private Label selectedClassLbl;
    @FXML
    private ListView<ClassModel.User> notInClassList;
    @FXML
    private TableView<ClassModel.User> inClassTbl;
    @FXML
    private TableColumn<ClassModel.User, String> nameColumn;
    @FXML
    private TableColumn<ClassModel.User, ServerRole> roleColumn;
    @FXML
    private TableColumn<ClassModel.User, Node> removeColumn;

    private Parent root;
    private CurrentUser userInfo = CurrentUser.getInstance();
    private ClassModel classModel = new ClassModel(this);

    public ClassGui() {
        FXMLLoader loader = new FXMLLoader(AssignmentsGui.class.getResource("/edu/rpi/aris/assign/client/view/class_view.fxml"));
        loader.setController(this);
        try {
            root = loader.load();
        } catch (IOException e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, true);
        }
    }

    private void removeFromClass(ClassModel.User user) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Remove From Class");
        alert.setHeaderText("Remove " + user.getFullName() + " (" + user.getUsername() + ") from class?");
        alert.setContentText("Any submissions made by this user will be deleted");
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alert.getDialogPane().getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
        ((Button) alert.getDialogPane().lookupButton(ButtonType.YES)).setDefaultButton(false);
        ((Button) alert.getDialogPane().lookupButton(ButtonType.NO)).setDefaultButton(true);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES)
            classModel.removeFromClass(userInfo.getSelectedClass().getClassId(), user.getUid());
    }

    @Override
    public void load(boolean reload) {
        classModel.load(reload);
    }

    @Override
    public void unload() {
        classModel.clear();
    }

    @Override
    public void closed() {
    }

    @FXML
    private void initialize() {
        selectedClassLbl.textProperty().bind(Bindings.createStringBinding(() -> userInfo.getSelectedClass() == null ? "None" : userInfo.getSelectedClass().getClassName(), userInfo.selectedClassProperty()));
        userInfo.selectedClassProperty().addListener((observable, oldValue, newValue) -> unload());
        notInClassList.setCellFactory(param -> new UserListCell());
        notInClassList.setPlaceholder(new Label("No Users"));
        notInClassList.setItems(classModel.getNotInClass());
        notInClassList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        inClassTbl.setPlaceholder(new Label("No Users"));
        nameColumn.setCellValueFactory(param -> {
            ClassModel.User u = param.getValue();
            return Bindings.createStringBinding(() -> u.getFullName() + " (" + u.getUsername() + ")", u.fullNameProperty(), u.usernameProperty());
        });
        roleColumn.setCellFactory(param -> new ComboBoxTableCell<>(ServerConfig.getRoleStringConverter(), ServerConfig.getPermissions().getRoles().toArray(new ServerRole[0])));
        roleColumn.setCellValueFactory(param -> param.getValue().classRoleProperty());
        roleColumn.setOnEditCommit(event -> {
            ServerRole old = event.getOldValue();
            event.getRowValue().classRoleProperty().set(event.getNewValue());
            classModel.roleChanged(event.getRowValue(), userInfo.getSelectedClass().getClassId(), old, event.getNewValue());
        });
        inClassTbl.setItems(classModel.getInClass());
        removeColumn.setCellValueFactory(param -> {
            Button btn = new Button("Remove From Class");
            btn.setOnAction(e -> removeFromClass(param.getValue()));
            return new SimpleObjectProperty<>(btn);
        });
    }

    @FXML
    public void createClass() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create Class");
        dialog.setHeaderText("Create a new class");
        dialog.setContentText("Class Name:");
        dialog.initOwner(AssignGui.getInstance().getStage());
        dialog.initModality(Modality.WINDOW_MODAL);
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> userInfo.createClass(name));
    }

    @FXML
    public void deleteClass() {
        ClassInfo info = userInfo.getSelectedClass();
        if (info == null)
            return;
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Delete Class");
        dialog.setContentText("Class Name:");
        dialog.setHeaderText("To delete the class, type the class name below exactly as follows: \"" + info.getClassName() + "\"");
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initOwner(AssignGui.getInstance().getStage());
        ButtonType delete = new ButtonType("Delete");
        dialog.getDialogPane().getButtonTypes().setAll(delete, ButtonType.CANCEL);
        dialog.setOnShowing(event -> {
            Button deleteBtn = (Button) dialog.getDialogPane().lookupButton(delete);
            deleteBtn.disableProperty().bind(dialog.getEditor().textProperty().isNotEqualTo(info.getClassName()));
            deleteBtn.setDefaultButton(true);
        });
        dialog.setResultConverter(buttonType -> {
            if (buttonType == delete)
                return dialog.getEditor().getText();
            return null;
        });
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && result.get().equals(info.getClassName()))
            userInfo.deleteClass(info.getClassId());
    }

    @FXML
    public void addToClass() {
        Set<Integer> selectedUids = notInClassList.getSelectionModel().getSelectedItems().stream()
                .map(ClassModel.User::getUid)
                .collect(Collectors.toSet());
        classModel.addToClass(userInfo.getSelectedClass().getClassId(), selectedUids);
    }

    @Override
    public Parent getRoot() {
        return root;
    }

    @Override
    public boolean isPermanentTab() {
        return true;
    }

    @Override
    public String getName() {
        return "Classes";
    }

    @Override
    public SimpleStringProperty nameProperty() {
        return new SimpleStringProperty(getName());
    }
}
