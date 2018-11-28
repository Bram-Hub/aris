package edu.rpi.aris.assign.client.model;

import edu.rpi.aris.assign.*;
import edu.rpi.aris.assign.client.AssignClient;
import edu.rpi.aris.assign.client.Client;
import edu.rpi.aris.assign.client.ResponseHandler;
import edu.rpi.aris.assign.client.controller.AssignGui;
import edu.rpi.aris.assign.client.controller.SingleAssignmentGui;
import edu.rpi.aris.assign.message.*;
import edu.rpi.aris.assign.spi.ArisModule;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
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
import org.jetbrains.annotations.NotNull;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class SingleAssignment {

    private static final Logger log = LogManager.getLogger();
    private static final CurrentUser userInfo = CurrentUser.getInstance();
    private static final HashSet<SingleAssignment> assignmentsOpen = new HashSet<>();
    private static final ReentrantLock lock = new ReentrantLock(true);
    private static ScheduledExecutorService submissionGradedCheck = null;
    private final int cid;
    private final int aid;
    private final SimpleStringProperty name = new SimpleStringProperty();
    private final SimpleStringProperty dueDate = new SimpleStringProperty();
    private final SimpleObjectProperty<GradingStatus> status = new SimpleObjectProperty<>();
    private final SimpleDoubleProperty grade = new SimpleDoubleProperty(0);
    private final SimpleStringProperty statusStr = new SimpleStringProperty();
    private final ObservableList<TreeItem<Submission>> problems = FXCollections.observableArrayList();
    private final SimpleBooleanProperty loadErrorProperty = new SimpleBooleanProperty(false);
    private final ResponseHandler<AssignmentGetStudentMsg> studentHandler = new AssignmentGetStudentHandler();
    private final ResponseHandler<AssignmentGetInstructorMsg> instructorHandler = new AssignmentGetInstructorHandler();
    private final SingleAssignmentGui gui;
    private final boolean isInstructor;
    private boolean loaded = false;

    public SingleAssignment(SingleAssignmentGui gui, String name, int cid, int aid, boolean isInstructor) {
        this.gui = gui;
        this.isInstructor = isInstructor;
        this.name.set(name);
        this.cid = cid;
        this.aid = aid;
        statusStr.bind(Bindings.createStringBinding(() -> {
            GradingStatus status = this.status.get();
            String grade = " (" + Math.round(this.grade.get() * 100.0) / 100.0 + "/" + (double) problems.size() + ")";
            if (status == null)
                return "Unknown";
            switch (status) {
                case CORRECT:
                    return "Complete" + grade;
                case INCORRECT:
                case PARTIAL:
                    return "Incomplete" + grade;
                case GRADING:
                    return "Your submission is being graded" + grade;
                case NONE:
                    return "You have not made any submissions" + grade;
                default:
                    return "Unknown" + grade;
            }
        }, status, grade, problems));
        addAssignment(this);
    }

    private synchronized static void updateGradingSubmissions() {
        HashMap<Integer, TreeItem<Submission>> subs = new HashMap<>();
        for (SingleAssignment assignment : assignmentsOpen) {
            for (TreeItem<Submission> item : assignment.problems) {
                AssignedProblem problem = (AssignedProblem) item.getValue();
                if (problem.getStatusProperty().get() == GradingStatus.GRADING) {
                    for (TreeItem<Submission> subItem : problem.submissions) {
                        Submission sub = subItem.getValue();
                        if (sub.getStatusProperty().get() == GradingStatus.GRADING)
                            subs.put(sub.sid, subItem);
                    }
                }
            }
        }
        if (subs.size() > 0) {
            log.info(subs.size() + " Submissions to refresh");
            Platform.runLater(() -> {
                Client.getInstance().processMessage(new SubmissionRefresh(subs.keySet()), new SubmissionRefreshHandler(subs));
            });
        }
    }

    public static synchronized void addAssignment(SingleAssignment assignment) {
        assignmentsOpen.add(assignment);
        if (submissionGradedCheck == null) {
            submissionGradedCheck = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("Student Submission Refresh", true));
            submissionGradedCheck.scheduleAtFixedRate(SingleAssignment::updateGradingSubmissions, 5, 5, TimeUnit.SECONDS);
        }
    }

    public static synchronized void removeAssignment(SingleAssignment assignment) {
        assignmentsOpen.remove(assignment);
        if (assignmentsOpen.size() == 0 && submissionGradedCheck != null) {
            submissionGradedCheck.shutdown();
            submissionGradedCheck = null;
        }
    }

    public static synchronized void cancelGradeCheck() {
        submissionGradedCheck.shutdownNow();
        submissionGradedCheck = null;
    }

    public synchronized void loadAssignment(boolean reload) {
        if (reload || !loaded) {
            clear();
            if (isInstructor) {
                Client.getInstance().processMessage(new AssignmentGetInstructorMsg(cid, aid), instructorHandler);
            } else
                Client.getInstance().processMessage(new AssignmentGetStudentMsg(cid, aid), studentHandler);
        }
    }

    private <T extends ArisModule> void cacheProblem(AssignedProblem problem, boolean open, boolean readOnly) {
        OfflineDB.submit(connection -> {
            ArisModule<T> module = ModuleService.getService().getModule(problem.getModuleName());
            if (module == null) {
                AssignClient.displayErrorMsg("Missing Module", "Client is missing \"" + problem.getModuleName() + "\" module");
                return;
            }
            boolean cached = false;
            if (connection != null)
                try (PreparedStatement selectProblem = connection.prepareStatement("SELECT data, problem_hash FROM problems WHERE id=?;");
                     PreparedStatement deleteProblem = connection.prepareStatement("DELETE FROM problems WHERE id=?;")) {
                    selectProblem.setInt(1, problem.getPid());
                    try (ResultSet rs = selectProblem.executeQuery()) {
                        if ((cached = rs.next() && rs.getString(2).equals(problem.problemHash)) && open) {
                            try {
                                ProblemConverter<T> converter = module.getProblemConverter();
                                Problem<T> prob = converter.loadProblem(rs.getBinaryStream(1), false);
                                if (readOnly)
                                    gui.viewProblem("Viewing Problem: " + problem.getName() + " (read only)", prob, module);
                                else
                                    gui.createAttempt(new Attempt(problem), problem.getName(), prob, module);
                            } catch (Exception e) {
                                log.error("Error loading problem from database", e);
                                deleteProblem.setInt(1, problem.getPid());
                                deleteProblem.executeUpdate();
                                cacheProblem(problem, true, readOnly);
                            }
                        }
                    }
                } catch (SQLException e) {
                    log.error("Failed to cache problem", e);
                }
            if (!cached) {
                Client.getInstance().processMessage(new ProblemFetchMsg<>(problem.getPid(), problem.getModuleName()), new ProblemFetchResponseHandler<>(problem, open, readOnly, module));
            }
        });
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
        OfflineDB.submit(connection -> {
            HashMap<Integer, HashSet<Attempt>> attempts = new HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT pid, created_time, module_name FROM attempts WHERE aid=? AND cid=?;")) {
                statement.setInt(1, aid);
                statement.setInt(2, cid);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        int pid = rs.getInt(1);
                        Attempt attempt = new Attempt(pid, rs.getTimestamp(2).toInstant().atZone(ZoneId.systemDefault()), null, rs.getString(3));
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
        });
    }

    private void updateStudentStatus(Student parent, Collection<TreeItem<Submission>> children) {
        double grade = 0;
        for (TreeItem<Submission> item : children) {
            Submission prob = item.getValue();
            grade += prob.getGrade();
        }
        int numProb = children.size();
        parent.gradeProperty().set(grade);
        parent.getStatusProperty().set(numProb == grade ? GradingStatus.CORRECT : (grade == 0 ? GradingStatus.INCORRECT : GradingStatus.PARTIAL));
    }

    private void updateProblemStatus(Submission parent, Collection<TreeItem<Submission>> children) {
        Submission best = null;
        boolean grading = false;
        for (TreeItem<Submission> item : children) {
            Submission sub = item.getValue();
            if (!(sub instanceof Attempt) && (best == null || sub.getGrade() > best.getGrade()))
                best = sub;
            if (sub.status.get() == GradingStatus.GRADING)
                grading = true;
        }
        if (best == null) {
            parent.submittedOn.set(null);
            parent.status.set(GradingStatus.NONE);
            parent.grade.set(0);
        } else {
            parent.submittedOn.set(best.submittedOn.get());
            parent.status.set(grading ? GradingStatus.GRADING : best.status.get());
            parent.grade.set(best.grade.get());
        }
        if (!isInstructor)
            updateAssignmentStatus();
    }

    private void updateAssignmentStatus() {
        if (isInstructor)
            return;
        double grade = 0;
        for (TreeItem<Submission> item : problems) {
            Submission prob = item.getValue();
            grade += prob.getGrade();
        }
        int numProb = problems.size();
        this.grade.set(grade);
        status.set(numProb == grade ? GradingStatus.CORRECT : (grade == 0 ? GradingStatus.INCORRECT : GradingStatus.PARTIAL));
    }

    public <T extends ArisModule> boolean saveAttempt(Attempt attempt, Problem<T> problem, ArisModule<T> module, boolean submitOnError) {
        AtomicBoolean error = new AtomicBoolean(true);
        AtomicBoolean overwrite = new AtomicBoolean(false);
        OfflineDB.submit(connection -> {
            if (connection != null) {
                try (PreparedStatement check = connection.prepareStatement("SELECT count(1) FROM attempts WHERE aid=? AND cid=? AND pid=? AND created_time=?;");
                     PreparedStatement insert = connection.prepareStatement("INSERT INTO attempts (data, aid, cid, pid, created_time, module_name) VALUES (?, ?, ?, ?, ?, ?);");
                     PreparedStatement update = connection.prepareStatement("UPDATE attempts SET data=? WHERE aid=? AND cid=? AND pid=? AND created_time=?;")) {
                    Timestamp date = Timestamp.from(attempt.getSubmittedOnDate().toInstant());

                    check.setInt(1, aid);
                    check.setInt(2, cid);
                    check.setInt(3, attempt.getPid());
                    check.setTimestamp(4, date);

                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next())
                            overwrite.set(rs.getInt(1) > 0);
                    }

                    PreparedStatement stmt = overwrite.get() ? update : insert;
                    stmt.setInt(2, aid);
                    stmt.setInt(3, cid);
                    stmt.setInt(4, attempt.getPid());
                    stmt.setTimestamp(5, date);
                    if (!overwrite.get())
                        stmt.setString(6, attempt.getModuleName());

                    try (PipedInputStream pis = new PipedInputStream();
                         PipedOutputStream pos = new PipedOutputStream(pis)) {

                        LibAssign.convertProblem(pos, problem, module, true);

                        stmt.setBinaryStream(1, pis);
                        stmt.executeUpdate();

                        connection.commit();
                    }
                    error.set(false);
                } catch (Exception e) {
                    log.error("An exception occurred", e);
                }
            }
        }, true);
        if (error.get()) {
            AssignClient.displayErrorMsg("Save Error", "An error occurred while trying to save the problem locally." + (submitOnError ? "The problem will be uploaded and you can make a copy of the problem to continue working" : ""));
            if (submitOnError)
                Platform.runLater(() -> uploadAttempt(attempt, problem));
            return false;
        } else if (!overwrite.get()) {
            for (TreeItem<Submission> item : problems) {
                AssignedProblem prob = (AssignedProblem) item.getValue();
                if (prob.getPid() == attempt.getPid()) {
                    Platform.runLater(() -> prob.submissions.add(0, new TreeItem<>(attempt)));
                    return true;
                }
            }
        }
        return false;
    }

    public <T extends ArisModule> void uploadAttempt(Attempt problemInfo, Problem<T> problem) {
        Client.getInstance().processMessage(new SubmissionCreateMsg<>(cid, aid, problemInfo.getPid(), problemInfo.getModuleName(), problem), new SubmissionCreateResponseHandler<>(problemInfo));
    }

    public <T extends ArisModule> void fetchSubmission(Submission submission, ArisModule<T> module, boolean readonly) {
        SubmissionFetchMsg<T> msg;
        if (isInstructor)
            msg = new SubmissionFetchMsg<>(cid, aid, submission.getPid(), submission.getSid(), submission.getUid(), submission.getModuleName());
        else
            msg = new SubmissionFetchMsg<>(cid, aid, submission.getPid(), submission.getSid(), submission.getModuleName());
        Client.getInstance().processMessage(msg, new SubmissionFetchResponseHandler<>(module, readonly, submission));
    }

    public void closed() {
        removeAssignment(this);
    }

    public static class SubmissionRefreshHandler implements ResponseHandler<SubmissionRefresh> {

        private final HashMap<Integer, TreeItem<Submission>> subs;

        public SubmissionRefreshHandler(HashMap<Integer, TreeItem<Submission>> subs) {
            this.subs = subs;
        }

        @Override
        public void response(SubmissionRefresh message) {
            Platform.runLater(() -> message.getInfo().forEach((id, info) -> {
                TreeItem<Submission> item = subs.get(id);
                if (item != null)
                    item.getValue().updateInfo(info, item);
            }));
        }

        @Override
        public void onError(boolean suggestRetry, SubmissionRefresh msg) {
            if (!suggestRetry)
                cancelGradeCheck();
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

    public class AssignmentGetInstructorHandler implements ResponseHandler<AssignmentGetInstructorMsg> {

        @Override
        public void response(AssignmentGetInstructorMsg message) {
            Platform.runLater(() -> {
                name.set(message.getName());
                dueDate.set(AssignGui.DATE_FORMAT.format(new Date(NetUtil.UTCToMilli(message.getDueDate()))));
                message.getUsers().entrySet().stream().filter(e -> e.getValue() != null && e.getValue().getLeft() != null).sorted(Comparator.comparing(o -> o.getValue().getLeft())).forEachOrdered(entry -> {
                    Student student = new Student(entry.getKey(), entry.getValue().getLeft() + " (" + entry.getValue().getRight() + ")", "Unknown", null);
                    TreeItem<Submission> s = new TreeItem<>(student);
                    problems.add(s);
                    message.getProblems().stream().sorted(Comparator.comparing(p -> p.name == null ? "" : p.name)).forEachOrdered(pInfo -> {
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
                        updateProblemStatus(problem, problem.submissions);
                        p.getChildren().addAll(problem.submissions);
                    });
                    updateStudentStatus(student, student.problems);
                    s.getChildren().addAll(student.problems);
                });
                loaded = true;
                loadErrorProperty.set(false);
            });
        }

        @Override
        public void onError(boolean suggestRetry, AssignmentGetInstructorMsg msg) {
            Platform.runLater(() -> {
                clear();
                loadErrorProperty.set(true);
                if (suggestRetry)
                    loadAssignment(true);
            });
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }

    }

    public class AssignmentGetStudentHandler implements ResponseHandler<AssignmentGetStudentMsg> {

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
                            AtomicInteger i = new AtomicInteger((int) c.getList().stream().map(TreeItem::getValue).filter(item -> !(item instanceof Attempt)).count());
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
                        submissionInfos.sort((o1, o2) -> {
                            if (o1.submissionTime == null || o2.submissionTime == null)
                                return 0;
                            return o2.submissionTime.compareTo(o1.submissionTime);
                        });
                        for (MsgUtil.SubmissionInfo submissionInfo : submissionInfos) {
                            Submission submission = new Submission(submissionInfo, assignedProblem.getModuleName());
                            TreeItem<Submission> sub = new TreeItem<>(submission);
                            subs.add(sub);
                        }
                    }
                    problems.add(p);
                    updateProblemStatus(assignedProblem, p.getChildren());
                    cacheProblem(assignedProblem, false, false);
                }
                problems.sort(Comparator.comparing(TreeItem::getValue));
                loadAttempts();
                loaded = true;
                loadErrorProperty.set(false);
            });
        }

        @Override
        public void onError(boolean suggestRetry, AssignmentGetStudentMsg msg) {
            Platform.runLater(() -> {
                clear();
                loadErrorProperty.set(true);
                if (suggestRetry)
                    loadAssignment(true);
            });
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }

    }

    private class ProblemFetchResponseHandler<T extends ArisModule> implements ResponseHandler<ProblemFetchMsg<T>> {

        private final AssignedProblem problem;
        private final boolean open;
        private final boolean readOnly;
        private final ArisModule<T> module;

        ProblemFetchResponseHandler(AssignedProblem problem, boolean open, boolean readOnly, ArisModule<T> module) {
            this.problem = problem;
            this.open = open;
            this.readOnly = readOnly;
            this.module = module;
        }

        @Override
        public void response(ProblemFetchMsg<T> message) {
            Problem<T> problem = message.getProblem();
            OfflineDB.submit(connection -> {
                if (connection == null)
                    return;
                try (PreparedStatement check = connection.prepareStatement("SELECT problem_hash FROM problems WHERE id=?;");
                     PreparedStatement delete = connection.prepareStatement("DELETE FROM problems WHERE id=?;");
                     PreparedStatement insert = connection.prepareStatement("INSERT INTO problems (id, name, module_name, problem_hash, data) VALUES (?, ?, ?, ?, ?);")) {
                    check.setInt(1, message.getPid());
                    boolean exists;
                    try (ResultSet rs = check.executeQuery()) {
                        if ((exists = rs.next()) && !rs.getString(1).equals(message.getProblemHash())) {
                            delete.setInt(1, message.getPid());
                            delete.executeUpdate();
                            exists = false;
                        }
                    }
                    if (!exists) {
                        try (PipedInputStream pis = new PipedInputStream();
                             PipedOutputStream pos = new PipedOutputStream(pis)) {
                            insert.setInt(1, message.getPid());
                            insert.setString(2, this.problem.getName());
                            insert.setString(3, message.getModuleName());
                            insert.setString(4, message.getProblemHash());

                            LibAssign.convertProblem(pos, problem, module, false);

                            insert.setBinaryStream(5, pis);
                            insert.executeUpdate();
                        } catch (Exception e) {
                            log.error("Failed to cache problem", e);
                        }
                    }
                } catch (SQLException e) {
                    log.error("Failed to cache problem", e);
                }
            });
            if (open) {
                try {
                    if (readOnly)
                        gui.viewProblem("Viewing Problem: " + this.problem.getName() + " (read only)", problem, module);
                    else
                        gui.createAttempt(new Attempt(this.problem), this.problem.getName(), problem, module);
                } catch (Exception e) {
                    LibAssign.showExceptionError(e);
                }
            }
        }

        @Override
        public void onError(boolean suggestRetry, ProblemFetchMsg msg) {
            if (suggestRetry)
                cacheProblem(problem, open, readOnly);
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
        }

        @Override
        public void onError(boolean suggestRetry, SubmissionFetchMsg<T> msg) {
            if (suggestRetry)
                fetchSubmission(submission, module, readonly);
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
                Submission sub = new Submission(info.getPid(), userInfo.getUser().uid, message.getSid(), message.getGrade(), info.getName(), message.getSubmittedOn(), message.getStatus(), message.getStatusStr(), info.getModuleName());
                ObservableList<TreeItem<Submission>> subs = null;
                for (TreeItem<Submission> s : problems) {
                    if (s.getValue() instanceof AssignedProblem && s.getValue().getPid() == info.getPid())
                        subs = ((AssignedProblem) s.getValue()).submissions;
                }
                if (subs != null)
                    subs.add((int) subs.stream().filter(i -> i.getValue() instanceof Attempt).count(), new TreeItem<>(sub));
                info.deleteAttempt();
            });
        }

        @Override
        public void onError(boolean suggestRetry, SubmissionCreateMsg<T> msg) {
            if (suggestRetry)
                uploadAttempt(info, msg.getProblem());
            else {
                ArisModule<T> module = ModuleService.getService().getModule(msg.getModuleName());
                if (module != null) {
                    saveAttempt(new Attempt(info), msg.getProblem(), module, false);
                }
            }
        }

        @Override
        public ReentrantLock getLock() {
            return lock;
        }
    }

    public class Submission implements Comparable<Submission> {

        private final int pid;
        private final int uid;
        private final int sid;
        private final SimpleStringProperty name;
        private final SimpleObjectProperty<ZonedDateTime> submittedOn;
        private final SimpleStringProperty submittedOnStr = new SimpleStringProperty();
        private final SimpleStringProperty statusStr;
        private final SimpleObjectProperty<Node> controlNode;
        private final SimpleObjectProperty<GradingStatus> status;
        private final SimpleDoubleProperty grade;
        private final String moduleName;

        public Submission(int pid, int uid, int sid, double grade, String name, ZonedDateTime submittedOn, GradingStatus status, String statusStr, String moduleName) {
            this.pid = pid;
            this.uid = uid;
            this.sid = sid;
            this.name = new SimpleStringProperty(name);
            this.submittedOn = new SimpleObjectProperty<>(submittedOn);
            this.status = new SimpleObjectProperty<>(status);
            this.moduleName = moduleName;
            this.submittedOnStr.bind(Bindings.createStringBinding(() -> this.submittedOn.get() == null ? "Never" : AssignGui.DATE_FORMAT.format(new Date(NetUtil.UTCToMilli(this.submittedOn.get()))), this.submittedOn));
            this.statusStr = new SimpleStringProperty(statusStr + " (" + (Math.round(grade * 100.0) / 100.0) + "/" + "1.0)");
            this.grade = new SimpleDoubleProperty(grade);
            HBox box = new HBox(5);
            Button view = new Button("View");
            view.setOnAction(e -> viewSubmission(true));
            box.getChildren().add(view);
            if (!isInstructor) {
                Button reattempt = new Button("Reattempt");
                reattempt.setOnAction(e -> viewSubmission(false));
                box.getChildren().add(reattempt);
            }
            box.setAlignment(Pos.CENTER);
            controlNode = new SimpleObjectProperty<>(box);
        }

        public Submission(MsgUtil.SubmissionInfo info, String moduleName) {
            this(info.pid, info.uid, info.sid, info.grade, null, info.submissionTime, info.status, info.statusStr, moduleName);
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

        public SimpleDoubleProperty gradeProperty() {
            return grade;
        }

        public double getGrade() {
            return grade.get();
        }

        public void updateInfo(MsgUtil.SubmissionInfo info, TreeItem<Submission> thisItem) {
            grade.set(info.grade);
            submittedOn.set(info.submissionTime);
            status.set(info.status);
            statusStr.set(info.statusStr + " (" + (Math.round(info.grade * 100.0) / 100.0) + "/" + "1.0)");
            TreeItem<Submission> parent = thisItem.getParent();
            if (parent != null && parent.getValue() instanceof AssignedProblem)
                updateProblemStatus(parent.getValue(), ((AssignedProblem) parent.getValue()).submissions);
        }

        public int getUid() {
            return uid;
        }
    }

    public class AssignedProblem extends Submission implements Comparable<Submission> {

        private final ObservableList<TreeItem<Submission>> submissions = FXCollections.observableArrayList();
        private final String problemHash;

        public AssignedProblem(int pid, String name, String status, String moduleName, String problemHash) {
            super(pid, -1, -1, 0, name, null, GradingStatus.NONE, status, moduleName);
            this.problemHash = problemHash;
            Button btn = new Button(isInstructor ? "View Problem" : "Start Attempt");
            controlNodeProperty().set(btn);
            btn.setOnAction(e -> {
                if (isInstructor)
                    viewProblem();
                else
                    createPushed();
            });
            statusStrProperty().bind(Bindings.createStringBinding(() -> {
                String grade = " (" + Math.round(getGrade() * 100.0) / 100.0 + "/1.0)";
                switch (getStatusProperty().get()) {
                    case GRADING:
                        return "Grading problem" + grade;
                    case CORRECT:
                        return "Correct" + grade;
                    case INCORRECT:
                        return "Incorrect" + grade;
                    case PARTIAL:
                        return "Partial Credit" + grade;
                    case ERROR:
                        return "Error";
                    case NONE:
                        return "No Submissions" + grade;
                }
                return null;
            }, getStatusProperty(), gradeProperty()));
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
            cacheProblem(this, true, false);
        }

        private <T extends ArisModule> void viewProblem() {
            ArisModule<T> module = ModuleService.getService().getModule(super.moduleName);
            if (module == null) {
                AssignClient.displayErrorMsg("Missing Module", "Client is missing \"" + super.moduleName + "\" module");
                return;
            }
            cacheProblem(this, true, true);
        }

        @Override
        public int compareTo(@NotNull Submission o) {
            if (o instanceof AssignedProblem)
                return getName().compareTo(o.getName());
            return super.compareTo(o);
        }
    }

    public class Student extends Submission implements Comparable<Submission> {

        private final ObservableList<TreeItem<Submission>> problems = FXCollections.observableArrayList();

        public Student(int uid, String name, String statusStr, String moduleName) {
            super(-1, uid, -1, 0, name, null, GradingStatus.NONE, statusStr, moduleName);
            super.controlNode.set(null);
            this.statusStrProperty().bind(Bindings.createStringBinding(() -> {
                String status;
                switch (getStatusProperty().get()) {
                    case CORRECT:
                        status = "Complete";
                        break;
                    case INCORRECT:
                        status = "Incomplete";
                        break;
                    case PARTIAL:
                        status = "Partially Complete";
                        break;
                    case GRADING:
                        status = "Grading";
                        break;
                    case ERROR:
                        status = "An error has occurred";
                        break;
                    default:
                        status = "Unknown";
                }
                return status + " (" + (Math.round(getGrade() * 100.0) / 100.0) + "/" + problems.size() + ".0)";
            }, gradeProperty(), getStatusProperty()));
        }

        @Override
        public int compareTo(@NotNull Submission o) {
            if (o instanceof Student)
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
            ArisModule<T> module = ModuleService.getService().getModule(getModuleName());
            if (module == null) {
                AssignClient.displayErrorMsg("Missing Module", "Client is missing \"" + getModuleName() + "\" module");
                return;
            }
            OfflineDB.submit(connection -> {
                if (connection != null) {
                    Problem<T> problem = null;
                    try (PreparedStatement stmt = connection.prepareStatement("SELECT data FROM attempts WHERE aid=? AND cid=? AND pid=? and created_time=?;")) {
                        stmt.setInt(1, aid);
                        stmt.setInt(2, cid);
                        stmt.setInt(3, getPid());
                        stmt.setString(4, getSubmittedOnDate().toString());
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (!rs.next())
                                throw new SQLException("Attempt does not exist in database");
                            problem = module.getProblemConverter().loadProblem(rs.getBinaryStream(1), true);
                        }
                    } catch (Exception e) {
                        log.error("Failed to load attempt from database", e);
                        AssignClient.displayErrorMsg("Upload Failed", "An error occurred while trying to load the attempt");
                    }
                    if (problem != null) {
                        Problem<T> finalProblem = problem;
                        Platform.runLater(() -> SingleAssignment.this.uploadAttempt(this, finalProblem));
                    }
                } else {
                    log.error("Upload failed: No connection available to offline database");
                    AssignClient.displayErrorMsg("Upload Failed", "An error occurred while trying to load the attempt");
                }
            });
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
            OfflineDB.submit(connection -> {
                if (connection != null) {
                    try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM attempts WHERE aid=? AND cid=? AND pid=? and created_time=?;")) {
                        stmt.setInt(1, aid);
                        stmt.setInt(2, cid);
                        stmt.setInt(3, getPid());
                        stmt.setTimestamp(4, Timestamp.from(getSubmittedOnDate().toInstant()));
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
                }
            });
        }

        private <T extends ArisModule> void editAttempt() {
            ArisModule<T> module = ModuleService.getService().getModule(getModuleName());
            if (module == null) {
                AssignClient.displayErrorMsg("Missing Module", "Client is missing \"" + getModuleName() + "\" module");
                return;
            }
            OfflineDB.submit(connection -> {
                if (connection != null) {
                    Problem<T> problem = null;
                    try (PreparedStatement stmt = connection.prepareStatement("SELECT data FROM attempts WHERE aid=? AND cid=? AND pid=? and created_time=?;")) {
                        stmt.setInt(1, aid);
                        stmt.setInt(2, cid);
                        stmt.setInt(3, getPid());
                        stmt.setTimestamp(4, Timestamp.from(getSubmittedOnDate().toInstant()));
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (!rs.next())
                                throw new SQLException("Attempt does not exist in database");
                            problem = module.getProblemConverter().loadProblem(rs.getBinaryStream(1), true);
                        }
                    } catch (Exception e) {
                        log.error("Failed to load attempt from database", e);
                        AssignClient.displayErrorMsg("Open Failed", "An error occurred while trying to open the attempt");
                    }
                    if (problem != null) {
                        try {
                            gui.createAttempt(this, problemName, problem, module);
                        } catch (Exception e) {
                            LibAssign.showExceptionError(e);
                        }
                    }
                } else {
                    log.error("Edit failed: No connection available to offline database");
                    AssignClient.displayErrorMsg("Edit Failed", "An error occurred while trying to load the attempt");
                }
            });
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
