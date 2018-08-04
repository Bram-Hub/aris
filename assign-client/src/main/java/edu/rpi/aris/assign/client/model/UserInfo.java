package edu.rpi.aris.assign.client.model;

import edu.rpi.aris.assign.UserType;
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
import javafx.util.Pair;
import javafx.util.StringConverter;

public class UserInfo implements ResponseHandler<UserGetMsg> {

    private SimpleObjectProperty<UserType> userType = new SimpleObjectProperty<>();
    private ObservableList<Pair<String, Integer>> classes = FXCollections.observableArrayList();
    private SimpleBooleanProperty loggedIn = new SimpleBooleanProperty();
    private SimpleBooleanProperty loading = new SimpleBooleanProperty();

    private ClassCreateResponseHandler createHandler = new ClassCreateResponseHandler();
    private ClassDeleteResponseHandler deleteHandler = new ClassDeleteResponseHandler();

    public ObservableList<Pair<String, Integer>> classesProperty() {
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
            message.getClasses().forEach((k, v) -> classes.add(new Pair<>(v, k)));
            loggedIn.set(true);
            loading.set(false);
        });
    }

    @Override
    public void onError(boolean suggestRetry) {
        Platform.runLater(() -> {
            loggedIn.set(false);
            loading.set(false);
        });
        if (suggestRetry)
            getUserInfo(false);
    }

    public static class ClassStringConverter extends StringConverter<Pair<String, Integer>> {
        @Override
        public String toString(Pair<String, Integer> object) {
            return object.getKey();
        }

        @Override
        public Pair<String, Integer> fromString(String string) {
            return null;
        }
    }

    private static class ClassCreateResponseHandler implements ResponseHandler<ClassCreateMsg> {

        @Override
        public void response(ClassCreateMsg message) {

        }

        @Override
        public void onError(boolean suggestRetry) {

        }
    }

    private static class ClassDeleteResponseHandler implements ResponseHandler<ClassDeleteMsg> {

        @Override
        public void response(ClassDeleteMsg message) {

        }

        @Override
        public void onError(boolean suggestRetry) {

        }
    }

}
