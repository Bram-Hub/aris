package edu.rpi.aris.assign.client.controller;

import edu.rpi.aris.assign.LibAssign;
import edu.rpi.aris.assign.Permission;
import edu.rpi.aris.assign.ServerRole;
import edu.rpi.aris.assign.client.model.ServerConfig;
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

    private final Permission perm;
    @FXML
    private Label name;
    @FXML
    private Label description;
    @FXML
    private ChoiceBox<ServerRole> roleChoice;
    private HBox root;
    private ServerRole currentRole;

    public PermRowGui(Permission perm) {
        this.perm = perm;
        currentRole = ServerConfig.getPermissions().getRole(perm.getRollId());
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
        roleChoice.getSelectionModel().select(currentRole);
    }

    public HBox getRoot() {
        return root;
    }

    public Permission getPerm() {
        return perm;
    }

    public void commit() {
        currentRole = roleChoice.getSelectionModel().getSelectedItem();
    }

    public void revert() {
        roleChoice.getSelectionModel().select(currentRole);
    }

}
