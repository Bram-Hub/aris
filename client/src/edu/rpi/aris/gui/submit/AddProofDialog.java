package edu.rpi.aris.gui.submit;

import edu.rpi.aris.Main;
import edu.rpi.aris.gui.EditMode;
import edu.rpi.aris.gui.MainWindow;
import edu.rpi.aris.proof.Proof;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class AddProofDialog extends Dialog<Pair<String, Proof>> {

    private static final Logger logger = LogManager.getLogger(AddProofDialog.class);

    @FXML
    private TextField textField;
    private SimpleObjectProperty<Proof> proof;
    private Button okBtn;
    private MainWindow window;

    public AddProofDialog(Window parent) throws IOException {
        this(parent, null);
    }

    public AddProofDialog(Window parent, Proof proof) throws IOException {
        initModality(Modality.WINDOW_MODAL);
        initOwner(parent);
        FXMLLoader loader = new FXMLLoader(AddProofDialog.class.getResource("add_proof.fxml"));
        loader.setController(this);
        getDialogPane().setContent(loader.load());
        getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        setTitle("Create Proof");
        setHeaderText("Create Proof");
        this.proof = new SimpleObjectProperty<>(proof);
        okBtn = (Button) getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setDisable(true);
        okBtn.disableProperty().bind(Bindings.createBooleanBinding(() -> this.proof.get() == null || textField.getText().length() == 0, textField.textProperty(), this.proof));
        setResultConverter(param -> param == ButtonType.CANCEL ? null : new Pair<>(textField.getText(), this.proof.get()));
    }

    @FXML
    private void openEditor() {
        if (window == null) {
            try {
                window = new MainWindow(new Stage(), EditMode.UNRESTRICTED);
                window.setModal(getDialogPane().getScene().getWindow());
                proof.set(window.getProof());
            } catch (IOException e) {
                logger.error("Failed to open proof window");
                Main.instance.showExceptionError(Thread.currentThread(), e, false);
                return;
            }
        }
        window.show();
    }

}
