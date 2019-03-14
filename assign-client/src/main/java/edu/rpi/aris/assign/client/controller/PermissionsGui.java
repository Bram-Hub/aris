package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.LibAssign;
import edu.rpi.aris.assign.Perm;
import edu.rpi.aris.assign.Permission;
import edu.rpi.aris.assign.ServerPermissions;
import edu.rpi.aris.assign.client.Client;
import edu.rpi.aris.assign.client.ResponseHandler;
import edu.rpi.aris.assign.client.model.ServerConfig;
import edu.rpi.aris.assign.message.PermissionEditMsg;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

public class PermissionsGui implements TabGui, ResponseHandler<PermissionEditMsg> {

    private static final ReentrantLock lock = new ReentrantLock(true);
    private final SimpleStringProperty tabName = new SimpleStringProperty("Permissions");
    @FXML
    private VBox permissionBox;
    @FXML
    private Button btnApply;
    @FXML
    private Button btnRevert;
    private Parent root;
    private ArrayList<PermRowGui> permRows = new ArrayList<>();
    private SimpleBooleanProperty updating = new SimpleBooleanProperty(false);
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

    @Override
    public synchronized void load(boolean reload) {
        if (!loaded) {
            Platform.runLater(() -> {
                synchronized (PermissionsGui.this) {
                    BooleanBinding binding = Bindings.createBooleanBinding(() -> false);
                    for (Perm.Group g : Perm.Group.values()) {
                        Label lbl = new Label(g.description + ":");
                        lbl.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, 14));
                        permissionBox.getChildren().add(lbl);
                        for (Perm p : g.permissions) {
                            Permission perm = ServerConfig.getPermissions().getPermission(p);
                            PermRowGui row = new PermRowGui(perm, updating);
                            binding = binding.or(row.modifiedProperty());
                            permRows.add(row);
                            permissionBox.getChildren().add(row.getRoot());
                        }
                    }
                    btnApply.disableProperty().bind(binding.not().or(updating));
                    btnRevert.disableProperty().bind(binding.not().or(updating));
                    loaded = true;
                }
            });
        } else if (reload) {
            Platform.runLater(() -> {
                synchronized (PermissionsGui.this) {
                    for (PermRowGui p : permRows) {
                        if (!p.isModified())
                            p.updateRole();
                    }
                }
            });
        }
    }

    @FXML
    public void apply() {
        updating.set(true);
        PermissionEditMsg msg = new PermissionEditMsg();
        for (PermRowGui p : permRows) {
            if (p.isModified())
                msg.addPermission(p.getPerm().getPerm(), p.getSelectedRole());
        }
        Client.getInstance().processMessage(msg, this);
    }

    @FXML
    public void revert() {
        for (PermRowGui row : permRows)
            row.revert();
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

    @Override
    public void response(PermissionEditMsg message) {
        Platform.runLater(() -> {
            ServerPermissions permissions = ServerConfig.getPermissions();
            message.getPermMap().forEach((p, id) -> permissions.getPermission(p).setRoleId(id));
            for (PermRowGui p : permRows) {
                if (message.getPermMap().containsKey(p.getPerm().getPerm()))
                    p.updateRole();
            }
            updating.set(false);
        });
    }

    @Override
    public void onError(boolean suggestRetry, PermissionEditMsg msg) {
        Platform.runLater(() -> {
            if (suggestRetry)
                apply();
            else
                updating.set(false);
        });
    }

    @Override
    public ReentrantLock getLock() {
        return lock;
    }
}
