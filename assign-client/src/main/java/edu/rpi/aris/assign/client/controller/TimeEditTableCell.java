package edu.rpi.aris.assign.client.controller;

import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;

import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.Date;
import java.util.function.UnaryOperator;

public class TimeEditTableCell<T> extends TableCell<T, Date> {

    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a");
    private HBox box;
    private DatePicker datePicker;
    private TextField timeField;

    @Override
    public void startEdit() {
        if (!isEmpty()) {
            super.startEdit();
            updateView();
            Dialog<Date> dialog = new Dialog<>();
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.initOwner(getScene().getWindow());
            dialog.show();

        }
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        updateText();
    }

    @Override
    protected void updateItem(Date item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
        } else {
            if (isEditable())
                updateView();
            else
                updateText();
        }
    }

    private void updateText() {
        Date item = getItem();
        setText(item == null ? null : AssignGui.DATE_FORMAT.format(item));
        setGraphic(null);
    }

    private void updateView() {
        if (box == null) {
            box = new HBox(5);
            timeField = new TextField();
            datePicker = new DatePicker();
            box.getChildren().addAll(datePicker, timeField);
            timeField.setTextFormatter(new TextFormatter<Object>(new UnaryOperator<TextFormatter.Change>() {
                @Override
                public TextFormatter.Change apply(TextFormatter.Change change) {
                    return null;
                }
            }));
        }
        Date item = getItem();
        if (item == null)
            item = new Date();
        datePicker.setValue(item.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        timeField.setText(timeFormat.format(item));
        setText(null);
        setGraphic(box);
    }

}
