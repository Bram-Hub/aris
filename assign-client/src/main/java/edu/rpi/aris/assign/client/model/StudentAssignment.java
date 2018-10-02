package edu.rpi.aris.assign.client.model;

import edu.rpi.aris.assign.GradingStatus;
import edu.rpi.aris.assign.NetUtil;
import edu.rpi.aris.assign.client.Client;
import edu.rpi.aris.assign.client.ResponseHandler;
import edu.rpi.aris.assign.client.controller.AssignGui;
import edu.rpi.aris.assign.message.AssignmentGetStudentMsg;
import edu.rpi.aris.assign.message.MsgUtil;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.TreeItem;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class StudentAssignment implements ResponseHandler<AssignmentGetStudentMsg> {

    private static final UserInfo userInfo = UserInfo.getInstance();
    private final int cid;
    private final int aid;
    private final SimpleStringProperty name = new SimpleStringProperty();
    private final SimpleStringProperty dueDate = new SimpleStringProperty();
    private final SimpleObjectProperty<GradingStatus> status = new SimpleObjectProperty<>();
    private final ObservableList<TreeItem<Submission>> problems = FXCollections.observableArrayList();
    private final HashMap<Integer, ObservableList<TreeItem<Submission>>> submissions = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock(true);
    private boolean loaded = false;

    public StudentAssignment(String name, int cid, int aid) {
        this.name.set(name);
        this.cid = cid;
        this.aid = aid;
    }

    public synchronized void loadAssignment(boolean reload) {
        if (reload || !loaded) {
            userInfo.startLoading();
            clear();
            Client.getInstance().processMessage(new AssignmentGetStudentMsg(cid, aid), this);
        }
    }

    public synchronized void clear() {

    }

    public SimpleStringProperty nameProperty() {
        return name;
    }

    public String getName() {
        return name.get();
    }

    public ObservableList<TreeItem<Submission>> getProblems() {
        return problems;
    }

    public HashMap<Integer, ObservableList<TreeItem<Submission>>> getSubmissions() {
        return submissions;
    }

    @Override
    public void response(AssignmentGetStudentMsg message) {
        Platform.runLater(() -> {
            name.set(message.getName());
            dueDate.set(AssignGui.DATE_FORMAT.format(new Date(NetUtil.UTCToMilli(message.getDueDate()))));
            for (MsgUtil.ProblemInfo problemInfo : message.getProblems()) {
                AssignedProblem assignedProblem = new AssignedProblem(problemInfo);
                TreeItem<Submission> p = new TreeItem<>(assignedProblem);
                ObservableList<TreeItem<Submission>> subs = submissions.computeIfAbsent(assignedProblem.getPid(), pid -> FXCollections.observableArrayList());
                subs.addListener((ListChangeListener<TreeItem<Submission>>) c -> {
                    if (c.wasRemoved())
                        p.getChildren().removeAll(c.getRemoved());
                    if (c.wasAdded())
                        p.getChildren().addAll(c.getAddedSubList());
                    p.getChildren().sorted(Comparator.comparing(TreeItem::getValue));
                    AtomicInteger i = new AtomicInteger(1);
                    p.getChildren().forEach(item -> item.getValue().name.set("Submission " + (i.getAndIncrement())));
                    updateProblemStatus(assignedProblem, p.getChildren());
                });
                for (MsgUtil.SubmissionInfo submissionInfo : message.getSubmissions().get(problemInfo.pid)) {
                    Submission submission = new Submission(submissionInfo);
                    TreeItem<Submission> sub = new TreeItem<>(submission);
                    subs.add(sub);
                }
                problems.add(p);
            }
            loaded = true;
            userInfo.finishLoading();
        });
    }

    @Override
    public void onError(boolean suggestRetry, AssignmentGetStudentMsg msg) {
        Platform.runLater(() -> {
            clear();
            userInfo.finishLoading();
            if (suggestRetry)
                loadAssignment(true);
        });
    }

    @Override
    public ReentrantLock getLock() {
        return lock;
    }

    private void updateProblemStatus(Submission parent, Collection<TreeItem<Submission>> children) {
        GradingStatus status = GradingStatus.NONE;
        for (TreeItem<Submission> item : children) {
            Submission sub = item.getValue();
            if (sub.status == GradingStatus.CORRECT) {
                parent.status = GradingStatus.CORRECT;
                return;
            }
            if (sub.status.compareTo(status) < 0)
                status = sub.status;
        }
        parent.status = status;
        updateAssignmentStatus();
    }

    private void updateAssignmentStatus() {
        boolean correct = true;
        boolean warn = false;
        for (TreeItem<Submission> item : problems) {
            Submission prob = item.getValue();
            correct &= prob.status != GradingStatus.CORRECT && prob.status != GradingStatus.CORRECT_WARN;
            warn |= prob.status == GradingStatus.CORRECT_WARN || prob.status == GradingStatus.INCORRECT_WARN;
        }
        status.set(correct ? (warn ? GradingStatus.CORRECT_WARN : GradingStatus.CORRECT) : (warn ? GradingStatus.INCORRECT_WARN : GradingStatus.INCORRECT));
    }

    public static class Submission implements Comparable<Submission> {

        private final int pid;
        private final int sid;
        private final SimpleStringProperty name;
        private final SimpleObjectProperty<ZonedDateTime> submittedOn;
        private final SimpleStringProperty submittedOnStr = new SimpleStringProperty();
        private final SimpleStringProperty statusStr;
        private final SimpleObjectProperty<Button> button;
        private GradingStatus status;

        public Submission(int pid, int sid, String name, ZonedDateTime submittedOn, GradingStatus status, String statusStr) {
            this.pid = pid;
            this.sid = sid;
            this.name = new SimpleStringProperty(name);
            this.submittedOn = new SimpleObjectProperty<>(submittedOn);
            this.status = status;
            this.submittedOnStr.bind(Bindings.createStringBinding(() -> AssignGui.DATE_FORMAT.format(new Date(NetUtil.UTCToMilli(submittedOn))), this.submittedOn));
            this.statusStr = new SimpleStringProperty(statusStr);
            Button btn = new Button("View");
            btn.setOnAction(this::buttonPushed);
            button = new SimpleObjectProperty<>(btn);
        }

        public Submission(MsgUtil.SubmissionInfo info) {
            this(info.pid, info.sid, null, info.submissionTime, info.status, info.statusStr);
        }

        protected void buttonPushed(ActionEvent actionEvent) {

        }

        public SimpleStringProperty submittedOnProperty() {
            return submittedOnStr;
        }

        public String getSubmittedOn() {
            return submittedOnStr.get();
        }

        public int getPid() {
            return pid;
        }

        public int getSid() {
            return sid;
        }

        public String getName() {
            return name.get();
        }

        public SimpleStringProperty nameProperty() {
            return name;
        }

        public String getStatusStr() {
            return statusStr.get();
        }

        public SimpleStringProperty statusStrProperty() {
            return statusStr;
        }

        public Button getButton() {
            return button.get();
        }

        public SimpleObjectProperty<Button> buttonProperty() {
            return button;
        }

        @Override
        public int compareTo(Submission o) {
            return 0;
        }

        public GradingStatus getStatus() {
            return status;
        }
    }


    public static class AssignedProblem extends Submission {

        public AssignedProblem(int pid, String name, String status) {
            super(pid, 0, name, null, GradingStatus.NONE, status);
            getButton().setText("Create Submission");
        }

        public AssignedProblem(MsgUtil.ProblemInfo info) {
            this(info.pid, info.name, null);
        }

        @Override
        protected void buttonPushed(ActionEvent actionEvent) {

        }
    }

}
