package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.client.model.ClassModel;
import javafx.scene.control.ListCell;

public class UserListCell extends ListCell<ClassModel.User> {

    @Override
    protected void updateItem(ClassModel.User item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
        } else {
            setText(item.getUsername());
            setGraphic(null);
        }
    }
}
