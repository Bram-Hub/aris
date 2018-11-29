package edu.rpi.aris.assign.client.model;

import edu.rpi.aris.assign.ServerRole;
import edu.rpi.aris.assign.client.Client;
import edu.rpi.aris.assign.client.ResponseHandler;
import edu.rpi.aris.assign.message.*;
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
    private final ReentrantLock lock = new ReentrantLock(true);
    private final SimpleBooleanProperty loadError = new SimpleBooleanProperty(false);
    private final PasswordChangeResponseHandler passwordChangeHandler = new PasswordChangeResponseHandler();
    private boolean loaded = false;
    private ResponseHandler<UserDeleteMsg> userDeleteHandler = new UserDeleteHandler();

    public synchronized void loadUsers(boolean reload) {
        if (reload || !loaded) {
            clear();
            Client.getInstance().processMessage(new UserListMsg(), this);
        }
    }

    public void fullNameChanged(UserInfo info, String oldName, String newName) {
        Client.getInstance().processMessage(new UserEditMsg(info.uid, newName), new UserRenameResponseHandler(info, oldName));
    }

    public void roleChanged(UserInfo info, ServerRole oldRole, ServerRole newRole) {
        roleChanged(info, oldRole, newRole.getId());
    }

    private void roleChanged(UserInfo info, ServerRole oldRole, int newRole) {
        Client.getInstance().processMessage(new UserEditMsg(info.uid, newRole), new UserRoleChangeResponseHandler(info, oldRole));
    }

    public void resetPassword(String username, String oldPass, String newPass) {
        Client.getInstance().processMessage(new UserChangePasswordMsg(username, newPass, oldPass), passwordChangeHandler);
    }

    public synchronized void clear() {
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
        });
    }

    @Override
    public void onError(boolean suggestRetry, UserListMsg msg) {
        Platform.runLater(() -> {
            clear();
            loadError.set(true);
            if (suggestRetry)
                loadUsers(true);
        });
    }

    @Override
    public ReentrantLock getLock() {
        return lock;
    }

    public void addUser(UserInfo userInfo) {
        users.add(userInfo);
    }

    public void deleteUser(int uid) {
        Client.getInstance().processMessage(new UserDeleteMsg(uid), userDeleteHandler);
    }

    public static class UserInfo {

        private final int uid;
        private final SimpleStringProperty username, fullName;
        private final SimpleObjectProperty<ServerRole> defaultRole;

        public UserInfo(int uid, @NotNull String username, @NotNull String fullName, ServerRole defaultRole) {
            this.uid = uid;
            this.username = new SimpleStringProperty(username);
            this.fullName = new SimpleStringProperty(fullName);
            this.defaultRole = new SimpleObjectProperty<>(defaultRole == null ? ServerConfig.getPermissions().getLowestRole() : defaultRole);
        }

        public UserInfo(MsgUtil.UserInfo user) {
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

    private class UserRenameResponseHandler implements ResponseHandler<UserEditMsg> {

        private final UserInfo info;
        private final String oldName;

        UserRenameResponseHandler(UserInfo info, String oldName) {
            this.info = info;
            this.oldName = oldName;
        }

        @Override
        public void response(UserEditMsg message) {
        }

        @Override
        public void onError(boolean suggestRetry, UserEditMsg msg) {
            if (suggestRetry)
                fullNameChanged(info, oldName, msg.getNewName());
            else
                Platform.runLater(() -> info.fullName.set(oldName));
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

    private class UserRoleChangeResponseHandler implements ResponseHandler<UserEditMsg> {

        private final UserInfo info;
        private final ServerRole oldRole;

        UserRoleChangeResponseHandler(UserInfo info, ServerRole oldRole) {
            this.info = info;
            this.oldRole = oldRole;
        }

        @Override
        public void response(UserEditMsg message) {
        }

        @Override
        public void onError(boolean suggestRetry, UserEditMsg msg) {
            if (suggestRetry)
                roleChanged(info, oldRole, msg.getNewDefaultRole());
            else
                Platform.runLater(() -> info.defaultRole.set(oldRole));
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

    private class UserDeleteHandler implements ResponseHandler<UserDeleteMsg> {

        @Override
        public void response(UserDeleteMsg message) {
            Platform.runLater(() -> users.removeIf(info -> info.getUid() == message.getUid()));
        }

        @Override
        public void onError(boolean suggestRetry, UserDeleteMsg msg) {
            if (suggestRetry)
                deleteUser(msg.getUid());
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

    private class PasswordChangeResponseHandler implements ResponseHandler<UserChangePasswordMsg> {

        @Override
        public void response(UserChangePasswordMsg message) {
            if (message.getUsername() != null && message.getUsername().equals(LocalConfig.USERNAME.getValue()))
                LocalConfig.ACCESS_TOKEN.setValue(message.getAccessToken());
        }

        @Override
        public void onError(boolean suggestRetry, UserChangePasswordMsg msg) {
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

}
