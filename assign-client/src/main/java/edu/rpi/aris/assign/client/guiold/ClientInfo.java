package edu.rpi.aris.assign.client.guiold;

import edu.rpi.aris.assign.client.Client;
import edu.rpi.aris.assign.client.model.LocalConfig;
import edu.rpi.aris.assign.message.UserGetMsg;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class ClientInfo {

    private SimpleBooleanProperty loaded = new SimpleBooleanProperty(false);
    private SimpleBooleanProperty isInstructor = new SimpleBooleanProperty(false);
    private ObservableList<Course> courses = FXCollections.observableArrayList();
    private int userId = -1;

    public void load(Runnable runnable, boolean reload) {
        if (!reload && loaded.get())
            return;
        isInstructor.set(false);
        courses.clear();
        new Thread(() -> {
            Client client = Client.getInstance();
            try {
                client.connect();
                UserGetMsg reply = (UserGetMsg) new UserGetMsg().sendAndGet(client);
                if (reply == null)
                    return;
                userId = reply.getUserId();
                Platform.runLater(() -> {
//                    isInstructor.set(reply.getDefaultRole().equals(NetUtil.USER_INSTRUCTOR));
                    reply.getClasses().forEach((id, name) -> courses.add(new Course(id, name.getKey())));
                    loaded.set(true);
                });
                if (runnable != null)
                    runnable.run();
            } catch (Exception e) {
                userId = -1;
                Platform.runLater(() -> {
                    isInstructor.set(false);
                    courses.clear();
                    loaded.set(false);
                });
                System.out.println("Connection failed");
                //TODO: show error to client
            } finally {
                client.disconnect();
            }
        }).start();
    }

    public void logout() {
        loaded.set(false);
        isInstructor.set(false);
        courses.clear();
        userId = -1;
        LocalConfig.USERNAME.setValue(null);
        LocalConfig.ACCESS_TOKEN.setValue(null);
    }

    public SimpleBooleanProperty isInstructorProperty() {
        return isInstructor;
    }

    public SimpleBooleanProperty loadedProperty() {
        return loaded;
    }

    public ObservableList<Course> getCourses() {
        return courses;
    }

    public int getUserId() {
        return userId;
    }

}
