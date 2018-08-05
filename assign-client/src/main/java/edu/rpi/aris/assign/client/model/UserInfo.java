package edu.rpi.aris.assign.client.model;

import edu.rpi.aris.assign.UserType;
import edu.rpi.aris.assign.client.AssignClient;
import edu.rpi.aris.assign.client.Client;
import edu.rpi.aris.assign.client.ResponseHandler;
import edu.rpi.aris.assign.message.ClassCreateMsg;
import edu.rpi.aris.assign.message.ClassDeleteMsg;
import edu.rpi.aris.assign.message.UserGetMsg;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.StringConverter;

import java.util.Collections;
import java.util.HashMap;

public class UserInfo implements ResponseHandler<UserGetMsg> {

    private SimpleObjectProperty<UserType> userType = new SimpleObjectProperty<>();
    private ObservableList<ClassInfo> classes = FXCollections.observableArrayList();
    private SimpleBooleanProperty loggedIn = new SimpleBooleanProperty();
    private SimpleBooleanProperty loading = new SimpleBooleanProperty();
    private SimpleObjectProperty<ClassInfo> selectedClass = new SimpleObjectProperty<>();

    private ClassCreateResponseHandler createHandler = new ClassCreateResponseHandler();
    private ClassDeleteResponseHandler deleteHandler = new ClassDeleteResponseHandler();

    private HashMap<Integer, ClassInfo> classMap = new HashMap<>();

    public ObservableList<ClassInfo> classesProperty() {
        return classes;
    }

    public SimpleBooleanProperty loadingProperty() {
        return loading;
    }

    public SimpleBooleanProperty loginProperty() {
        return loggedIn;
    }

    public SimpleObjectProperty<UserType> userTypeProperty() {
        return userType;
    }

    public SimpleObjectProperty<ClassInfo> selectedClassProperty() {
        return selectedClass;
    }

    public boolean isLoggedIn() {
        return loggedIn.get();
    }

    public boolean isLoading() {
        return loading.get();
    }

    public UserType getUserType() {
        return userType.get();
    }

    public void getUserInfo(boolean refresh) {
        if (refresh || !loggedIn.get()) {
            loading.set(true);
            Client.getInstance().processMessage(new UserGetMsg(), this);
        }
    }

    public void createClass(String name) {
        Client.getInstance().processMessage(new ClassCreateMsg(name), createHandler);
    }

    public void deleteClass(int classId) {
        Client.getInstance().processMessage(new ClassDeleteMsg(classId), deleteHandler);
    }

    @Override
    public void response(UserGetMsg message) {
        Platform.runLater(() -> {
            userType.set(message.getUserType());
            classes.clear();
            classMap.clear();
            message.getClasses().forEach((k, v) -> {
                ClassInfo info = new ClassInfo(k, v);
                classes.add(info);
                classMap.put(k, info);
            });
            Collections.sort(classes);
            loggedIn.set(true);
            loading.set(false);
        });
    }

    @Override
    public void onError(boolean suggestRetry, UserGetMsg msg) {
        Platform.runLater(() -> {
            loggedIn.set(false);
            loading.set(false);
        });
        if (suggestRetry)
            getUserInfo(false);
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
            });
        }

        @Override
        public void onError(boolean suggestRetry, ClassDeleteMsg msg) {
            if (suggestRetry)
                Client.getInstance().processMessage(msg, this);
            else
                AssignClient.getInstance().getMainWindow().displayErrorMsg("Error Deleting Class", "An error occured while attempting to delete the class");
        }
    }

    private class ClassCreateResponseHandler implements ResponseHandler<ClassCreateMsg> {

        @Override
        public void response(ClassCreateMsg message) {
            Platform.runLater(() -> {
                ClassInfo info = new ClassInfo(message.getClassId(), message.getClassName());
                classMap.put(message.getClassId(), info);
                classes.add(info);
                classes.a
                Collections.sort(classes);
            });
        }

        @Override
        public void onError(boolean suggestRetry, ClassCreateMsg msg) {
            if (suggestRetry)
                Client.getInstance().processMessage(msg, this);
            else
                AssignClient.getInstance().getMainWindow().displayErrorMsg("Error Creating Class", "An error occurred while attempting to create the class");
        }
    }

}
