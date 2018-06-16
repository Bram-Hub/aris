package edu.rpi.aris.gui.submit;

import edu.rpi.aris.Main;
import edu.rpi.aris.net.MessageBuildException;
import edu.rpi.aris.net.MessageParseException;
import edu.rpi.aris.net.NetUtil;
import edu.rpi.aris.net.client.Client;
import edu.rpi.aris.net.message.AssignmentsMsg;
import edu.rpi.aris.net.message.Message;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;

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
                AssignmentsMsg msg = new AssignmentsMsg(id);
                msg.sendMessage(client);
                Message replyMsg = Message.parse(client);
                if (!(replyMsg instanceof AssignmentsMsg))
                    throw new MessageParseException("Unexpected message type received");
                AssignmentsMsg reply = (AssignmentsMsg) replyMsg;
                for (AssignmentsMsg.AssignmentData date : reply.getAssignments()) {
                    Assignment assignment = new Assignment(date.name, NetUtil.zoneToLocal(date.dueDateUTC).toInstant().toEpochMilli(), date.assignedBy, date.id, this.id, this);
                    this.assignments.add(assignment);
                }
                Platform.runLater(() -> loaded.set(true));
                if (runnable != null)
                    runnable.run();
            } catch (IOException | MessageParseException e) {
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
