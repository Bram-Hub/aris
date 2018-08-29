package edu.rpi.aris.assign.client.model;

import edu.rpi.aris.assign.NetUtil;
import edu.rpi.aris.assign.client.Client;
import edu.rpi.aris.assign.client.ResponseHandler;
import edu.rpi.aris.assign.message.AssignmentsGetMsg;
import edu.rpi.aris.assign.message.MsgUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Date;

public class Assignments implements ResponseHandler<AssignmentsGetMsg> {

    private final ObservableList<Assignment> assignments = FXCollections.observableArrayList();
    private final SimpleBooleanProperty loadError = new SimpleBooleanProperty(false);
    private UserInfo userInfo = UserInfo.getInstance();
    private int loaded = -1;

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

        public final String name, status;
        public final Date dueDate;

        public Assignment(MsgUtil.AssignmentData data) {
            name = data.name;
            status = "Unknown";
            dueDate = new Date(NetUtil.UTCToMilli(data.dueDateUTC));
        }

        @Override
        public int compareTo(Assignment o) {
            return dueDate.compareTo(o.dueDate);
        }
    }

}
