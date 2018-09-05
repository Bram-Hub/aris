package edu.rpi.aris.assign.client.model;

import edu.rpi.aris.assign.NetUtil;
import edu.rpi.aris.assign.client.AssignClient;
import edu.rpi.aris.assign.client.Client;
import edu.rpi.aris.assign.client.ResponseHandler;
import edu.rpi.aris.assign.client.controller.AssignGui;
import edu.rpi.aris.assign.client.controller.AssignmentsGui;
import edu.rpi.aris.assign.message.AssignmentCreateMsg;
import edu.rpi.aris.assign.message.AssignmentEditMsg;
import edu.rpi.aris.assign.message.AssignmentsGetMsg;
import edu.rpi.aris.assign.message.MsgUtil;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;

public class Assignments implements ResponseHandler<AssignmentsGetMsg> {

    private final AssignmentCreateResponseHandler createHandler = new AssignmentCreateResponseHandler();
    private final AssignmentEditResponseHandler renamedHandler = new AssignmentEditResponseHandler();
    private final ObservableList<Assignment> assignments = FXCollections.observableArrayList();
    private final SimpleBooleanProperty loadError = new SimpleBooleanProperty(false);
    private final AssignmentsGui gui;
    private final ReentrantLock lock = new ReentrantLock(true);
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
            clear();
            for (MsgUtil.AssignmentData data : message.getAssignments())
                assignments.add(new Assignment(message.getClassId(), data));
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

    @Override
    public ReentrantLock getLock() {
        return lock;
    }

    public boolean isLoadError() {
        return loadError.get();
    }

    public SimpleBooleanProperty loadErrorProperty() {
        return loadError;
    }

    public void createAssignment(int cid, String name, ZonedDateTime date, Collection<Integer> pids) {
        userInfo.startLoading();
        AssignmentCreateMsg msg = new AssignmentCreateMsg(cid, name, date);
        msg.addProofs(pids);
        Client.getInstance().processMessage(msg, createHandler);
    }

    public void renamed(Assignment assignment) {
        userInfo.startLoading();
        AssignmentEditMsg msg = new AssignmentEditMsg(assignment.getCid(), assignment.getAid());
        msg.setName(assignment.getName());
        Client.getInstance().processMessage(msg, renamedHandler);
    }

    public static class Assignment implements Comparable<Assignment> {

        private final int aid;
        private final int cid;
        private final SimpleStringProperty name = new SimpleStringProperty();
        private final SimpleStringProperty status = new SimpleStringProperty();
        private final SimpleObjectProperty<Date> dueDate = new SimpleObjectProperty<>();
        private final SimpleStringProperty dueDateStr = new SimpleStringProperty();

        public Assignment(int cid, int aid, String name, String status, Date dueDate) {
            this.cid = cid;
            this.aid = aid;
            this.name.set(name);
            this.status.set(status);
            this.dueDate.set(dueDate);
            dueDateStr.bind(Bindings.createStringBinding(() -> this.dueDate.get() == null ? null : AssignGui.DATE_FORMAT.format(this.dueDate.get()), this.dueDate));
        }

        public Assignment(int cid, MsgUtil.AssignmentData data) {
            this(cid, data.id, data.name, "Unknown", new Date(NetUtil.UTCToMilli(data.dueDateUTC)));
        }

        public int getAid() {
            return aid;
        }

        public SimpleStringProperty dueDateProperty() {
            return dueDateStr;
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

        public int getCid() {
            return cid;
        }
    }

    private class AssignmentCreateResponseHandler implements ResponseHandler<AssignmentCreateMsg> {

        @Override
        public void response(AssignmentCreateMsg message) {
            Platform.runLater(() -> {
                if (userInfo.getSelectedClass().getClassId() == message.getCid()) {
                    Assignment assignment = new Assignment(message.getCid(), message.getAid(), message.getName(), "Unknown", new Date(NetUtil.UTCToMilli(message.getDueDate())));
                    assignments.add(assignment);
                }
                userInfo.finishLoading();
            });
        }

        @Override
        public void onError(boolean suggestRetry, AssignmentCreateMsg msg) {
            if (suggestRetry)
                createAssignment(msg.getCid(), msg.getName(), msg.getDueDate(), msg.getProblems());
            else
                AssignClient.getInstance().getMainWindow().displayErrorMsg("Error", "An error occurred creating the assignment");
            Platform.runLater(() -> userInfo.finishLoading());
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

    private class AssignmentEditResponseHandler implements ResponseHandler<AssignmentEditMsg> {

        @Override
        public void response(AssignmentEditMsg message) {
            Platform.runLater(() -> userInfo.finishLoading());
        }

        @Override
        public void onError(boolean suggestRetry, AssignmentEditMsg msg) {
            if (suggestRetry) {
                for (Assignment a : assignments) {
                    if (a.getCid() == msg.getCid() && a.getAid() == msg.getAid()) {
                        renamed(a);
                        break;
                    }
                }
            } else {
                AssignClient.getInstance().getMainWindow().displayErrorMsg("Error", "An error occurred while renaming the problem");
                loadAssignments(true);
            }
            Platform.runLater(() -> userInfo.finishLoading());
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

}
