package edu.rpi.aris.assign.client.model;

import edu.rpi.aris.assign.ServerRole;
import edu.rpi.aris.assign.client.Client;
import edu.rpi.aris.assign.client.ResponseHandler;
import edu.rpi.aris.assign.client.controller.ClassGui;
import edu.rpi.aris.assign.message.ClassUserListMsg;
import edu.rpi.aris.assign.message.MsgUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Comparator;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class ClassModel {

    private final ClassGui gui;
    private final CurrentUser userInfo = CurrentUser.getInstance();
    private final ClassUserListHandler listHandler = new ClassUserListHandler();
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
            userInfo.startLoading();
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

    public class ClassUserListHandler implements ResponseHandler<ClassUserListMsg> {

        @Override
        public void response(ClassUserListMsg message) {
            Platform.runLater(() -> {
                synchronized (ClassModel.this) {
                    userInfo.finishLoading();
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
                userInfo.finishLoading();
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
    }

}
