package edu.rpi.aris.assign.client.dialog;

import edu.rpi.aris.assign.*;
import edu.rpi.aris.assign.client.AssignClient;
import edu.rpi.aris.assign.spi.ArisModule;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;

import java.io.IOException;
import java.util.Collections;

public class ProblemDialog<T extends ArisModule> extends Dialog<Triple<String, String, Problem<T>>> implements ModuleUIListener {

    public static final ModuleUIOptions UI_OPTIONS = new ModuleUIOptions(EditMode.CREATE_EDIT_PROBLEM, "Edit problem", false, true, false, true, false);
    @FXML
    private TextField name;
    @FXML
    private ComboBox<String> module;
    @FXML
    private Button btnEditor;
    @FXML
    private VBox box;
    private Button okBtn;
    private SimpleObjectProperty<Problem<T>> problem = new SimpleObjectProperty<>();
    private ModuleUI<T> moduleUI;

    public ProblemDialog(Window parent) throws IOException {
        initModality(Modality.WINDOW_MODAL);
        initOwner(parent);
        FXMLLoader loader = new FXMLLoader(ProblemDialog.class.getResource("/edu/rpi/aris/assign/client/view/problem_dialog.fxml"));
        loader.setController(this);
        getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        setTitle("Create Problem");
        setHeaderText("Create Problem");
        okBtn = (Button) getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setDisable(true);
        getDialogPane().setContent(loader.load());
        setResultConverter(param -> param == ButtonType.OK ? new ImmutableTriple<>(name.getText(), module.getSelectionModel().getSelectedItem(), problem.get()) : null);
    }

    public ProblemDialog(Window parent, String moduleName, String name, Problem<T> problem) throws IOException {
        this(parent);
        setTitle("Modify Problem");
        setHeaderText("Modify Problem");
        this.problem.set(problem);
        replaceModuleChoice(moduleName);
        this.name.setText(name);
        module.getSelectionModel().select(moduleName);
    }

    @FXML
    public void initialize() {
        module.getItems().addAll(ModuleService.getService().moduleNames());
        Collections.sort(module.getItems());

        if (module.getItems().size() == 1)
            module.getSelectionModel().select(0);

        btnEditor.disableProperty().bind(module.getSelectionModel().selectedItemProperty().isNull());

        okBtn.disableProperty().bind(problem.isNull().or(name.textProperty().isEmpty()).or(module.getSelectionModel().selectedItemProperty().isNull()));
    }

    @FXML
    public void openEditor() {
        try {
            if (moduleUI == null) {
                String moduleName = module.getSelectionModel().getSelectedItem();
                ArisClientModule<T> client = ModuleService.getService().getClientModule(moduleName);
                if (client == null) {
                    AssignClient.displayErrorMsg("Error loading module", "Failed to load client module for \"" + moduleName + "\"");
                    return;
                }
                replaceModuleChoice(moduleName);
                if (problem.get() == null)
                    moduleUI = client.createModuleGui(UI_OPTIONS);
                else
                    moduleUI = client.createModuleGui(UI_OPTIONS, problem.get());
                moduleUI.setModal(Modality.WINDOW_MODAL, getOwner());
                moduleUI.setModuleUIListener(this);
            }
            moduleUI.show();
        } catch (Exception e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, false);
        }
    }

    public void replaceModuleChoice(String moduleName) {
        int index = box.getChildren().indexOf(module);
        if (index == -1)
            return;
        Label lbl = new Label("Selected Module: " + moduleName);
        lbl.setPadding(new Insets(5));
        box.getChildren().set(index, lbl);
    }

    @Override
    public boolean guiCloseRequest(boolean hasUnsavedChanges) {
        return true;
    }

    @Override
    public void guiClosed() {
        try {
            problem.set(moduleUI.getProblem());
        } catch (Exception e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, false);
        }
    }

    @Override
    public void saveProblemLocally() {
    }

    @Override
    public void uploadProblem() {
        try {
            moduleUI.hide();
        } catch (Exception e) {
            LibAssign.showExceptionError(e);
        }
    }
}
