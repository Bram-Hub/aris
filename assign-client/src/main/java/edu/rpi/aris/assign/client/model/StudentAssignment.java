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
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.encoders.Hex;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class StudentAssignment implements ResponseHandler<AssignmentGetStudentMsg> {

    private static final Logger log = LogManager.getLogger();
    private static final CurrentUser userInfo = CurrentUser.getInstance();
    private static final File problemStorageDir = new File(LocalConfig.CLIENT_CACHE_DIR, "problems");
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

    private <T extends ArisModule> void cacheProblem(AssignedProblem problem, boolean open) {
        File f = new File(problemStorageDir, problem.problemHash);
        ArisModule<T> module = ModuleService.getService().getModule(problem.getModuleName());
        if (module == null) {
            AssignClient.displayErrorMsg("Missing Module", "Client is missing \"" + problem.getModuleName() + "\" module");
            return;
        }
        if (!f.exists()) {
            userInfo.startLoading();
            Client.getInstance().processMessage(new ProblemFetchMsg<>(problem.getPid(), problem.getModuleName()), new ProblemFetchResponseHandler<>(problem, open, module));
        } else if (open) {
            try (FileInputStream fis = new FileInputStream(f)) {
                ProblemConverter<T> converter = module.getProblemConverter();
                gui.createAttempt(new Attempt(problem), problem.getName(), converter.loadProblem(fis, false), module);
            } catch (Exception e) {
                if (f.delete())
                    cacheProblem(problem, true);
                else
                    LibAssign.showExceptionError(e);
            }
        }
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

    private void loadAttempts() {
        if (OfflineDB.loaded()) {
            HashMap<Integer, HashSet<Attempt>> attempts = new HashMap<>();
            try (Connection connection = OfflineDB.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT pid, created_time, module_name FROM attempts WHERE aid=? AND cid=?;")) {
                statement.setInt(1, aid);
                statement.setInt(2, cid);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        int pid = rs.getInt(1);
                        Attempt attempt = new Attempt(pid, ZonedDateTime.parse(rs.getString(2)), null, rs.getString(3));
                        attempts.computeIfAbsent(pid, id -> new HashSet<>()).add(attempt);
                    }
                }
            } catch (SQLException e) {
                log.error("Failed to load attempts from offline DB", e);
                LibAssign.showExceptionError(e);
            }
            Platform.runLater(() -> {
                for (TreeItem<Submission> sub : problems) {
                    AssignedProblem prob = (AssignedProblem) sub.getValue();
                    HashSet<Attempt> set = attempts.get(prob.getPid());
                    if (set != null) {
                        set.stream().sorted().map(TreeItem<Submission>::new).forEach(i -> {
                            ((Attempt) i.getValue()).setProblemName(prob.getName());
                            prob.submissions.add(0, i);
                        });
                    }
                }
            });
        }
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
                        AtomicInteger i = new AtomicInteger((int) c.getList().stream().filter(item -> !(item.getValue() instanceof Attempt)).count());
                        p.getChildren().forEach(item -> {
                            if (!(item.getValue() instanceof Attempt))
                                item.getValue().name.set("Submission " + (i.getAndDecrement()));
                        });
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
                cacheProblem(assignedProblem, false);
            }
            problems.sort(Comparator.comparing(TreeItem::getValue));
            new Thread(this::loadAttempts, "Load Attempts").start();
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

    public synchronized <T extends ArisModule> void saveAttempt(Attempt attempt, Problem<T> problem, ArisModule<T> module) {
        boolean error = true;
        boolean overwrite = false;
        if (OfflineDB.loaded()) {
            try (Connection connection = OfflineDB.getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement check = connection.prepareStatement("SELECT count(1) FROM attempts WHERE aid=? AND cid=? AND pid=? AND created_time=?;");
                     PreparedStatement insert = connection.prepareStatement("INSERT INTO attempts (data, aid, cid, pid, created_time, module_name) VALUES (?, ?, ?, ?, ?, ?);");
                     PreparedStatement update = connection.prepareStatement("UPDATE attempts SET data=? WHERE aid=? AND cid=? AND pid=? AND created_time=?;");
                     ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    String dateStr = attempt.getSubmittedOnDate().toString();

                    check.setInt(1, aid);
                    check.setInt(2, cid);
                    check.setInt(3, attempt.getPid());
                    check.setString(4, dateStr);

                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next())
                            overwrite = rs.getInt(1) > 0;
                    }

                    PreparedStatement stmt = overwrite ? update : insert;
                    stmt.setInt(2, aid);
                    stmt.setInt(3, cid);
                    stmt.setInt(4, attempt.getPid());
                    stmt.setString(5, dateStr);
                    if (!overwrite)
                        stmt.setString(6, attempt.getModuleName());

                    module.getProblemConverter().convertProblem(problem, baos, true);

                    stmt.setBytes(1, baos.toByteArray());
                    stmt.executeUpdate();
                    connection.commit();
                    error = false;
                } catch (Exception e) {
                    connection.rollback();
                    throw e;
                }
            } catch (Exception e) {
                log.error("An exception occurred", e);
                e.printStackTrace();
            }
        }
        if (error) {
            AssignClient.displayErrorMsg("Save Error", "An error occurred while trying to save the problem locally. The problem will be uploaded and you can make a copy of the problem to continue working");
            Platform.runLater(() -> uploadAttempt(attempt, problem));
        } else if (!overwrite) {
            for (TreeItem<Submission> item : problems) {
                AssignedProblem prob = (AssignedProblem) item.getValue();
                if (prob.getPid() == attempt.getPid()) {
                    Platform.runLater(() -> prob.submissions.add(0, new TreeItem<>(attempt)));
                    return;
                }
            }
        }
    }

    public <T extends ArisModule> void uploadAttempt(Attempt problemInfo, Problem<T> problem) {
        userInfo.startLoading();
        Client.getInstance().processMessage(new SubmissionCreateMsg<>(cid, aid, problemInfo.getPid(), problemInfo.getModuleName(), problem), new SubmissionCreateResponseHandler<>(problemInfo));
    }

    public <T extends ArisModule> void fetchSubmission(Submission submission, ArisModule<T> module, boolean readonly) {
        userInfo.startLoading();
        Client.getInstance().processMessage(new SubmissionFetchMsg<>(cid, aid, submission.getPid(), submission.getSid(), submission.getModuleName()), new SubmissionFetchResponseHandler<>(module, readonly, submission));
    }

    private class ProblemFetchResponseHandler<T extends ArisModule> implements ResponseHandler<ProblemFetchMsg<T>> {

        private final AssignedProblem problem;
        private final boolean open;
        private final ArisModule<T> module;

        ProblemFetchResponseHandler(AssignedProblem problem, boolean open, ArisModule<T> module) {
            this.problem = problem;
            this.open = open;
            this.module = module;
        }

        @Override
        public void response(ProblemFetchMsg<T> message) {
            Platform.runLater(() -> {
                Problem<T> problem = message.getProblem();
                File f = new File(problemStorageDir, this.problem.problemHash);
                if (problemStorageDir.exists() || problemStorageDir.mkdirs()) {
                    try (FileOutputStream fos = new FileOutputStream(f)) {
                        module.getProblemConverter().convertProblem(problem, fos, false);
                    } catch (Exception e) {
                        LibAssign.showExceptionError(e);
                    }
                } else {
                    AssignClient.displayErrorMsg("Cache Failed", "Failed to cache assignment. Unable to create cache directory", true);
                }
                if (open) {
                    try {
                        gui.createAttempt(new Attempt(this.problem), this.problem.getName(), problem, module);
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
                cacheProblem(problem, open);
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
                        gui.createAttempt(new Attempt(problemInfo), problemInfo.getName(), problem, module);
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

        private final Attempt info;

        SubmissionCreateResponseHandler(Attempt info) {
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
                    subs.add((int) subs.stream().filter(i -> i.getValue() instanceof Attempt).count(), new TreeItem<>(sub));
                info.deleteAttempt();
                userInfo.finishLoading();
            });
        }

        @Override
        public void onError(boolean suggestRetry, SubmissionCreateMsg<T> msg) {
            if (suggestRetry)
                uploadAttempt(info, msg.getProblem());
            else {
                ArisModule<T> module = ModuleService.getService().getModule(msg.getModuleName());
                if (module != null) {
                    saveAttempt(new Attempt(info), msg.getProblem(), module);
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
            Button resubmit = new Button("Reattempt");
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
                AssignClient.displayErrorMsg("Missing Module", "Client is missing \"" + getModuleName() + "\" module");
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
        public int compareTo(@NotNull Submission o) {
            if (o instanceof Attempt)
                return -1;
            return Integer.compare(sid, o.sid);
        }

        public SimpleObjectProperty<GradingStatus> getStatusProperty() {
            return status;
        }

        public String getModuleName() {
            return moduleName;
        }

        public ZonedDateTime getSubmittedOnDate() {
            return submittedOn.get();
        }

        public SimpleObjectProperty<ZonedDateTime> submittedOnDateProperty() {
            return submittedOn;
        }

    }

    public class AssignedProblem extends Submission implements Comparable<Submission> {

        private final ObservableList<TreeItem<Submission>> submissions = FXCollections.observableArrayList();
        private final String problemHash;

        public AssignedProblem(int pid, String name, String status, String moduleName, String problemHash) {
            super(pid, -1, name, null, GradingStatus.NONE, status, moduleName);
            this.problemHash = problemHash;
            Button btn = new Button("Start Attempt");
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
            this(info.pid, info.name, null, info.moduleName, info.problemHash);
        }

        private <T extends ArisModule> void createPushed() {
            ArisModule<T> module = ModuleService.getService().getModule(getModuleName());
            if (module == null) {
                AssignClient.displayErrorMsg("Missing Module", "Client is missing \"" + getModuleName() + "\" module");
                return;
            }
            cacheProblem(this, true);
        }

        @Override
        public int compareTo(@NotNull Submission o) {
            if (o instanceof AssignedProblem)
                return getName().compareTo(o.getName());
            return super.compareTo(o);
        }
    }

    public class Attempt extends AssignedProblem implements Comparable<Submission> {

        private String problemName;

        public Attempt(int pid, ZonedDateTime createdOn, String problemName, String moduleName) {
            super(pid, null, null, moduleName, null);
            this.problemName = problemName;
            submittedOnDateProperty().set(createdOn);
            nameProperty().set("Attempt " + getSubmittedOn());
            statusStrProperty().unbind();
            statusStrProperty().set("Not Submitted");
            submittedOnProperty().unbind();
            submittedOnProperty().set("Not Submitted");
            Button edit = new Button("Edit");
            edit.setOnAction(e -> editAttempt());
            Button upload = new Button("Upload");
            upload.setOnAction(e -> uploadAttempt());
            Button delete = new Button("Delete");
            delete.setOnAction(e -> askDelete());
            HBox box = new HBox(5);
            box.getChildren().addAll(edit, upload, delete);
            box.setAlignment(Pos.CENTER);
            controlNodeProperty().set(box);
        }

        public Attempt(AssignedProblem problem) {
            this(problem.getPid(), ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC), problem.getName(), problem.getModuleName());
        }

        private <T extends ArisModule> void uploadAttempt() {
            new Thread(() -> {
                ArisModule<T> module = ModuleService.getService().getModule(getModuleName());
                if (module == null) {
                    AssignClient.displayErrorMsg("Missing Module", "Client is missing \"" + getModuleName() + "\" module");
                    return;
                }
                Problem<T> problem = null;
                try (Connection connection = OfflineDB.getConnection();
                     PreparedStatement stmt = connection.prepareStatement("SELECT data FROM attempts WHERE aid=? AND cid=? AND pid=? and created_time=?;")) {
                    stmt.setInt(1, aid);
                    stmt.setInt(2, cid);
                    stmt.setInt(3, getPid());
                    stmt.setString(4, getSubmittedOnDate().toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next())
                            throw new SQLException("Attempt does not exist in database");
                        ByteArrayInputStream bais = new ByteArrayInputStream(rs.getBytes(1));
                        problem = module.getProblemConverter().loadProblem(bais, true);
                    }
                } catch (Exception e) {
                    log.error("Failed to load attempt from database", e);
                    AssignClient.displayErrorMsg("Upload Failed", "An error occurred while trying to load the attempt");
                }
                if (problem != null) {
                    Problem<T> finalProblem = problem;
                    Platform.runLater(() -> StudentAssignment.this.uploadAttempt(this, finalProblem));
                }
            }, "Attempt Upload").start();
        }

        private void askDelete() {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setHeaderText("Delete Attempt: " + getName());
            alert.setContentText("Are you sure you want to delete this attempt? This cannot be undone.");
            alert.getDialogPane().getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
            ((Button) alert.getDialogPane().lookupButton(ButtonType.NO)).setDefaultButton(true);
            ((Button) alert.getDialogPane().lookupButton(ButtonType.YES)).setDefaultButton(false);
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            alert.initModality(Modality.WINDOW_MODAL);
            alert.initOwner(AssignGui.getInstance().getStage());
            Optional<ButtonType> result = alert.showAndWait();
            result.ifPresent(r -> {
                if (r == ButtonType.YES)
                    deleteAttempt();
            });
        }

        private void deleteAttempt() {
            new Thread(() -> {
                try (Connection connection = OfflineDB.getConnection();
                     PreparedStatement stmt = connection.prepareStatement("DELETE FROM attempts WHERE aid=? AND cid=? AND pid=? and created_time=?;")) {
                    stmt.setInt(1, aid);
                    stmt.setInt(2, cid);
                    stmt.setInt(3, getPid());
                    stmt.setString(4, getSubmittedOnDate().toString());
                    stmt.executeUpdate();
                    Platform.runLater(() -> {
                        for (TreeItem<Submission> sub : problems) {
                            AssignedProblem prob = (AssignedProblem) sub.getValue();
                            if (prob.getPid() == getPid()) {
                                prob.submissions.removeIf(i -> i.getValue() == Attempt.this);
                                return;
                            }
                        }
                    });
                } catch (Exception e) {
                    log.error("Failed to delete attempt from database", e);
                    AssignClient.displayErrorMsg("Delete Failed", "An error occurred while trying to delete the attempt");
                }
            }, "Delete Attempt").start();
        }

        private <T extends ArisModule> void editAttempt() {
            new Thread(() -> {
                ArisModule<T> module = ModuleService.getService().getModule(getModuleName());
                if (module == null) {
                    AssignClient.displayErrorMsg("Missing Module", "Client is missing \"" + getModuleName() + "\" module");
                    return;
                }
                Problem<T> problem = null;
                try (Connection connection = OfflineDB.getConnection();
                     PreparedStatement stmt = connection.prepareStatement("SELECT data FROM attempts WHERE aid=? AND cid=? AND pid=? and created_time=?;")) {
                    stmt.setInt(1, aid);
                    stmt.setInt(2, cid);
                    stmt.setInt(3, getPid());
                    stmt.setString(4, getSubmittedOnDate().toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next())
                            throw new SQLException("Attempt does not exist in database");
                        ByteArrayInputStream bais = new ByteArrayInputStream(rs.getBytes(1));
                        problem = module.getProblemConverter().loadProblem(bais, true);
                    }
                } catch (Exception e) {
                    log.error("Failed to load attempt from database", e);
                    AssignClient.displayErrorMsg("Open Failed", "An error occurred while trying to open the attempt");
                }
                if (problem != null) {
                    Problem<T> finalProblem = problem;
                    Platform.runLater(() -> {
                        try {
                            gui.createAttempt(this, problemName, finalProblem, module);
                        } catch (Exception e) {
                            LibAssign.showExceptionError(e);
                        }
                    });
                }
            }, "Attempt Edit Load").start();
        }

        public int compareTo(@NotNull Submission o) {
            if (o instanceof Attempt)
                return getSubmittedOnDate().compareTo(o.getSubmittedOnDate());
            else
                return 1;
        }

        void setProblemName(String name) {
            problemName = name;
        }
    }

}
