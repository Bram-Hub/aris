package edu.rpi.aris.gui.submit;

import edu.rpi.aris.gui.GuiConfig;
import edu.rpi.aris.Main;
import edu.rpi.aris.net.NetUtil;
import edu.rpi.aris.net.client.Client;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

public class ClientInfo {

    private SimpleBooleanProperty loaded = new SimpleBooleanProperty(false);
    private SimpleBooleanProperty isInstructor = new SimpleBooleanProperty(false);
    private ObservableList<Course> courses = FXCollections.observableArrayList();

    public void load(Runnable runnable, boolean reload) {
        if (reload)
            loaded.set(false);
        if (loaded.get())
            return;
        isInstructor.set(false);
        courses.clear();
        new Thread(() -> {
            Client client = Main.getClient();
            try {
                client.connect();
                client.sendMessage(NetUtil.GET_USER_INFO);
                String clientType = client.readMessage();
                Platform.runLater(() -> isInstructor.set(NetUtil.USER_INSTRUCTOR.equals(clientType)));
                String res;
                while ((res = client.readMessage()) != null && !res.equals(NetUtil.ERROR) && !res.equals(NetUtil.DONE)) {
                    String[] split = res.split("\\|");
                    if (split.length == 2) {
                        try {
                            Platform.runLater(() -> {
                                try {
                                    courses.add(new Course(Integer.parseInt(split[0]), URLDecoder.decode(split[1], "UTF-8")));
                                } catch (UnsupportedEncodingException e) {
                                    e.printStackTrace();
                                }
                            });
                        } catch (NumberFormatException e) {
                            throw new IOException("Error fetching user data");
                        }
                    } else {
                        throw new IOException("Error fetching user data");
                    }
                }
                if (res == null || res.equals(NetUtil.ERROR))
                    throw new IOException("Error fetching user data");
                Platform.runLater(() -> loaded.set(true));
                if (runnable != null)
                    runnable.run();
            } catch (IOException e) {
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
}
