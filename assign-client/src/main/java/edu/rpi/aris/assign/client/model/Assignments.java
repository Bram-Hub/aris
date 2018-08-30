package edu.rpi.aris.assign.client.model;

import edu.rpi.aris.assign.NetUtil;
import edu.rpi.aris.assign.client.Client;
import edu.rpi.aris.assign.client.ResponseHandler;
import edu.rpi.aris.assign.client.controller.AssignmentsGui;
import edu.rpi.aris.assign.message.AssignmentsGetMsg;
import edu.rpi.aris.assign.message.MsgUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Collections;
import java.util.Date;

public class Assignments implements ResponseHandler<AssignmentsGetMsg> {

    private final ObservableList<Assignment> assignments = FXCollections.observableArrayList();
    private final SimpleBooleanProperty loadError = new SimpleBooleanProperty(false);
    private final AssignmentsGui gui;
    private UserInfo userInfo = UserInfo.getInstance();
    private int loaded = -1;

    public Assignments(AssignmentsGui gui) {
        this.gui = gui;
    }

    public void loadAssignments(boolean reload) {
        if (userInfo.getSelectedClass() == null)
            return;
        if (reload || loaded != userInfo.getSelectedClass().getClassId()) {
            userInfo.startLoading();
            Client.getInstance().processMessage(new AssignmentsGetMsg(userInfo.getSelectedClass().getClassId()), this);
        }
    }

    public ObservableList<Assignment> getAssignments() {
        return assignments;
    }

    public void clear() {
        assignments.clear();
        loaded = -1;
    }

    @Override
    public void response(AssignmentsGetMsg message) {
        Platform.runLater(() -> {
            loadError.set(false);
            for (MsgUtil.AssignmentData data : message.getAssignments())
                assignments.add(new Assignment(data));
            Collections.sort(assignments);
            loaded = message.getClassId();
            userInfo.finishLoading();
        });
    }

    @Override
    public void onError(boolean suggestRetry, AssignmentsGetMsg msg) {
        Platform.runLater(() -> {
            clear();
            loadError.set(true);
            userInfo.finishLoading();
            if (suggestRetry)
                loadAssignments(true);
        });
    }

    public boolean isLoadError() {
        return loadError.get();
    }

    public SimpleBooleanProperty loadErrorProperty() {
        return loadError;
    }

    public static class Assignment implements Comparable<Assignment> {

        private final int aid;
        private final SimpleStringProperty name = new SimpleStringProperty();
        private final SimpleStringProperty status = new SimpleStringProperty();
        private final SimpleObjectProperty<Date> dueDate = new SimpleObjectProperty<>();

        public Assignment(MsgUtil.AssignmentData data) {
            aid = data.id;
            name.set(data.name);
            status.set("Unknown");
            dueDate.set(new Date(NetUtil.UTCToMilli(data.dueDateUTC)));
        }

        public int getAid() {
            return aid;
        }

        public SimpleObjectProperty<Date> dueDateProperty() {
            return dueDate;
        }

        public Date getDueDate() {
            return dueDate.get();
        }

        public SimpleStringProperty nameProperty() {
            return name;
        }

        public String getName() {
            return name.get();
        }

        public SimpleStringProperty statusProperty() {
            return status;
        }

        public String getStatus() {
            return status.get();
        }

        @Override
        public int compareTo(Assignment o) {
            if (o == null)
                return 1;
            if (dueDate.get() == null)
                return o.dueDate.get() == null ? 0 : -1;
            return dueDate.get().compareTo(o.dueDate.get());
        }

    }

}
