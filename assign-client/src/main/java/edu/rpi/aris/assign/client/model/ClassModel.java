package edu.rpi.aris.assign.client.model;

import edu.rpi.aris.assign.Pair;
import edu.rpi.aris.assign.ServerRole;
import edu.rpi.aris.assign.client.Client;
import edu.rpi.aris.assign.client.ResponseHandler;
import edu.rpi.aris.assign.client.controller.ClassGui;
import edu.rpi.aris.assign.message.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class ClassModel {

    private final ClassGui gui;
    private final CurrentUser userInfo = CurrentUser.getInstance();
    private final ClassUserListHandler listHandler = new ClassUserListHandler();
    private final UserClassAddHandler addHandler = new UserClassAddHandler();
    private final UserClassRemoveHandler removeHandler = new UserClassRemoveHandler();
    private final ReentrantLock lock = new ReentrantLock(true);
    private final ObservableList<User> notInClass = FXCollections.observableArrayList();
    private final ObservableList<User> inClass = FXCollections.observableArrayList();
    private int loaded = -1;

    public ClassModel(ClassGui gui) {
        this.gui = gui;
    }

    public synchronized void load(boolean reload) {
        if (userInfo.getSelectedClass() == null)
            return;
        if (reload || loaded != userInfo.getSelectedClass().getClassId()) {
            Client.getInstance().processMessage(new ClassUserListMsg(userInfo.getSelectedClass().getClassId()), listHandler);
        }
    }

    public synchronized void clear() {
        notInClass.clear();
        inClass.clear();
        loaded = -1;
    }

    public ObservableList<User> getNotInClass() {
        return notInClass;
    }

    public ObservableList<User> getInClass() {
        return inClass;
    }

    public void roleChanged(User user, int cid, ServerRole old, ServerRole newValue) {
        roleChanged(user, cid, old, newValue.getId());
    }

    public void roleChanged(User user, int cid, ServerRole old, int newRole) {
        Client.getInstance().processMessage(new UserEditMsg(user.getUid(), new Pair<>(cid, newRole)), new UserRoleChangedHandler(user, old));
    }

    public void removeFromClass(int classId, int uid) {
        Client.getInstance().processMessage(new UserClassRemoveMsg(classId, uid), removeHandler);
    }

    public void addToClass(int classId, Set<Integer> selectedUids) {
        Client.getInstance().processMessage(new UserClassAddMsg(classId, selectedUids), addHandler);
    }

    public class ClassUserListHandler implements ResponseHandler<ClassUserListMsg> {

        @Override
        public void response(ClassUserListMsg message) {
            Platform.runLater(() -> {
                synchronized (ClassModel.this) {
                    clear();
                    notInClass.addAll(message.getUsersNotInClass().entrySet().stream()
                            .map(e -> new User(e.getKey(), null, e.getValue().getFirst(), e.getValue().getSecond()))
                            .sorted(Comparator.comparing(o -> o.username.get()))
                            .collect(Collectors.toList()));
                    inClass.addAll(message.getUserInClass().entrySet().stream()
                            .map(e -> new User(e.getValue()))
                            .sorted(Comparator.comparing(o -> o.username.get()))
                            .collect(Collectors.toList()));
                    loaded = message.getClassId();
                }
            });
        }

        @Override
        public void onError(boolean suggestRetry, ClassUserListMsg msg) {
            Platform.runLater(() -> {
                clear();
                if (suggestRetry)
                    load(true);
            });
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

    public class UserClassAddHandler implements ResponseHandler<UserClassAddMsg> {

        @Override
        public void response(UserClassAddMsg message) {
            Platform.runLater(() -> {
                if (userInfo.getSelectedClass() != null && message.getClassId() == userInfo.getSelectedClass().getClassId()) {
                    Set<User> newUsers = notInClass.parallelStream()
                            .filter(user -> message.getRoleIds().containsKey(user.uid))
                            .collect(Collectors.toSet());
                    newUsers.forEach(u -> u.classRoleProperty().set(ServerConfig.getPermissions().getRole(message.getRoleIds().get(u.uid))));
                    inClass.addAll(newUsers);
                    notInClass.removeAll(newUsers);
                }
            });
        }

        @Override
        public void onError(boolean suggestRetry, UserClassAddMsg msg) {
            if (suggestRetry)
                addToClass(msg.getClassId(), msg.getUserIds());
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

    public class UserClassRemoveHandler implements ResponseHandler<UserClassRemoveMsg> {

        @Override
        public void response(UserClassRemoveMsg message) {
            Platform.runLater(() -> {
                if (userInfo.getSelectedClass() != null && userInfo.getSelectedClass().getClassId() == message.getClassId()) {
                    User user = null;
                    for (User u : inClass) {
                        if (u.getUid() == message.getUid())
                            user = u;
                    }
                    if (user == null)
                        return;
                    inClass.remove(user);
                    notInClass.add(user);
                    notInClass.sort(Comparator.comparing(User::getUsername));
                }
            });
        }

        @Override
        public void onError(boolean suggestRetry, UserClassRemoveMsg msg) {
            if (suggestRetry)
                removeFromClass(msg.getClassId(), msg.getUid());
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

    public class UserRoleChangedHandler implements ResponseHandler<UserEditMsg> {

        private final User user;
        private final ServerRole oldRole;

        UserRoleChangedHandler(User user, ServerRole oldRole) {
            this.user = user;
            this.oldRole = oldRole;
        }

        @Override
        public void response(UserEditMsg message) {
        }

        @Override
        public void onError(boolean suggestRetry, UserEditMsg msg) {
            Pair<Integer, Integer> classRole = msg.getNewClassRole();
            if (suggestRetry && classRole != null)
                roleChanged(user, classRole.getFirst(), oldRole, classRole.getSecond());
            else
                Platform.runLater(() -> user.classRole.set(oldRole));
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

    public class User {
        private final int uid;
        private final SimpleObjectProperty<ServerRole> classRole;
        private final SimpleStringProperty username;
        private final SimpleStringProperty fullName;

        public User(int uid, ServerRole classRole, String username, String fullName) {
            this.uid = uid;
            this.classRole = new SimpleObjectProperty<>(classRole);
            this.username = new SimpleStringProperty(username);
            this.fullName = new SimpleStringProperty(fullName);
        }

        public User(MsgUtil.UserInfo value) {
            this(value.uid, ServerConfig.getPermissions().getRole(value.defaultRole), value.username, value.fullName);
        }

        public String getUsername() {
            return username.get();
        }

        public String getFullName() {
            return fullName.get();
        }

        public ServerRole getClassRole() {
            return classRole.get();
        }

        public SimpleStringProperty usernameProperty() {
            return username;
        }

        public SimpleStringProperty fullNameProperty() {
            return fullName;
        }

        public SimpleObjectProperty<ServerRole> classRoleProperty() {
            return classRole;
        }

        public int getUid() {
            return uid;
        }
    }

}
