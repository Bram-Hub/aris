package edu.rpi.aris.assign.client.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Parent;

public class PermissionsGui implements TabGui {
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
        return null;
    }

    @Override
    public boolean isPermanentTab() {
        return true;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public SimpleStringProperty nameProperty() {
        return null;
    }

    @Override
    public boolean requiresOnline() {
        return true;
    }
}
