package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.client.model.UserInfo;
import javafx.scene.Parent;

public class StudentAssignmentGui implements TabGui {

    private UserInfo userInfo = UserInfo.getInstance();

    @Override
    public void load(boolean reload) {

    }

    @Override
    public void unload() {

    }

    @Override
    public Parent getRoot() {
        return null;
    }

    @Override
    public boolean isPermanentTab() {
        return false;
    }
}
