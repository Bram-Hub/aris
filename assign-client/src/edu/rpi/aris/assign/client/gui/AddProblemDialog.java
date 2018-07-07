package edu.rpi.aris.assign.client.gui;

import edu.rpi.aris.assign.*;
import edu.rpi.aris.assign.spi.client.ArisClientModule;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class AddProblemDialog extends Dialog<Pair<String, Problem>> {

    private static final Logger logger = LogManager.getLogger(AddProblemDialog.class);

    @FXML
    private TextField textField;
    private SimpleObjectProperty<Problem> problem;
    private Button okBtn;
    private ArisClientModule module;
    private ModuleUI moduleUI;

    public AddProblemDialog(Window parent, ArisClientModule module) throws IOException {
        this(parent, module, null);
    }

    public AddProblemDialog(Window parent, ArisClientModule module, Problem problem) throws IOException {
        this.module = module;
        initModality(Modality.WINDOW_MODAL);
        initOwner(parent);
        FXMLLoader loader = new FXMLLoader(AddProblemDialog.class.getResource("add_proof.fxml"));
        loader.setController(this);
        getDialogPane().setContent(loader.load());
        getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        setTitle("Create Proof");
        setHeaderText("Create Proof");
        this.problem = new SimpleObjectProperty<>(problem);
        okBtn = (Button) getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setDisable(true);
        okBtn.disableProperty().bind(Bindings.createBooleanBinding(() -> this.problem.get() == null || textField.getText().length() == 0, textField.textProperty(), this.problem));
        setResultConverter(param -> param == ButtonType.CANCEL ? null : new Pair<>(textField.getText(), this.problem.get()));
    }

    @FXML
    private void openEditor() {
        String description = "Creating problem " + (textField.getText().length() == 0 ? "" : "\"" + textField.getText() + "\" ");
        try {
            if (moduleUI == null) {
                moduleUI = module.createModuleGui(EditMode.UNRESTRICTED, description);
                moduleUI.setModal(Modality.WINDOW_MODAL, getDialogPane().getScene().getWindow());
                problem.set(moduleUI.getProblem());
            } else
                moduleUI.setDescription(description);
            moduleUI.show();
        } catch (ArisModuleException e) {
            logger.error("Failed to open problem window");
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, false);
        }
    }

}
