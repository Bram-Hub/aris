package edu.rpi.aris.assign.client.guiold;

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
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AssignmentDialog extends Dialog<Triple<String, LocalDateTime, Collection<ProblemInfo>>> {

    private static final Pattern timePattern = Pattern.compile("(?i)(?<hour>1[0-2]|0?[1-9]):(?<min>[0-5][0-9]) *?(?<ap>AM|PM)");
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a");
    private final ProblemList availableProofs;
    @FXML
    private TextField nameField;
    @FXML
    private DatePicker dueDate;
    @FXML
    private VBox proofBox;
    @FXML
    private TextField timeInput;
    private Button okBtn;
    private boolean edit = false;

    public AssignmentDialog(Window parent, ProblemList availableProofs) throws IOException {
        this.availableProofs = availableProofs;
        initModality(Modality.WINDOW_MODAL);
        initOwner(parent);
        FXMLLoader loader = new FXMLLoader(AssignmentDialog.class.getResource("assignment_dialog.fxml"));
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
            if (hour == 12) {
                hour = ap.equals("pm") ? 12 : 0;
            } else {
                hour = ap.equals("pm") ? hour + 12 : hour;
                if (hour == 24)
                    hour = 0;
            }
            LocalDateTime date = dueDate.getValue().atTime(hour, min);
            List<ProblemInfo> list = proofBox.getChildren().stream().map(node -> (ProblemInfo) ((ComboBox) ((HBox) node).getChildren().get(0)).getSelectionModel().getSelectedItem()).filter(Objects::nonNull).collect(Collectors.toList());
            return new ImmutableTriple<>(name, date, list);
        });
    }

    public AssignmentDialog(Window parent, ProblemList availableProofs, String name, Date dueDate, Set<ProblemInfo> proofs) throws IOException {
        this(parent, availableProofs);
        proofBox.getChildren().clear();
        for (ProblemInfo info : proofs)
            addSelector().getSelectionModel().select(info);
        nameField.setText(name);
        timeInput.setText(timeFormat.format(dueDate));
        this.dueDate.setValue(dueDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        edit = true;
    }

    private ComboBox<ProblemInfo> addSelector() {
        HBox box = new HBox(5);
        ComboBox<ProblemInfo> combo = new ComboBox<>();
        combo.setPromptText("Select Proof");
        combo.setItems(availableProofs.getProofs());
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<ProblemInfo>() {

            boolean added = false;

            @Override
            public void changed(ObservableValue<? extends ProblemInfo> observable, ProblemInfo oldValue, ProblemInfo newValue) {
                if (oldValue == null && !added) {
                    addSelector();
                    added = true;
                }
            }
        });
        Button delete = new Button("-");
        delete.disableProperty().bind(Bindings.createBooleanBinding(() -> combo.getSelectionModel().getSelectedItem() == null, combo.getSelectionModel().selectedItemProperty()));
        delete.setOnAction(action -> {
            if (edit) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.initModality(Modality.WINDOW_MODAL);
                alert.initOwner(getDialogPane().getScene().getWindow());
                alert.setTitle("Remove Proof?");
                alert.setHeaderText("Are you sure you want to remove this proof");
                alert.setContentText("This will also delete any student submissions\nfor this proof on this assignment");
                alert.getButtonTypes().setAll(ButtonType.NO, ButtonType.YES);
                alert.getDialogPane().getScene().getWindow().sizeToScene();
                Optional<ButtonType> result = alert.showAndWait();
                if (!result.isPresent() || result.get() != ButtonType.YES)
                    return;
            }
            proofBox.getChildren().remove(box);
            getDialogPane().getScene().getWindow().sizeToScene();
        });
        HBox.setHgrow(combo, Priority.ALWAYS);
        box.getChildren().addAll(combo, delete);
        proofBox.getChildren().add(box);
        getDialogPane().getScene().getWindow().sizeToScene();
        return combo;
    }

    @FXML
    private void initialize() {
        okBtn.disableProperty().bind(Bindings.createBooleanBinding(() -> !timePattern.matcher(timeInput.getText()).matches() || nameField.getText().length() == 0 || dueDate.getValue() == null || proofBox.getChildren().size() < 2, timeInput.textProperty(), nameField.textProperty(), dueDate.valueProperty(), proofBox.getChildren()));
        addSelector();
    }
}
