package edu.rpi.aris.assign.client.model;

import edu.rpi.aris.assign.NetUtil;
import edu.rpi.aris.assign.Perm;
import edu.rpi.aris.assign.ServerPermissions;
import edu.rpi.aris.assign.client.Client;
import edu.rpi.aris.assign.client.ResponseHandler;
import edu.rpi.aris.assign.client.controller.AssignGui;
import edu.rpi.aris.assign.client.controller.AssignmentsGui;
import edu.rpi.aris.assign.message.*;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class Assignments implements ResponseHandler<AssignmentsGetMsg> {

    private static final Logger log = LogManager.getLogger();

    private final AssignmentCreateResponseHandler createHandler = new AssignmentCreateResponseHandler();
    private final AssignmentEditResponseHandler editHandler = new AssignmentEditResponseHandler();
    private final AssignmentDeleteResponseHandler deleteHandler = new AssignmentDeleteResponseHandler();
    private final ObservableList<Assignment> assignments = FXCollections.observableArrayList();
    private final SimpleBooleanProperty loadError = new SimpleBooleanProperty(false);
    private final AssignmentsGui gui;
    private final ReentrantLock lock = new ReentrantLock(true);
    private CurrentUser userInfo = CurrentUser.getInstance();
    private int loaded = -1;

    public Assignments(AssignmentsGui gui) {
        this.gui = gui;
    }

    public synchronized void loadAssignments(boolean reload) {
        if (!userInfo.isLoggedIn()) {
            loadOffline();
            return;
        }
        if (userInfo.getSelectedClass() == null) {
            return;
        }
        if (reload || loaded != userInfo.getSelectedClass().getClassId()) {
            Client.getInstance().processMessage(new AssignmentsGetMsg(userInfo.getSelectedClass().getClassId()), this);
        }
    }

    private void loadOffline() {
        clear();
        OfflineDB.submit(con -> {
            try (PreparedStatement selectAssign = con.prepareStatement("SELECT cid, aid, name, due_date FROM assignments GROUP BY cid, aid;");
                 PreparedStatement selectProbs = con.prepareStatement("SELECT pid FROM assignments WHERE cid = ? AND aid = ?;");
                 PreparedStatement selectClass = con.prepareStatement("SELECT name FROM classes WHERE cid = ?;");
                 ResultSet rs = selectAssign.executeQuery()) {
                while (rs.next()) {
                    int cid = rs.getInt(1);
                    int aid = rs.getInt(2);
                    HashSet<Integer> problems = new HashSet<>();
                    selectProbs.setInt(1, cid);
                    selectProbs.setInt(2, aid);
                    try (ResultSet rs2 = selectProbs.executeQuery()) {
                        while (rs2.next())
                            problems.add(rs2.getInt(1));
                    }
                    ZonedDateTime zdt = NetUtil.timestampToZDT(rs.getTimestamp(4));
                    selectClass.setInt(1, cid);
                    String className = "Unknown";
                    try (ResultSet crs = selectClass.executeQuery()) {
                        if (crs.next())
                            className = crs.getString(1);
                    }
                    assignments.add(new Assignment(cid, aid, rs.getString(3), "Unknown", new Date(NetUtil.UTCToMilli(zdt)), className, problems));
                }
                Collections.sort(assignments);
                loaded = -1;
            } catch (Exception e) {
                log.error("An error occurred loading the assignments from the offline database", e);
            }
        });
    }

    public ObservableList<Assignment> getAssignments() {
        return assignments;
    }

    public synchronized void clear() {
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
        });
    }

    @Override
    public void onError(boolean suggestRetry, AssignmentsGetMsg msg) {
        Platform.runLater(() -> {
            clear();
            loadError.set(true);
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
        AssignmentCreateMsg msg = new AssignmentCreateMsg(cid, name, date);
        msg.addProofs(pids);
        Client.getInstance().processMessage(msg, createHandler);
    }

    public void delete(int cid, int aid) {
        Client.getInstance().processMessage(new AssignmentDeleteMsg(cid, aid), deleteHandler);
    }

    public void modifyAssignment(Assignment assignment, String name, ZonedDateTime due, Set<Integer> problems) {
        AssignmentEditMsg editMsg = new AssignmentEditMsg(assignment.getCid(), assignment.getAid());
        if (!name.equals(assignment.getName()))
            editMsg.setName(name);
        if (!assignment.getDueDate().equals(new Date(NetUtil.UTCToMilli(due))))
            editMsg.setNewDueDate(due);
        problems.stream().filter(p -> !assignment.getProblems().contains(p)).forEach(editMsg::addProblem);
        assignment.getProblems().stream().filter(p -> !problems.contains(p)).forEach(editMsg::removeProblem);
        Client.getInstance().processMessage(editMsg, editHandler);
    }

    public class Assignment implements Comparable<Assignment> {

        private final int aid;
        private final int cid;
        private final SimpleStringProperty className = new SimpleStringProperty();
        private final SimpleStringProperty name = new SimpleStringProperty();
        private final SimpleStringProperty status = new SimpleStringProperty();
        private final SimpleObjectProperty<Date> dueDate = new SimpleObjectProperty<>();
        private final SimpleStringProperty dueDateStr = new SimpleStringProperty();
        private final SimpleObjectProperty<Node> modifyColumn = new SimpleObjectProperty<>();
        private final HashSet<Integer> problems = new HashSet<>();

        public Assignment(int cid, int aid, String name, String status, Date dueDate, String className, Collection<Integer> problems) {
            this.cid = cid;
            this.aid = aid;
            this.name.set(name);
            this.status.set(status);
            this.dueDate.set(dueDate);
            this.className.set(className);
            this.problems.addAll(problems);
            dueDateStr.bind(Bindings.createStringBinding(() -> this.dueDate.get() == null ? null : AssignGui.DATE_FORMAT.format(this.dueDate.get()), this.dueDate));
            HBox box = new HBox(5);
            Button modify = new Button("Modify");
            modify.setOnAction(event -> modify());
            Button delete = new Button("Delete");
            delete.setOnAction(event -> delete());
            boolean deleteVisible = false;
            for (ClassInfo info : userInfo.classesProperty()) {
                if (info.getClassId() == cid) {
                    ServerPermissions permissions = ServerConfig.getPermissions();
                    deleteVisible = permissions != null && permissions.hasPermission(info.getUserRole(), Perm.ASSIGNMENT_DELETE);
                    break;
                }
            }
            delete.setVisible(deleteVisible);
            delete.setManaged(deleteVisible);
            box.getChildren().addAll(modify, delete);
            box.setAlignment(Pos.CENTER);
            modifyColumn.set(box);
        }

        public Assignment(int cid, MsgUtil.AssignmentData data) {
            this(cid, data.id, data.name, "Unknown", new Date(NetUtil.UTCToMilli(data.dueDateUTC)), null, data.problems);
        }

        private void modify() {
            gui.modifyAssignment(this);
        }

        private void delete() {
            if (gui.confirmDelete(this))
                Assignments.this.delete(cid, aid);
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

        public Node getModifyColumn() {
            return modifyColumn.get();
        }

        public SimpleObjectProperty<Node> modifyColumnProperty() {
            return modifyColumn;
        }

        public HashSet<Integer> getProblems() {
            return problems;
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

        public SimpleStringProperty classNameProperty() {
            return className;
        }
    }

    private class AssignmentCreateResponseHandler implements ResponseHandler<AssignmentCreateMsg> {

        @Override
        public void response(AssignmentCreateMsg message) {
            Platform.runLater(() -> {
                if (userInfo.getSelectedClass().getClassId() == message.getClassId()) {
                    Assignment assignment = new Assignment(message.getClassId(), message.getAid(), message.getName(), "Unknown", new Date(NetUtil.UTCToMilli(message.getDueDate())), null, message.getProblems());
                    assignments.add(assignment);
                }
            });
        }

        @Override
        public void onError(boolean suggestRetry, AssignmentCreateMsg msg) {
            if (suggestRetry)
                createAssignment(msg.getClassId(), msg.getName(), msg.getDueDate(), msg.getProblems());
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

    private class AssignmentEditResponseHandler implements ResponseHandler<AssignmentEditMsg> {

        @Override
        public void response(AssignmentEditMsg message) {
            Platform.runLater(() -> {
                for (Assignment a : assignments) {
                    if (a.getCid() == message.getClassId() && a.getAid() == message.getAid()) {
                        if (message.getNewName() != null)
                            a.nameProperty().set(message.getNewName());
                        if (message.getNewDueDate() != null)
                            a.dueDate.set(new Date(NetUtil.UTCToMilli(message.getNewDueDate())));
                        a.getProblems().removeAll(message.getRemovedProblems());
                        a.getProblems().addAll(message.getAddedProblems());
                        break;
                    }
                }
            });
        }

        @Override
        public void onError(boolean suggestRetry, AssignmentEditMsg msg) {
            if (suggestRetry) {
                Client.getInstance().processMessage(msg, this);
            } else {
                loadAssignments(true);
            }
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

    private class AssignmentDeleteResponseHandler implements ResponseHandler<AssignmentDeleteMsg> {

        @Override
        public void response(AssignmentDeleteMsg message) {
            Platform.runLater(() -> assignments.removeIf(assignment -> assignment.getCid() == message.getClassId() && assignment.getAid() == message.getAid()));
        }

        @Override
        public void onError(boolean suggestRetry, AssignmentDeleteMsg msg) {
            if (suggestRetry)
                delete(msg.getClassId(), msg.getAid());
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

}
