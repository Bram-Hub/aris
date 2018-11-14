package edu.rpi.aris.assign.client.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Parent;

public interface TabGui {

    void load(boolean reload);

    void unload();

    void closed();

    Parent getRoot();

    boolean isPermanentTab();

    String getName();

    SimpleStringProperty nameProperty();

}
