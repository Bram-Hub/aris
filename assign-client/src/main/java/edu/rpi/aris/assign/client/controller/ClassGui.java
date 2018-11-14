package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.LibAssign;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;

public class ClassGui implements TabGui {
    private Parent root;

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

    }

    @Override
    public void unload() {

    }

    @Override
    public void closed() {

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
