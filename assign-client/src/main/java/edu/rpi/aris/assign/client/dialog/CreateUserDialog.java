package edu.rpi.aris.assign.client.dialog;

import edu.rpi.aris.assign.AuthType;
import edu.rpi.aris.assign.ServerRole;
import edu.rpi.aris.assign.client.Client;
import edu.rpi.aris.assign.client.ResponseHandler;
import edu.rpi.aris.assign.client.model.ServerConfig;
import edu.rpi.aris.assign.client.model.Users;
import edu.rpi.aris.assign.message.UserCreateMsg;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

public class CreateUserDialog extends Dialog<Users.UserInfo> implements EventHandler<ActionEvent>, ResponseHandler<UserCreateMsg> {

    private final SimpleBooleanProperty loading = new SimpleBooleanProperty(false);
    @FXML
    private TextField username;
    @FXML
    private TextField fullName;
    @FXML
    private PasswordField password;
    @FXML
    private ComboBox<ServerRole> defaultRole;
    private Button okBtn, cancelBtn;
    private ReentrantLock lock = new ReentrantLock(true);
    private Users.UserInfo result = null;

    public CreateUserDialog(Window parent) throws IOException {
        initModality(Modality.WINDOW_MODAL);
        initOwner(parent);
        FXMLLoader loader = new FXMLLoader(CreateUserDialog.class.getResource("/edu/rpi/aris/assign/client/view/create_user_dialog.fxml"));
        loader.setController(this);
        getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        setTitle("Add User");
        setHeaderText("Add user to the server");
        okBtn = (Button) getDialogPane().lookupButton(ButtonType.OK);
        cancelBtn = (Button) getDialogPane().lookupButton(ButtonType.CANCEL);
        okBtn.setDisable(true);
        okBtn.addEventFilter(ActionEvent.ACTION, this);
        getDialogPane().setContent(loader.load());
        setResultConverter(param -> result);
    }

    @FXML
    private void initialize() {
        defaultRole.getItems().addAll(ServerConfig.getPermissions().getRoles());
        defaultRole.setConverter(ServerConfig.getRoleStringConverter());
        okBtn.disableProperty().bind(username.textProperty().isEmpty()
                .or(fullName.textProperty().isEmpty())
                .or(password.textProperty().isEmpty())
                .or(defaultRole.getSelectionModel().selectedItemProperty().isNull())
                .or(loading));
        cancelBtn.disableProperty().bind(loading);
        username.disableProperty().bind(loading);
        fullName.disableProperty().bind(loading);
        password.disableProperty().bind(loading);
        defaultRole.disableProperty().bind(loading);
    }

    @Override
    public void handle(ActionEvent event) {
        if (event != null)
            event.consume();
        loading.set(true);
        Client.getInstance().processMessage(new UserCreateMsg(username.getText(), fullName.getText(), password.getText(), defaultRole.getSelectionModel().getSelectedItem().getId(), AuthType.LOCAL), this);
    }

    @Override
    public void response(UserCreateMsg message) {
        Users.UserInfo info = new Users.UserInfo(message.getUid(), message.getUsername(), message.getFullName(), defaultRole.getSelectionModel().getSelectedItem());
        Platform.runLater(() -> {
            loading.set(false);
            result = info;
            close();
        });
    }

    @Override
    public void onError(boolean suggestRetry, UserCreateMsg msg) {
        Platform.runLater(() -> {
            if (suggestRetry)
                handle(null);
            else
                loading.set(false);
        });
    }

    @Override
    public ReentrantLock getLock() {
        return lock;
    }
}
