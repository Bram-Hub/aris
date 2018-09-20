package edu.rpi.aris.assign.client.model;

import edu.rpi.aris.assign.ServerRole;
import edu.rpi.aris.assign.User;
import edu.rpi.aris.assign.client.Client;
import edu.rpi.aris.assign.client.ResponseHandler;
import edu.rpi.aris.assign.message.ClassCreateMsg;
import edu.rpi.aris.assign.message.ClassDeleteMsg;
import edu.rpi.aris.assign.message.UserGetMsg;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableIntegerValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.StringConverter;

import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

public class UserInfo implements ResponseHandler<UserGetMsg> {

    private static final UserInfo instance = new UserInfo();

    private SimpleObjectProperty<ServerRole> defaultRole = new SimpleObjectProperty<>();
    private SimpleObjectProperty<ServerRole> classRole = new SimpleObjectProperty<>();
    private ObservableList<ClassInfo> classes = FXCollections.observableArrayList();
    private SimpleBooleanProperty loggedIn = new SimpleBooleanProperty();
    private SimpleIntegerProperty loading = new SimpleIntegerProperty();
    private SimpleObjectProperty<ClassInfo> selectedClass = new SimpleObjectProperty<>();

    private ClassCreateResponseHandler createHandler = new ClassCreateResponseHandler();
    private ClassDeleteResponseHandler deleteHandler = new ClassDeleteResponseHandler();

    private HashMap<Integer, ClassInfo> classMap = new HashMap<>();
    private User user;
    private ReentrantLock lock = new ReentrantLock(true);
    private Runnable onLoad;

    private UserInfo() {
        selectedClass.addListener((observable, oldValue, newValue) -> {
            if (newValue != null)
                LocalConfig.SELECTED_COURSE_ID.setValue(newValue.getClassId());
        });
        classRole.bind(Bindings.createObjectBinding(() -> selectedClass.get() == null ? defaultRole.get() : selectedClass.get().getUserRole(), selectedClass, defaultRole));
    }

    public static UserInfo getInstance() {
        return instance;
    }

    public ObservableList<ClassInfo> classesProperty() {
        return classes;
    }

    public BooleanBinding loadingBinding() {
        return loading.greaterThan(0);
    }

    public ObservableIntegerValue loadingProperty() {
        return loading;
    }

    public SimpleBooleanProperty loginProperty() {
        return loggedIn;
    }

    public SimpleObjectProperty<ServerRole> defaultRoleProperty() {
        return defaultRole;
    }

    public SimpleObjectProperty<ClassInfo> selectedClassProperty() {
        return selectedClass;
    }

    public boolean isLoggedIn() {
        return loggedIn.get();
    }

    public boolean isLoading() {
        return loading.get() > 0;
    }

    public void startLoading() {
        loading.set(loading.get() + 1);
    }

    public void finishLoading() {
        loading.set(loading.get() - 1);
    }

    public ServerRole getDefaultRole() {
        return defaultRole.get();
    }

    public void getUserInfo(boolean refresh, Runnable onLoad) {
        if (refresh || !loggedIn.get()) {
            if (lock.isLocked())
                return;
            this.onLoad = onLoad;
            startLoading();
            Client.getInstance().processMessage(new UserGetMsg(), this);
        }
    }

    public void logout() {
        loggedIn.set(false);
        classes.clear();
        classMap.clear();
        defaultRole.set(null);
        LocalConfig.USERNAME.setValue(null);
        LocalConfig.ACCESS_TOKEN.setValue(null);
    }

    public void createClass(String name) {
        startLoading();
        Client.getInstance().processMessage(new ClassCreateMsg(name), createHandler);
    }

    public void deleteClass(int classId) {
        startLoading();
        Client.getInstance().processMessage(new ClassDeleteMsg(classId), deleteHandler);
    }

    public User getUser() {
        return loggedIn.get() ? user : null;
    }

    @Override
    public void response(UserGetMsg message) {
        Platform.runLater(() -> {
            ServerConfig.setPermissions(message.getPermissions());
            user = new User(message.getUserId(), LocalConfig.USERNAME.getValue(), message.getDefaultRole(), false);
            defaultRole.set(message.getDefaultRole());
            classes.clear();
            classMap.clear();
            message.getClassNames().forEach((k, v) -> {
                ClassInfo info = new ClassInfo(k, v, message.getPermissions() == null ? null : message.getPermissions().getRole(message.getClassRoles().get(k)));
                classes.add(info);
                classMap.put(k, info);
            });
            Collections.sort(classes);
            selectedClass.set(classMap.get(LocalConfig.SELECTED_COURSE_ID.getValue()));
            if (selectedClass.get() == null && classes.size() > 0)
                selectedClass.set(classes.get(0));
            loggedIn.set(true);
            finishLoading();
            if (onLoad != null)
                onLoad.run();
        });
    }

    @Override
    public void onError(boolean suggestRetry, UserGetMsg msg) {
        Platform.runLater(() -> {
            loggedIn.set(false);
            finishLoading();
            classes.clear();
            classMap.clear();
            if (suggestRetry)
                getUserInfo(false, onLoad);
        });
    }

    @Override
    public ReentrantLock getLock() {
        return lock;
    }

    public ClassInfo getSelectedClass() {
        return selectedClass.get();
    }

    public ServerRole getClassRole() {
        return classRole.get();
    }

    public SimpleObjectProperty<ServerRole> classRoleProperty() {
        return classRole;
    }

    public static class ClassStringConverter extends StringConverter<ClassInfo> {
        @Override
        public String toString(ClassInfo object) {
            return object.getClassName();
        }

        @Override
        public ClassInfo fromString(String string) {
            return null;
        }
    }

    private class ClassDeleteResponseHandler implements ResponseHandler<ClassDeleteMsg> {

        @Override
        public void response(ClassDeleteMsg message) {
            Platform.runLater(() -> {
                ClassInfo info = classMap.get(message.getClassId());
                classes.remove(info);
                if (classes.size() > 0)
                    selectedClass.set(classes.get(0));
                finishLoading();
            });
        }

        @Override
        public void onError(boolean suggestRetry, ClassDeleteMsg msg) {
            if (suggestRetry)
                Client.getInstance().processMessage(msg, this);
            Platform.runLater(UserInfo.this::finishLoading);
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

    private class ClassCreateResponseHandler implements ResponseHandler<ClassCreateMsg> {

        @Override
        public void response(ClassCreateMsg message) {
            Platform.runLater(() -> {
                ClassInfo info = new ClassInfo(message.getClassId(), message.getClassName(), defaultRole.get());
                classMap.put(message.getClassId(), info);
                classes.add(info);
                Collections.sort(classes);
                selectedClass.set(info);
                finishLoading();
            });
        }

        @Override
        public void onError(boolean suggestRetry, ClassCreateMsg msg) {
            if (suggestRetry)
                Client.getInstance().processMessage(msg, this);
            Platform.runLater(UserInfo.this::finishLoading);
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

}
