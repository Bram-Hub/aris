package edu.rpi.aris.assign.client.controller;

import javafx.scene.Parent;

public interface TabGui {

    void load(boolean reload);

    void unload();

    Parent getRoot();

    boolean isPermanentTab();

}
