package edu.rpi.aris.assign.client.model;

import edu.rpi.aris.assign.*;
import edu.rpi.aris.assign.client.AssignClient;
import edu.rpi.aris.assign.client.Client;
import edu.rpi.aris.assign.client.ResponseHandler;
import edu.rpi.aris.assign.client.controller.AssignGui;
import edu.rpi.aris.assign.client.controller.SingleAssignmentGui;
import edu.rpi.aris.assign.message.AssignmentGetInstructorMsg;
import edu.rpi.aris.assign.message.MsgUtil;
import edu.rpi.aris.assign.message.ProblemFetchMsg;
import edu.rpi.aris.assign.message.SubmissionFetchMsg;
import edu.rpi.aris.assign.spi.ArisModule;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TreeItem;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class InstructorAssignment implements ResponseHandler<AssignmentGetInstructorMsg> {

    private static final CurrentUser userInfo = CurrentUser.getInstance();
    private final int cid;
    private final int aid;
    private final SimpleStringProperty name = new SimpleStringProperty();
    private final SimpleStringProperty dueDate = new SimpleStringProperty();
    private final SimpleBooleanProperty loadErrorProperty = new SimpleBooleanProperty(false);
    private final ObservableList<TreeItem<Submission>> students = FXCollections.observableArrayList();
    private final SingleAssignmentGui gui;
    private final ReentrantLock lock = new ReentrantLock(true);
    private boolean loaded = false;

    public InstructorAssignment(SingleAssignmentGui gui, String name, int cid, int aid) {
        this.gui = gui;
        this.name.set(name);
        this.cid = cid;
        this.aid = aid;
    }

    public synchronized void loadAssignment(boolean reload) {
        if (reload || !loaded) {
            userInfo.startLoading();
            clear();
            Client.getInstance().processMessage(new AssignmentGetInstructorMsg(cid, aid), this);
        }
    }

    public synchronized void clear() {
        students.clear();
    }

    private void updateStudentStatus(Student student) {
        int total = student.problems.size();
        int complete = 0;
        ZonedDateTime submittedOn = null;
        boolean grading = false;
        for (TreeItem<Submission> item : student.problems) {
            if (item.getValue().status.get() == GradingStatus.CORRECT)
                complete++;
            if (item.getValue().status.get() == GradingStatus.GRADING)
                grading = true;
            if (submittedOn == null || (item.getValue().submittedOn.get() != null && submittedOn.compareTo(item.getValue().submittedOn.get()) > 0))
                submittedOn = item.getValue().submittedOn.get();
        }
        ((Submission) student).status.set(complete >= total ? GradingStatus.CORRECT : complete > 1 ? GradingStatus.PARTIAL : GradingStatus.INCORRECT);
        ((Submission) student).statusStr.set(complete + "/" + total + (grading ? " (Grading)" : ""));
        ((Submission) student).submittedOn.set(submittedOn);
    }

    private void updateProblemStatus(AssignedProblem problem) {
        GradingStatus status = GradingStatus.NONE;
        ZonedDateTime submittedOn = null;
        Submission parent = problem;
        for (TreeItem<Submission> item : problem.submissions) {
            Submission sub = item.getValue();
            if (sub.status.get() == GradingStatus.CORRECT) {
                parent.status.set(GradingStatus.CORRECT);
                return;
            }
            if (sub.status.get().compareTo(status) < 0)
                status = sub.status.get();
            if (submittedOn == null || sub.submittedOn.get().compareTo(submittedOn) > 0)
                submittedOn = sub.submittedOn.get();
        }
        parent.submittedOn.set(submittedOn);
        parent.status.set(status);
    }

    public SimpleStringProperty nameProperty() {
        return name;
    }

    public String getName() {
        return name.get();
    }

    public SimpleStringProperty dueDateProperty() {
        return dueDate;
    }

    public String getDueDate() {
        return dueDate.get();
    }

    public SimpleBooleanProperty loadErrorProperty() {
        return loadErrorProperty;
    }

    public boolean isLoadError() {
        return loadErrorProperty.get();
    }

    public int getCid() {
        return cid;
    }

    public int getAid() {
        return aid;
    }

    public ObservableList<TreeItem<Submission>> getStudents() {
        return students;
    }

    private <T extends ArisModule> void fetchSubmission(Submission submission, ArisModule<T> module) {
        userInfo.startLoading();
        Client.getInstance().processMessage(new SubmissionFetchMsg<>(cid, aid, submission.pid, submission.sid, submission.uid, submission.moduleName), new SubmissionFetchResponseHandler<>(module, submission));
    }

    private <T extends ArisModule> void fetchProblem(int pid, ArisModule<T> module) {
        userInfo.startLoading();
        Client.getInstance().processMessage(new ProblemFetchMsg<>(pid, module.getModuleName()), new ProblemFetchResponseHandler<>(module));
    }

    @Override
    public void response(AssignmentGetInstructorMsg message) {
        Platform.runLater(() -> {
            name.set(message.getName());
            dueDate.set(AssignGui.DATE_FORMAT.format(new Date(NetUtil.UTCToMilli(message.getDueDate()))));
            message.getUsers().entrySet().stream().filter(e -> e.getValue() != null && e.getValue().getLeft() != null).sorted(Comparator.comparing(o -> o.getValue().getLeft())).forEachOrdered(entry -> {
                Student student = new Student(entry.getKey(), entry.getValue().getLeft() + " (" + entry.getValue().getRight() + ")", "Unknown", null);
                TreeItem<Submission> s = new TreeItem<>(student);
                students.add(s);
                message.getProblems().stream().sorted(Comparator.comparing(p -> p.name)).forEachOrdered(pInfo -> {
                    AssignedProblem problem = new AssignedProblem(pInfo);
                    TreeItem<Submission> p = new TreeItem<>(problem);
                    student.problems.add(p);
                    HashMap<Integer, HashSet<MsgUtil.SubmissionInfo>> probs = message.getSubmissions().get(student.getUid());
                    HashSet<MsgUtil.SubmissionInfo> subs;
                    if (probs != null && (subs = probs.get(pInfo.pid)) != null) {
                        AtomicInteger i = new AtomicInteger(subs.size());
                        subs.stream().sorted((o1, o2) -> o2.submissionTime.compareTo(o1.submissionTime)).forEachOrdered(sInfo -> {
                            Submission submission = new Submission(sInfo, pInfo.moduleName);
                            submission.name.set("Submission " + i.getAndDecrement());
                            TreeItem<Submission> sub = new TreeItem<>(submission);
                            problem.submissions.add(sub);
                        });
                    }
                    updateProblemStatus(problem);
                    p.getChildren().addAll(problem.submissions);
                });
                updateStudentStatus(student);
                s.getChildren().addAll(student.problems);
            });
            loaded = true;
            loadErrorProperty.set(false);
            userInfo.finishLoading();
        });
    }

    @Override
    public void onError(boolean suggestRetry, AssignmentGetInstructorMsg msg) {
        Platform.runLater(() -> {
            clear();
            userInfo.finishLoading();
            loadErrorProperty.set(true);
            if (suggestRetry)
                loadAssignment(true);
        });
    }

    @Override
    public ReentrantLock getLock() {
        return lock;
    }

    private class ProblemFetchResponseHandler<T extends ArisModule> implements ResponseHandler<ProblemFetchMsg<T>> {

        private final ArisModule<T> module;

        ProblemFetchResponseHandler(ArisModule<T> module) {
            this.module = module;
        }

        @Override
        public void response(ProblemFetchMsg<T> message) {
            Platform.runLater(() -> {
                AssignedProblem problemInfo = null;
                TreeItem<Submission> studentTreeItem = students.get(0);
                if (studentTreeItem != null) {
                    Student student = (Student) studentTreeItem.getValue();
                    for (TreeItem<Submission> item : student.problems)
                        if (item.getValue().pid == message.getPid())
                            problemInfo = (AssignedProblem) item.getValue();
                }
                if (problemInfo != null) {
                    Problem<T> problem = message.getProblem();
                    try {
//                        gui.viewProblem(problemInfo.getName(), problem, module);
                    } catch (Exception e) {
                        LibAssign.showExceptionError(e);
                    }
                }
                userInfo.finishLoading();
            });
        }

        @Override
        public void onError(boolean suggestRetry, ProblemFetchMsg msg) {
            if (suggestRetry)
                fetchProblem(msg.getPid(), module);
            Platform.runLater(userInfo::finishLoading);
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

    private class SubmissionFetchResponseHandler<T extends ArisModule> implements ResponseHandler<SubmissionFetchMsg<T>> {

        private final ArisModule<T> module;
        private final Submission submission;

        SubmissionFetchResponseHandler(ArisModule<T> module, Submission submission) {
            this.module = module;
            this.submission = submission;
        }

        @Override
        public void response(SubmissionFetchMsg<T> message) {
            Platform.runLater(() -> {
                AssignedProblem problemInfo = null;
                TreeItem<Submission> studentTreeItem = students.get(0);
                if (studentTreeItem != null) {
                    Student student = (Student) studentTreeItem.getValue();
                    for (TreeItem<Submission> item : student.problems)
                        if (item.getValue().pid == submission.pid)
                            problemInfo = (AssignedProblem) item.getValue();
                }
                Problem<T> problem = message.getProblem();
                try {
//                    gui.viewProblem(submission.getName() + (problemInfo == null ? "" : " for " + problemInfo.getName()), problem, module);
                } catch (Exception e) {
                    LibAssign.showExceptionError(e);
                }
                userInfo.finishLoading();
            });
        }

        @Override
        public void onError(boolean suggestRetry, SubmissionFetchMsg<T> msg) {
            if (suggestRetry)
                fetchSubmission(submission, module);
            Platform.runLater(userInfo::finishLoading);
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

    public class Submission implements Comparable<Submission> {

        private final int pid, sid, uid;
        private final SimpleStringProperty name;
        private final SimpleObjectProperty<ZonedDateTime> submittedOn;
        private final SimpleStringProperty submittedOnStr = new SimpleStringProperty();
        private final SimpleStringProperty statusStr;
        private final SimpleObjectProperty<Node> controlNode;
        private final SimpleObjectProperty<GradingStatus> status;
        private final String moduleName;

        public Submission(int pid, int uid, int sid, String name, ZonedDateTime submittedOn, GradingStatus status, String statusStr, String moduleName) {
            this.pid = pid;
            this.sid = sid;
            this.uid = uid;
            this.name = new SimpleStringProperty(name);
            this.submittedOn = new SimpleObjectProperty<>(submittedOn);
            this.status = new SimpleObjectProperty<>(status);
            this.moduleName = moduleName;
            this.submittedOnStr.bind(Bindings.createStringBinding(() -> this.submittedOn.get() == null ? "Never" : AssignGui.DATE_FORMAT.format(new Date(NetUtil.UTCToMilli(this.submittedOn.get()))), this.submittedOn));
            this.statusStr = new SimpleStringProperty(statusStr);
            Button view = new Button("View");
            view.setOnAction(e -> viewSubmission());
            controlNode = new SimpleObjectProperty<>(view);
        }

        public Submission(MsgUtil.SubmissionInfo info, String moduleName) {
            this(info.pid, info.uid, info.sid, null, info.submissionTime, info.status, info.statusStr, moduleName);
        }

        public <T extends ArisModule> void viewSubmission() {
            ArisModule<T> module = ModuleService.getService().getModule(moduleName);
            if (module == null) {
                AssignClient.displayErrorMsg("Missing Module", "Client is missing \"" + moduleName + "\" module");
                return;
            }
            fetchSubmission(this, module);
        }

        @Override
        public int compareTo(Submission o) {
            return 0;
        }

        public String getName() {
            return name.get();
        }

        public SimpleStringProperty nameProperty() {
            return name;
        }

        public int getUid() {
            return uid;
        }

        public SimpleStringProperty submittedOnProperty() {
            return submittedOnStr;
        }

        public SimpleStringProperty statusStrProperty() {
            return statusStr;
        }

        public SimpleObjectProperty<Node> controlNodeProperty() {
            return controlNode;
        }

    }

    public class AssignedProblem extends Submission implements Comparable<Submission> {

        private final ObservableList<TreeItem<Submission>> submissions = FXCollections.observableArrayList();

        public AssignedProblem(int pid, String name, String statusStr, String moduleName) {
            super(pid, -1, -1, name, null, GradingStatus.NONE, statusStr, moduleName);
            Button view = new Button("View Problem");
            view.setOnAction(e -> viewProblem());
            super.controlNode.set(view);
            super.statusStr.bind(Bindings.createStringBinding(() -> {
                switch (super.status.get()) {
                    case GRADING:
                        return "Grading problem";
                    case CORRECT:
                        return "Correct";
                    case INCORRECT:
                        return "Incorrect";
                    case NONE:
                        return "No Submissions";
                }
                return null;
            }, super.status));
        }

        AssignedProblem(MsgUtil.ProblemInfo info) {
            this(info.pid, info.name, null, info.moduleName);
        }

        private <T extends ArisModule> void viewProblem() {
            ArisModule<T> module = ModuleService.getService().getModule(super.moduleName);
            if (module == null) {
                AssignClient.displayErrorMsg("Missing Module", "Client is missing \"" + super.moduleName + "\" module");
                return;
            }
            fetchProblem(super.pid, module);
        }

        @Override
        public int compareTo(Submission o) {
            if (o instanceof AssignedProblem)
                return getName().compareTo(o.getName());
            return super.compareTo(o);
        }

    }

    public class Student extends Submission implements Comparable<Submission> {

        private final ObservableList<TreeItem<Submission>> problems = FXCollections.observableArrayList();

        public Student(int uid, String name, String statusStr, String moduleName) {
            super(-1, uid, -1, name, null, GradingStatus.NONE, statusStr, moduleName);
            super.controlNode.set(null);
        }

        @Override
        public int compareTo(Submission o) {
            if (o instanceof Student)
                return getName().compareTo(o.getName());
            return super.compareTo(o);
        }
    }

}
