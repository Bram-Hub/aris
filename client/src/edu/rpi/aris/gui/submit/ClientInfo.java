package edu.rpi.aris.gui.submit;

import edu.rpi.aris.Main;
import edu.rpi.aris.gui.GuiConfig;
import edu.rpi.aris.net.MessageParseException;
import edu.rpi.aris.net.NetUtil;
import edu.rpi.aris.net.client.Client;
import edu.rpi.aris.net.message.Message;
import edu.rpi.aris.net.message.UserInfoMsg;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;

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
            Client client = Main.getClient();
            try {
                client.connect();
                UserInfoMsg msg = new UserInfoMsg();
                msg.send(client);
                Message replyMsg = Message.parse(client);
                if (!(replyMsg instanceof UserInfoMsg))
                    throw new MessageParseException("Unexpected message type received");
                UserInfoMsg reply = (UserInfoMsg) replyMsg;
                userId = reply.getUserId();
                Platform.runLater(() -> {
                    isInstructor.set(reply.getUserType().equals(NetUtil.USER_INSTRUCTOR));
                    reply.getClasses().forEach((id, name) -> courses.add(new Course(id, name)));
                    loaded.set(true);
                });
                if (runnable != null)
                    runnable.run();
            } catch (IOException | MessageParseException e) {
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
        GuiConfig.getConfigManager().username.set(null);
        GuiConfig.getConfigManager().setAccessToken(null);
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
