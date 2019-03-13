package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.LibAssign;
import edu.rpi.aris.assign.Perm;
import edu.rpi.aris.assign.Permission;
import edu.rpi.aris.assign.client.model.ServerConfig;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.ArrayList;

public class PermissionsGui implements TabGui {

    private final SimpleStringProperty tabName = new SimpleStringProperty("Permissions");
    @FXML
    private VBox permissionBox;
    private Parent root;
    private ArrayList<PermRowGui> permRows = new ArrayList<>();
    private boolean loaded = false;

    public PermissionsGui() {
        FXMLLoader loader = new FXMLLoader(PermissionsGui.class.getResource("/edu/rpi/aris/assign/client/view/permissions_view.fxml"));
        loader.setController(this);
        try {
            root = loader.load();
        } catch (IOException e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, true);
        }
    }

    @FXML
    public void initialize() {

    }

    @Override
    public synchronized void load(boolean reload) {
        if (!loaded) {
            Platform.runLater(() -> {
                synchronized (PermissionsGui.this) {
                    for (Perm p : Perm.values()) {
                        Permission perm = ServerConfig.getPermissions().getPermission(p);
                        PermRowGui row = new PermRowGui(perm);
                        permRows.add(row);
                        permissionBox.getChildren().add(row.getRoot());
                    }
                    loaded = true;
                }
            });
        }
    }

    @Override
    public void unload() {
        // don't do anything here since CurrentUser handles permission loading
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
        return tabName.getName();
    }

    @Override
    public SimpleStringProperty nameProperty() {
        return tabName;
    }

    @Override
    public boolean requiresOnline() {
        return true;
    }
}
