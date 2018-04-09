package edu.rpi.aris.gui.submit;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AddAssignmentDialog extends Dialog<Triple<String, LocalDateTime, Collection<ProofInfo>>> {

    private static final Pattern timePattern = Pattern.compile("(?i)(?<hour>1[0-2]|0?[1-9]):(?<min>[0-5][0-9]) *?(?<ap>AM|PM)");
    private final ProofList availableProofs;
    @FXML
    private TextField nameField;
    @FXML
    private DatePicker dueDate;
    @FXML
    private VBox proofBox;
    @FXML
    private TextField timeInput;
    private Button okBtn;

    public AddAssignmentDialog(Window parent, ProofList availableProofs) throws IOException {
        this.availableProofs = availableProofs;
        initModality(Modality.WINDOW_MODAL);
        initOwner(parent);
        FXMLLoader loader = new FXMLLoader(AddAssignmentDialog.class.getResource("add_assignment.fxml"));
        loader.setController(this);
        getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        setTitle("Create Assignment");
        setHeaderText("Create Assignment");
        okBtn = (Button) getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setDisable(true);
        getDialogPane().setContent(loader.load());
        setResultConverter(param -> {
            if (param != ButtonType.OK)
                return null;
            String name = nameField.getText();
            Matcher m = timePattern.matcher(timeInput.getText());
            if (!m.matches())
                return null;
            int hour = Integer.parseInt(m.group("hour"));
            int min = Integer.parseInt(m.group("min"));
            String ap = m.group("ap").toLowerCase();
            hour = ap.equals("pm") ? hour + 12 : hour;
            if (hour == 24)
                hour = 0;
            LocalDateTime date = dueDate.getValue().atTime(hour, min);
            List<ProofInfo> list = proofBox.getChildren().stream().map(node -> (ProofInfo) ((ComboBox) ((HBox) node).getChildren().get(0)).getSelectionModel().getSelectedItem()).filter(Objects::nonNull).collect(Collectors.toList());
            return new ImmutableTriple<>(name, date, list);
        });
    }

    private void addSelector() {
        HBox box = new HBox(5);
        ComboBox<ProofInfo> combo = new ComboBox<>();
        combo.setPromptText("Select Proof");
        combo.setItems(availableProofs.getProofs());
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<ProofInfo>() {

            boolean added = false;

            @Override
            public void changed(ObservableValue<? extends ProofInfo> observable, ProofInfo oldValue, ProofInfo newValue) {
                if (oldValue == null && !added) {
                    addSelector();
                    added = true;
                }
            }
        });
        Button delete = new Button("-");
        delete.disableProperty().bind(Bindings.createBooleanBinding(() -> combo.getSelectionModel().getSelectedItem() == null, combo.getSelectionModel().selectedItemProperty()));
        delete.setOnAction(action -> {
            proofBox.getChildren().remove(box);
            getDialogPane().getScene().getWindow().sizeToScene();
        });
        HBox.setHgrow(combo, Priority.ALWAYS);
        box.getChildren().addAll(combo, delete);
        proofBox.getChildren().add(box);
        getDialogPane().getScene().getWindow().sizeToScene();
    }

    @FXML
    private void initialize() {
        okBtn.disableProperty().bind(Bindings.createBooleanBinding(() -> !timePattern.matcher(timeInput.getText()).matches() || nameField.getText().length() == 0 || dueDate.getValue() == null || proofBox.getChildren().size() < 2, timeInput.textProperty(), nameField.textProperty(), dueDate.valueProperty(), proofBox.getChildren()));
        addSelector();
    }

}
