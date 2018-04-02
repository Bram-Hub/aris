package edu.rpi.aris.gui.submit;

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

public class Course {

    private final int id;
    private SimpleBooleanProperty loaded = new SimpleBooleanProperty(false);
    private ObservableList<Assignment> assignments = FXCollections.observableArrayList();
    private String name;

    public Course(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public void load(Runnable runnable, boolean reload) {
        if (reload)
            loaded.set(false);
        if (loaded.get())
            return;
        assignments.clear();
        new Thread(() -> {
            Client client = Main.getClient();
            try {
                client.connect();
                client.sendMessage(NetUtil.GET_ASSIGNMENTS);
                client.sendMessage(String.valueOf(id));
                String res;
                while ((res = client.readMessage()) != null && !res.equals(NetUtil.ERROR) && !res.equals(NetUtil.DONE)) {
                    String[] split = res.split("\\|");
                    if (split.length == 4) {
                        try {
                            Platform.runLater(() -> {
                                try {
                                    assignments.add(new Assignment(URLDecoder.decode(split[0], "UTF-8"), URLDecoder.decode(split[1], "UTF-8"), URLDecoder.decode(split[2], "UTF-8"), Integer.parseInt(split[3]), id));
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
                    assignments.clear();
                    loaded.set(false);
                });
                System.out.println("Connection failed");
                //TODO: show error to client
            } finally {
                client.disconnect();
            }
        }).start();
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    public int getId() {
        return id;
    }

    public ObservableList<Assignment> getAssignments() {
        return assignments;
    }
}
