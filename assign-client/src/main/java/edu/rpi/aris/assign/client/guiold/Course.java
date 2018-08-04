package edu.rpi.aris.assign.client.guiold;

import edu.rpi.aris.assign.NetUtil;
import edu.rpi.aris.assign.client.Client;
import edu.rpi.aris.assign.message.AssignmentsGetMsg;
import edu.rpi.aris.assign.message.MsgUtil;
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
//        Main.getClient().processMessage(new AssignmentsGetMsg(id), this);
        new Thread(() -> {
            Client client = Client.getInstance();
            try {
                client.connect();
                AssignmentsGetMsg msg = new AssignmentsGetMsg(id);
                AssignmentsGetMsg reply = (AssignmentsGetMsg) msg.sendAndGet(client);
                if (reply == null)
                    return;
                for (MsgUtil.AssignmentData date : reply.getAssignments()) {
                    Assignment assignment = new Assignment(date.name, NetUtil.zoneToLocal(date.dueDateUTC).toInstant().toEpochMilli(), date.assignedBy, date.id, this.id, this);
                    this.assignments.add(assignment);
                }
                Platform.runLater(() -> loaded.set(true));
                if (runnable != null)
                    runnable.run();
            } catch (Exception e) {
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

//    @Override
//    public void response(AssignmentsGetMsg message) {
//        for (MsgUtil.AssignmentData date : mess.getAssignments()) {
//            Assignment assignment = new Assignment(date.name, NetUtil.zoneToLocal(date.dueDateUTC).toInstant().toEpochMilli(), date.assignedBy, date.id, this.id, this);
//            this.assignments.add(assignment);
//        }
//        Platform.runLater(() -> loaded.set(true));
//        if (runnable != null)
//            runnable.run();
//    }
//
//    @Override
//    public void onError() {
//
//    }
}
