package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.LibAssign;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;

public class ProblemsGui implements TabGui {

    private Parent root;

    public ProblemsGui() {
        FXMLLoader loader = new FXMLLoader(ProblemsGui.class.getResource("../view/problems_view.fxml"));
        loader.setController(this);
        try {
            root = loader.load();
        } catch (IOException e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, true);
        }
    }

    public Parent getRoot() {
        return root;
    }

    @Override
    public boolean isPermanentTab() {
        return true;
    }

    @Override
    public void load(boolean reload) {

    }

    @Override
    public void unload() {

    }
}
