package edu.rpi.aris.assign.client.model;

import edu.rpi.aris.assign.*;
import edu.rpi.aris.assign.client.AssignClient;
import edu.rpi.aris.assign.client.Client;
import edu.rpi.aris.assign.client.ResponseHandler;
import edu.rpi.aris.assign.client.controller.AssignGui;
import edu.rpi.aris.assign.client.controller.StudentAssignmentGui;
import edu.rpi.aris.assign.message.*;
import edu.rpi.aris.assign.spi.ArisModule;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.HBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.encoders.Hex;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class StudentAssignment implements ResponseHandler<AssignmentGetStudentMsg> {

    private static final Logger log = LogManager.getLogger();
    private static final UserInfo userInfo = UserInfo.getInstance();
    private static final File submissionStorageDir = new File(LocalConfig.CLIENT_STORAGE_DIR, "submissions");
    private final int cid;
    private final int aid;
    private final SimpleStringProperty name = new SimpleStringProperty();
    private final SimpleStringProperty dueDate = new SimpleStringProperty();
    private final SimpleObjectProperty<GradingStatus> status = new SimpleObjectProperty<>();
    private final SimpleStringProperty statusStr = new SimpleStringProperty();
    private final ObservableList<TreeItem<Submission>> problems = FXCollections.observableArrayList();
    private final SimpleBooleanProperty loadErrorProperty = new SimpleBooleanProperty(false);
    private final ReentrantLock lock = new ReentrantLock(true);
    private final StudentAssignmentGui gui;
    private boolean loaded = false;

    public StudentAssignment(StudentAssignmentGui gui, String name, int cid, int aid) {
        this.gui = gui;
        this.name.set(name);
        this.cid = cid;
        this.aid = aid;
        statusStr.bind(Bindings.createStringBinding(() -> {
            GradingStatus status = this.status.get();
            if (status == null)
                return "Unknown";
            switch (status) {
                case CORRECT:
                    return "Complete";
                case INCORRECT:
                    return "Incomplete";
                case GRADING:
                    return "Your submission is being graded";
                case NONE:
                    return "You have not made any submissions";
                default:
                    return "Unknown";
            }
        }, status));
    }

    public synchronized void loadAssignment(boolean reload) {
        if (reload || !loaded) {
            userInfo.startLoading();
            clear();
            Client.getInstance().processMessage(new AssignmentGetStudentMsg(cid, aid), this);
        }
    }

    public <T extends ArisModule> void fetchAndCreate(int pid, ArisModule<T> module) {
        userInfo.startLoading();
        Client.getInstance().processMessage(new ProblemFetchMsg<>(pid, module.getModuleName()), new ProblemFetchResponseHandler<>(module));
    }

    public synchronized void clear() {
        problems.clear();
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

    public SimpleStringProperty statusProperty() {
        return statusStr;
    }

    public String getStatusStr() {
        return statusStr.get();
    }

    public GradingStatus getStatus() {
        return status.get();
    }

    public SimpleBooleanProperty loadErrorProperty() {
        return loadErrorProperty;
    }

    public boolean isLoadError() {
        return loadErrorProperty.get();
    }

    public ObservableList<TreeItem<Submission>> getProblems() {
        return problems;
    }

    public int getAid() {
        return aid;
    }

    public int getCid() {
        return cid;
    }

    @Override
    public void response(AssignmentGetStudentMsg message) {
        Platform.runLater(() -> {
            name.set(message.getName());
            dueDate.set(AssignGui.DATE_FORMAT.format(new Date(NetUtil.UTCToMilli(message.getDueDate()))));
            ArrayList<MsgUtil.ProblemInfo> problemInfos = new ArrayList<>(message.getProblems());
            problemInfos.sort(Comparator.comparing(o -> o.name));
            for (MsgUtil.ProblemInfo problemInfo : problemInfos) {
                AssignedProblem assignedProblem = new AssignedProblem(problemInfo);
                TreeItem<Submission> p = new TreeItem<>(assignedProblem);
                ObservableList<TreeItem<Submission>> subs = assignedProblem.submissions;
                subs.addListener((ListChangeListener<TreeItem<Submission>>) c -> {
                    while (c.next()) {
                        if (c.wasRemoved())
                            p.getChildren().removeAll(c.getRemoved());
                        if (c.wasAdded())
                            p.getChildren().addAll(c.getFrom(), c.getAddedSubList());
                        p.getChildren().sorted(Comparator.comparing(TreeItem::getValue));
                        AtomicInteger i = new AtomicInteger(c.getList().size());
                        p.getChildren().forEach(item -> item.getValue().name.set("Submission " + (i.getAndDecrement())));
                        updateProblemStatus(assignedProblem, p.getChildren());
                    }
                });
                HashSet<MsgUtil.SubmissionInfo> tmp = message.getSubmissions().get(problemInfo.pid);
                if (tmp != null) {
                    ArrayList<MsgUtil.SubmissionInfo> submissionInfos = new ArrayList<>(tmp);
                    submissionInfos.sort((o1, o2) -> o2.submissionTime.compareTo(o1.submissionTime));
                    for (MsgUtil.SubmissionInfo submissionInfo : submissionInfos) {
                        Submission submission = new Submission(submissionInfo, assignedProblem.getModuleName());
                        TreeItem<Submission> sub = new TreeItem<>(submission);
                        subs.add(sub);
                    }
                }
                problems.add(p);
                updateProblemStatus(assignedProblem, p.getChildren());
            }
            problems.sort(Comparator.comparing(TreeItem::getValue));
            loaded = true;
            loadErrorProperty.set(false);
            userInfo.finishLoading();
        });
    }

    @Override
    public void onError(boolean suggestRetry, AssignmentGetStudentMsg msg) {
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

    private void updateProblemStatus(Submission parent, Collection<TreeItem<Submission>> children) {
        GradingStatus status = GradingStatus.NONE;
        ZonedDateTime submittedOn = null;
        for (TreeItem<Submission> item : children) {
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
        updateAssignmentStatus();
    }

    private void updateAssignmentStatus() {
        boolean correct = true;
        for (TreeItem<Submission> item : problems) {
            Submission prob = item.getValue();
            correct &= prob.status.get() != GradingStatus.CORRECT && prob.status.get() != GradingStatus.NONE;
        }
        status.set(correct ? GradingStatus.CORRECT : GradingStatus.INCORRECT);
    }

    private String getProblemFileName(int pid) {
        String id = cid + "." + aid + "." + pid;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] data = digest.digest(id.getBytes());
            id = Hex.toHexString(data);
        } catch (NoSuchAlgorithmException e) {
            log.error(e);
        }
        return id;
    }

    public <T extends ArisModule> void saveLocalSubmission(AssignedProblem problemInfo, Problem<T> problem, ArisModule<T> module) {
        if (submissionStorageDir.exists() && !submissionStorageDir.isDirectory()) {
            if (!submissionStorageDir.delete()) {
                AssignClient.getInstance().getMainWindow().displayErrorMsg("Failed to save", "Failed to create submission storage directory");
                return;
            }
        }
        if (!submissionStorageDir.exists()) {
            if (!submissionStorageDir.mkdirs()) {
                AssignClient.getInstance().getMainWindow().displayErrorMsg("Failed to save", "Failed to create submission storage directory");
                return;
            }
        }
        File saveFile = new File(submissionStorageDir, getProblemFileName(problemInfo.getPid()));
        try (FileOutputStream fos = new FileOutputStream(saveFile)) {
            module.getProblemConverter().convertProblem(problem, fos, true);
        } catch (Exception e) {
            AssignClient.getInstance().getMainWindow().displayErrorMsg("Failed to save", "An error occurred while trying to save the file locally");
        }
    }

    public <T extends ArisModule> void uploadSubmission(AssignedProblem problemInfo, Problem<T> problem) {
        userInfo.startLoading();
        Client.getInstance().processMessage(new SubmissionCreateMsg<>(cid, aid, problemInfo.getPid(), problemInfo.getModuleName(), problem), new SubmissionCreateResponseHandler<>(problemInfo));
    }

    public <T extends ArisModule> void fetchSubmission(Submission submission, ArisModule<T> module, boolean readonly) {
        userInfo.startLoading();
        Client.getInstance().processMessage(new SubmissionFetchMsg<>(cid, aid, submission.getPid(), submission.getSid(), submission.getModuleName()), new SubmissionFetchResponseHandler<>(module, readonly, submission));
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
                for (TreeItem<Submission> item : problems)
                    if (item.getValue() instanceof AssignedProblem && item.getValue().getPid() == message.getPid()) {
                        problemInfo = (AssignedProblem) item.getValue();
                        break;
                    }
                if (problemInfo != null) {
                    Problem<T> problem = message.getProblem();
                    try {
                        gui.createSubmission(problemInfo, problem, module);
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
                fetchAndCreate(msg.getPid(), module);
            Platform.runLater(userInfo::finishLoading);
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

    private class SubmissionFetchResponseHandler<T extends ArisModule> implements ResponseHandler<SubmissionFetchMsg<T>> {

        private final ArisModule<T> module;
        private final boolean readonly;
        private final Submission submission;

        SubmissionFetchResponseHandler(ArisModule<T> module, boolean readonly, Submission submission) {
            this.module = module;
            this.readonly = readonly;
            this.submission = submission;
        }

        @Override
        public void response(SubmissionFetchMsg<T> message) {
            Platform.runLater(() -> {
                AssignedProblem problemInfo = null;
                for (TreeItem<Submission> item : problems)
                    if (item.getValue() instanceof AssignedProblem && item.getValue().getPid() == submission.getPid()) {
                        problemInfo = (AssignedProblem) item.getValue();
                        break;
                    }
                Problem<T> problem = message.getProblem();
                try {
                    if (readonly || problemInfo == null)
                        gui.viewSubmission(submission, problemInfo == null ? null : problemInfo.getName(), problem, module);
                    else
                        gui.createSubmission(problemInfo, problem, module);
                } catch (Exception e) {
                    LibAssign.showExceptionError(e);
                }
                userInfo.finishLoading();
            });
        }

        @Override
        public void onError(boolean suggestRetry, SubmissionFetchMsg<T> msg) {
            if (suggestRetry)
                fetchSubmission(submission, module, readonly);
            Platform.runLater(userInfo::finishLoading);
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

    private class SubmissionCreateResponseHandler<T extends ArisModule> implements ResponseHandler<SubmissionCreateMsg<T>> {

        private final AssignedProblem info;

        SubmissionCreateResponseHandler(AssignedProblem info) {
            this.info = info;
        }

        @Override
        public void response(SubmissionCreateMsg<T> message) {
            Platform.runLater(() -> {
                Submission sub = new Submission(info.getPid(), message.getSid(), info.getName(), message.getSubmittedOn(), message.getStatus(), message.getStatusStr(), info.getModuleName());
                ObservableList<TreeItem<Submission>> subs = null;
                for (TreeItem<Submission> s : problems) {
                    if (s.getValue() instanceof AssignedProblem && s.getValue().getPid() == info.getPid())
                        subs = ((AssignedProblem) s.getValue()).submissions;
                }
                if (subs != null)
                    subs.add(0, new TreeItem<>(sub));
                File pFile = new File(submissionStorageDir, getProblemFileName(info.getPid()));
                if (pFile.canExecute())
                    //noinspection ResultOfMethodCallIgnored
                    pFile.delete();
                userInfo.finishLoading();
            });
        }

        @Override
        public void onError(boolean suggestRetry, SubmissionCreateMsg<T> msg) {
            if (suggestRetry)
                uploadSubmission(info, msg.getProblem());
            else {
                ArisModule<T> module = ModuleService.getService().getModule(msg.getModuleName());
                if (module != null) {
                    try {
                        gui.createSubmission(info, msg.getProblem(), module);
                    } catch (Exception e) {
                        LibAssign.showExceptionError(e);
                    }
                }
            }
            Platform.runLater(userInfo::finishLoading);
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

    public class Submission implements Comparable<Submission> {

        private final int pid;
        private final int sid;
        private final SimpleStringProperty name;
        private final SimpleObjectProperty<ZonedDateTime> submittedOn;
        private final SimpleStringProperty submittedOnStr = new SimpleStringProperty();
        private final SimpleStringProperty statusStr;
        private final SimpleObjectProperty<Node> controlNode;
        private final SimpleObjectProperty<GradingStatus> status;
        private final String moduleName;

        public Submission(int pid, int sid, String name, ZonedDateTime submittedOn, GradingStatus status, String statusStr, String moduleName) {
            this.pid = pid;
            this.sid = sid;
            this.name = new SimpleStringProperty(name);
            this.submittedOn = new SimpleObjectProperty<>(submittedOn);
            this.status = new SimpleObjectProperty<>(status);
            this.moduleName = moduleName;
            this.submittedOnStr.bind(Bindings.createStringBinding(() -> this.submittedOn.get() == null ? "Never" : AssignGui.DATE_FORMAT.format(new Date(NetUtil.UTCToMilli(this.submittedOn.get()))), this.submittedOn));
            this.statusStr = new SimpleStringProperty(statusStr);
            Button view = new Button("View");
            view.setOnAction(e -> viewSubmission(true));
            Button resubmit = new Button("Resubmit");
            resubmit.setOnAction(e -> viewSubmission(false));
            HBox box = new HBox(5);
            box.getChildren().addAll(view, resubmit);
            box.setAlignment(Pos.CENTER);
            controlNode = new SimpleObjectProperty<>(box);
        }

        public Submission(MsgUtil.SubmissionInfo info, String moduleName) {
            this(info.pid, info.sid, null, info.submissionTime, info.status, info.statusStr, moduleName);
        }

        private <T extends ArisModule> void viewSubmission(boolean readonly) {
            ArisModule<T> module = ModuleService.getService().getModule(getModuleName());
            if (module == null) {
                AssignClient.getInstance().getMainWindow().displayErrorMsg("Missing Module", "Client is missing \"" + getModuleName() + "\" module");
                return;
            }
            fetchSubmission(this, module, readonly);
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

        public Node getControlNode() {
            return controlNode.get();
        }

        public SimpleObjectProperty<Node> controlNodeProperty() {
            return controlNode;
        }

        @Override
        public int compareTo(Submission o) {
            if (o == null)
                return 0;
            return Integer.compare(sid, o.sid);
        }

        public SimpleObjectProperty<GradingStatus> getStatusProperty() {
            return status;
        }

        public String getModuleName() {
            return moduleName;
        }
    }


    public class AssignedProblem extends Submission implements Comparable<Submission> {

        private final ObservableList<TreeItem<Submission>> submissions = FXCollections.observableArrayList();

        public AssignedProblem(int pid, String name, String status, String moduleName) {
            super(pid, -1, name, null, GradingStatus.NONE, status, moduleName);
            Button btn = new Button("Create Submission");
            controlNodeProperty().set(btn);
            btn.setOnAction(e -> createPushed());
            statusStrProperty().bind(Bindings.createStringBinding(() -> {
                switch (getStatusProperty().get()) {
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
            }, getStatusProperty()));
        }

        AssignedProblem(MsgUtil.ProblemInfo info) {
            this(info.pid, info.name, null, info.moduleName);
        }

        private <T extends ArisModule> void createPushed() {
            ArisModule<T> module = ModuleService.getService().getModule(getModuleName());
            if (module == null) {
                AssignClient.getInstance().getMainWindow().displayErrorMsg("Missing Module", "Client is missing \"" + getModuleName() + "\" module");
                return;
            }
            File localFile = new File(submissionStorageDir, getProblemFileName(getPid()));
            if (localFile.exists()) {
                try (FileInputStream fis = new FileInputStream(localFile)) {
                    ProblemConverter<T> converter = module.getProblemConverter();
                    gui.createSubmission(this, converter.loadProblem(fis, true), module);
                } catch (Exception e) {
                    LibAssign.showExceptionError(e);
                }
            } else
                fetchAndCreate(getPid(), module);
        }

        @Override
        public int compareTo(Submission o) {
            if (o instanceof AssignedProblem)
                return getName().compareTo(o.getName());
            return super.compareTo(o);
        }
    }

}
