package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.LibAssign;
import edu.rpi.aris.assign.Permission;
import edu.rpi.aris.assign.ServerRole;
import edu.rpi.aris.assign.client.model.ServerConfig;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;

import java.io.IOException;
import java.util.Comparator;
import java.util.stream.Collectors;

public class PermRowGui {

    private final BooleanProperty disableProperty;
    private Permission perm;
    @FXML
    private Label name;
    @FXML
    private Label description;
    @FXML
    private ChoiceBox<ServerRole> roleChoice;
    private HBox root;
    private SimpleObjectProperty<ServerRole> currentRole;
    private SimpleBooleanProperty modified = new SimpleBooleanProperty(false);

    public PermRowGui(Permission perm, BooleanProperty disableProperty) {
        this.perm = perm;
        currentRole = new SimpleObjectProperty<>(ServerConfig.getPermissions().getRole(perm.getRollId()));
        this.disableProperty = disableProperty;
        FXMLLoader loader = new FXMLLoader(PermRowGui.class.getResource("/edu/rpi/aris/assign/client/view/perm_row.fxml"));
        loader.setController(this);
        try {
            root = loader.load();
        } catch (IOException e) {
            LibAssign.getInstance().showExceptionError(Thread.currentThread(), e, true);
        }
    }

    @FXML
    public void initialize() {
        name.setText(perm.getName() + ":");
        description.setText(perm.getDescription());
        roleChoice.setConverter(new StringConverter<ServerRole>() {
            @Override
            public String toString(ServerRole role) {
                return role.getName();
            }

            @Override
            public ServerRole fromString(String string) {
                return null;
            }
        });
        roleChoice.getItems().setAll(ServerConfig.getPermissions().getRoles().stream().sorted(Comparator.comparingInt(ServerRole::getId)).collect(Collectors.toList()));
        roleChoice.getSelectionModel().select(currentRole.get());
        roleChoice.disableProperty().bind(disableProperty);
        modified.bind(currentRole.isNotEqualTo(roleChoice.getSelectionModel().selectedItemProperty()));
    }

    public HBox getRoot() {
        return root;
    }

    public Permission getPerm() {
        return perm;
    }

    public BooleanProperty modifiedProperty() {
        return modified;
    }

    public boolean isModified() {
        return modified.get();
    }

    public ServerRole getSelectedRole() {
        return roleChoice.getSelectionModel().getSelectedItem();
    }

    public void commit() {
        currentRole.set(roleChoice.getSelectionModel().getSelectedItem());
    }

    public void revert() {
        roleChoice.getSelectionModel().select(currentRole.get());
    }

    public void updateRole() {
        perm = ServerConfig.getPermissions().getPermission(perm.getPerm());
        ServerRole role = ServerConfig.getPermissions().getRole(perm.getRollId());
        currentRole.set(role);
        roleChoice.getSelectionModel().select(role);
    }
}
