package edu.rpi.aris.assign.client.dialog;

import edu.rpi.aris.assign.*;
import edu.rpi.aris.assign.client.AssignClient;
import edu.rpi.aris.assign.client.ClientModuleService;
import edu.rpi.aris.assign.spi.ArisModule;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;

import java.io.IOException;
import java.util.Collections;

public class ProblemDialog<T extends ArisModule> extends Dialog<Triple<String, String, Problem<T>>> {

    @FXML
    private TextField name;
    @FXML
    private ComboBox<String> module;
    @FXML
    private Button btnEditor;

    private Button okBtn;
    private SimpleObjectProperty<Problem<T>> problem = new SimpleObjectProperty<>();
    private ModuleUI<T> moduleUI;

    public ProblemDialog(Window parent) throws IOException {
        initModality(Modality.WINDOW_MODAL);
        initOwner(parent);
        FXMLLoader loader = new FXMLLoader(ProblemDialog.class.getResource("../view/problem_dialog.fxml"));
        loader.setController(this);
        getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        setTitle("Create Problem");
        setHeaderText("Create Problem");
        okBtn = (Button) getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setDisable(true);
        getDialogPane().setContent(loader.load());
        setResultConverter(param -> new ImmutableTriple<>(name.getText(), module.getSelectionModel().getSelectedItem(), problem.get()));
    }

    @FXML
    public void initialize() {
        module.getItems().addAll(ClientModuleService.getService().moduleNames());
        Collections.sort(module.getItems());

        btnEditor.disableProperty().bind(module.getSelectionModel().selectedItemProperty().isNull());

        okBtn.disableProperty().bind(problem.isNull().or(name.textProperty().isEmpty()).or(module.getSelectionModel().selectedItemProperty().isNull()));
    }

    @FXML
    public void openEditor() {
        try {
            if (moduleUI == null) {
                String moduleName = module.getSelectionModel().getSelectedItem();
                ArisClientModule<T> client = ClientModuleService.getService().getClientModule(moduleName);
                if (client == null) {
                    AssignClient.getInstance().getMainWindow().displayErrorMsg("Error loading module", "Failed to load client module for \"" + moduleName + "\"");
                    return;
                }
                module.setDisable(true);
                if (problem.get() == null)
                    moduleUI = client.createModuleGui(EditMode.CREATE_EDIT_PROBLEM, "Edit Problem");
                else
                    moduleUI = client.createModuleGui(EditMode.CREATE_EDIT_PROBLEM, "Edit Problem", problem.get());
                moduleUI.setModal(Modality.WINDOW_MODAL, getDialogPane().getScene().getWindow());
                moduleUI.addCloseListener(() -> {
                    try {
                        problem.set(moduleUI.getProblem());
                    } catch (Exception e) {
                        LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, false);
                    }
                });
            }
            moduleUI.show();
        } catch (Exception e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, false);
        }
    }

}
