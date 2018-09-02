package edu.rpi.aris.assign.client.guiold;

import edu.rpi.aris.assign.*;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class AddProblemDialog extends Dialog<Triple<String, String, Problem>> {

    private static final Logger logger = LogManager.getLogger(AddProblemDialog.class);

    @FXML
    private TextField textField;
    @FXML
    private ChoiceBox<String> moduleSelect;
    @FXML
    private Button editorBtn;
    private SimpleObjectProperty<Problem> problem = new SimpleObjectProperty<>();
    private ArisClientModule module;
    private ModuleUI moduleUI;
    private boolean moduleUiOpen = false;

    public AddProblemDialog(Window parent) throws IOException {
        initModality(Modality.WINDOW_MODAL);
        initOwner(parent);
        FXMLLoader loader = new FXMLLoader(AddProblemDialog.class.getResource("add_proof.fxml"));
        loader.setController(this);
        getDialogPane().setContent(loader.load());
        getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        setTitle("Create Proof");
        setHeaderText("Create Proof");
        setResultConverter(param -> param == ButtonType.CANCEL ? null : new ImmutableTriple<>(textField.getText(), moduleSelect.getValue(), this.problem.get()));
    }

    @FXML
    private void initialize() {
        moduleSelect.getItems().addAll(ModuleService.getService().moduleNames());
        moduleSelect.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> module = newValue == null ? null : ModuleService.getService().getClientModule(newValue));

        editorBtn.setDisable(true);
        editorBtn.disableProperty().bind(moduleSelect.getSelectionModel().selectedItemProperty().isNull());

        Button okBtn = (Button) getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setDisable(true);
        okBtn.disableProperty().bind(problem.isNull().or(textField.textProperty().isEmpty()).or(moduleSelect.getSelectionModel().selectedItemProperty().isNull()));
    }

    @FXML
    private void openEditor() {
        try {
            moduleSelect.setDisable(true);
            if (moduleUiOpen && moduleUI != null) {
                moduleUI.hide();
                return;
            }
            String description = "Creating problem " + (textField.getText().length() == 0 ? "" : "\"" + textField.getText() + "\" ");
            if (moduleUI == null) {
                moduleUI = module.createModuleGui(null);
                moduleUI.setModal(Modality.WINDOW_MODAL, getDialogPane().getScene().getWindow());
//                moduleUI.addCloseListener(() -> {
//                    try {
//                        synchronized (AddProblemDialog.this) {
//                            moduleUiOpen = false;
//                        }
//                        problem.set(moduleUI.getProblem());
//                    } catch (Exception e) {
//                        logger.error("Failed to retrieve problem from module ui");
//                        LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, false);
//                    }
//                });
            } else
//                moduleUI.setDescription(description);
            problem.set(null);
            synchronized (this) {
                moduleUI.show();
                moduleUiOpen = true;
            }
        } catch (Exception e) {
            logger.error("Failed to show/hide problem window");
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, false);
        }
    }

}
