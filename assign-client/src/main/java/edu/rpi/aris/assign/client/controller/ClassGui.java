package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.LibAssign;
import edu.rpi.aris.assign.client.model.ClassInfo;
import edu.rpi.aris.assign.client.model.ClassModel;
import edu.rpi.aris.assign.client.model.CurrentUser;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.Modality;

import java.io.IOException;
import java.util.Optional;

public class ClassGui implements TabGui {

    @FXML
    private Label selectedClassLbl;
    @FXML
    private ListView<ClassModel.User> notInClassList;

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
