package edu.rpi.aris.assign.client.model;

import edu.rpi.aris.assign.ServerRole;
import edu.rpi.aris.assign.client.Client;
import edu.rpi.aris.assign.client.ResponseHandler;
import edu.rpi.aris.assign.client.controller.UsersGui;
import edu.rpi.aris.assign.message.UserListMsg;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.locks.ReentrantLock;

public class Users implements ResponseHandler<UserListMsg> {

    private final ObservableList<UserInfo> users = FXCollections.observableArrayList();
    private final CurrentUser userInfo = CurrentUser.getInstance();
    private final UsersGui gui;
    private final ReentrantLock lock = new ReentrantLock(true);
    private final SimpleBooleanProperty loadError = new SimpleBooleanProperty(false);
    private boolean loaded = false;

    public Users(UsersGui gui) {
        this.gui = gui;
    }

    public void loadUsers(boolean reload) {
        if (reload || !loaded) {
            userInfo.startLoading();
            clear();
            Client.getInstance().processMessage(new UserListMsg(), this);
        }
    }

    public void clear() {
        users.clear();
        loaded = false;
    }

    public boolean isLoadError() {
        return loadError.get();
    }

    public SimpleBooleanProperty loadErrorProperty() {
        return loadError;
    }

    public ObservableList<UserInfo> getUsers() {
        return users;
    }

    @Override
    public void response(UserListMsg message) {
        Platform.runLater(() -> {
            loadError.set(false);
            message.getUsers().stream().sorted().forEachOrdered(user -> users.add(new UserInfo(user)));
            loaded = true;
            userInfo.finishLoading();
        });
    }

    @Override
    public void onError(boolean suggestRetry, UserListMsg msg) {
        Platform.runLater(() -> {
            clear();
            loadError.set(true);
            userInfo.finishLoading();
            if (suggestRetry)
                loadUsers(true);
        });
    }

    @Override
    public ReentrantLock getLock() {
        return lock;
    }

    public class UserInfo {

        private final int uid;
        private final SimpleStringProperty username, fullName;
        private final SimpleObjectProperty<ServerRole> defaultRole;

        public UserInfo(int uid, @NotNull String username, @NotNull String fullName, ServerRole defaultRole) {
            this.uid = uid;
            this.username = new SimpleStringProperty(username);
            this.fullName = new SimpleStringProperty(fullName);
            this.defaultRole = new SimpleObjectProperty<>(defaultRole);
        }

        public UserInfo(UserListMsg.User user) {
            this(user.uid, user.username, user.fullName, ServerConfig.getPermissions().getRole(user.defaultRole));
        }

        public String getUsername() {
            return username.get();
        }

        public SimpleStringProperty usernameProperty() {
            return username;
        }

        public String getFullName() {
            return fullName.get();
        }

        public SimpleStringProperty fullNameProperty() {
            return fullName;
        }

        public ServerRole getDefaultRole() {
            return defaultRole.get();
        }

        public SimpleObjectProperty<ServerRole> defaultRoleProperty() {
            return defaultRole;
        }

        public int getUid() {
            return uid;
        }
    }

}
