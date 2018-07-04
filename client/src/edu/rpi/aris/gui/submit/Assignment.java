package edu.rpi.aris.gui.submit;

import edu.rpi.aris.Main;
import edu.rpi.aris.net.NetUtil;
import edu.rpi.aris.net.client.Client;
import edu.rpi.aris.net.message.*;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.text.DateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

public class Assignment {

    private static Logger logger = LogManager.getLogger(Assignment.class);
    private final int id, classId;
    private final Course course;
    private SimpleLongProperty dueDate = new SimpleLongProperty();
    private SimpleStringProperty name = new SimpleStringProperty();
    private TitledPane titledPane;
    private VBox tableBox = new VBox(5);
    private SimpleBooleanProperty loaded = new SimpleBooleanProperty(false);
    private ArrayList<AssignmentInfo> rootNodes = new ArrayList<>();
    private HashSet<ProofInfo> proofs = new HashSet<>();

    public Assignment(String name, long dueDate, String assignedBy, int id, int classId, Course course) {
        this.course = course;
        this.name.set(name);
        this.dueDate.set(dueDate);
        this.id = id;
        this.classId = classId;
        titledPane = new TitledPane("", tableBox);
        titledPane.textProperty().bind(Bindings.createStringBinding(() -> {
            String dateStr = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(Assignment.this.dueDate.get()));
            return Assignment.this.name.get() + "\nDue: " + dateStr + "\nAssigned by: " + assignedBy;
        }, this.name, this.dueDate));
        titledPane.expandedProperty().addListener((observableValue, oldVal, newVal) -> {
            if (newVal)
                load(false);
        });
    }

    public void load(boolean reload) {
        if (reload)
            loaded.set(false);
        if (loaded.get())
            return;
        rootNodes.clear();
        tableBox.getChildren().clear();
        new Thread(() -> {
            Client client = Main.getClient();
            try {
                client.connect();
                if (AssignmentWindow.instance.getClientInfo().isInstructorProperty().get())
                    loadInstructor(client);
                else
                    loadStudent(client);
                Platform.runLater(() -> {
                    loaded.set(true);
                    buildUI();
                });
            } catch (IOException e) {
                Platform.runLater(() -> {
                    rootNodes.clear();
                    tableBox.getChildren().clear();
                    loaded.set(false);
                });
                System.out.println("Connection failed");
                e.printStackTrace();
                //TODO: show error to user
            } finally {
                client.disconnect();
            }
        }).start();
    }

    private void buildUI() {
        TreeItem<AssignmentInfo> root = new TreeItem<>(null);
        TreeTableView<AssignmentInfo> view = new TreeTableView<>(root);
        boolean columnsAdded = false;
        for (AssignmentInfo rootInfo : rootNodes) {
            TreeItem<AssignmentInfo> proof = new TreeItem<>(rootInfo);
            addChildren(rootInfo, proof);
            if (!columnsAdded) {
                for (int i = 0; i < rootInfo.getNumColumns(); ++i) {
                    TreeTableColumn<AssignmentInfo, Object> column = new TreeTableColumn<>(rootInfo.getColumnName(i));
                    final int columnNum = i;
                    column.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getValue().getColumnData(columnNum)));
                    column.setSortable(false);
                    column.setStyle(i == 0 ? "-fx-alignment: CENTER_LEFT;" : "-fx-alignment: CENTER;");
                    view.getColumns().add(column);
                }
                columnsAdded = true;
            }
            root.getChildren().add(proof);
        }
        root.getChildren().sort(Comparator.comparing(TreeItem::getValue));
        view.setShowRoot(false);
        view.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);
        view.setEditable(false);
        root.setExpanded(true);
        if (AssignmentWindow.instance.getClientInfo().isInstructorProperty().get()) {
            Button editAssignment = new Button("Edit Assignment");
            Button deleteAssignment = new Button("Delete Assignment");
            editAssignment.setOnAction(action -> editAssignment());
            deleteAssignment.setOnAction(action -> deleteAssignment());
            HBox box = new HBox(5);
            box.getChildren().addAll(editAssignment, deleteAssignment);
            tableBox.getChildren().add(box);
        }
        tableBox.getChildren().add(view);
    }

    private void deleteAssignment() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.initModality(Modality.WINDOW_MODAL);
        alert.initOwner(AssignmentWindow.instance.getStage());
        alert.setTitle("Delete Assignment?");
        alert.setHeaderText("Are you sure you want to delete this assignment?");
        alert.setContentText("This will also delete any student submissions for this assignment");
        alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
        Optional<ButtonType> result = alert.showAndWait();
        result.ifPresent(type -> {
            if (type != ButtonType.YES)
                return;
            new Thread(() -> {
                Client client = Main.getClient();
                try {
                    client.connect();
                    AssignmentDeleteMsg msg = (AssignmentDeleteMsg) new AssignmentDeleteMsg(classId, id).sendAndGet(client);
                    if (msg == null)
                        return;
                    AssignmentWindow.instance.loadAssignments(course, true);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    client.disconnect();
                }
            }).start();
        });
    }

    private void editAssignment() {
        ProofList proofList = AssignmentWindow.instance.getProofList();
        proofList.load(false);
        try {
            AssignmentDialog dialog = new AssignmentDialog(AssignmentWindow.instance.getStage(), proofList, name.get(), new Date(dueDate.get()), proofs);
            Optional<Triple<String, LocalDateTime, Collection<ProofInfo>>> result = dialog.showAndWait();
            if (!result.isPresent())
                return;
            Triple<String, LocalDateTime, Collection<ProofInfo>> info = result.get();
            String newName = info.getLeft().equals(name.get()) ? null : info.getLeft();
            ZonedDateTime newDueDate = info.getMiddle().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() == dueDate.get() ? null : NetUtil.localToUTC(info.getMiddle());
            Collection<ProofInfo> newProofs = info.getRight();
            Set<ProofInfo> remove = new HashSet<>();
            Set<ProofInfo> add = new HashSet<>();
            for (ProofInfo p : newProofs)
                if (!proofs.contains(p))
                    add.add(p);
            for (ProofInfo p : proofs)
                if (!newProofs.contains(p))
                    remove.add(p);
            if (newName != null || newDueDate != null || remove.size() > 0 || add.size() > 0) {
                new Thread(() -> {
                    Client client = Main.getClient();
                    try {
                        client.connect();
                        AssignmentEditMsg msg = new AssignmentEditMsg(classId, id);
                        msg.setName(newName);
                        msg.setNewDueDate(newDueDate);
                        for (ProofInfo p : remove)
                            msg.removeProblem(p.getProofId());
                        for (ProofInfo p : add)
                            msg.addProblem(p.getProofId());
                        msg = (AssignmentEditMsg) msg.sendAndGet(client);
                        if(msg == null)
                            return;
                        Platform.runLater(() -> {
                            if (newName != null)
                                name.set(newName);
                            if (newDueDate != null)
                                dueDate.set(newDueDate.toInstant().toEpochMilli());
                            load(true);
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        client.disconnect();
                    }
                }).start();
            }
        } catch (IOException e) {
            logger.error("Failed to show assignment dialog", e);
        }
    }

    private void addChildren(AssignmentInfo rootInfo, TreeItem<AssignmentInfo> rootItem) {
        if (rootInfo.getChildren() != null) {
            rootInfo.getChildren().sort(Comparator.naturalOrder());
            for (AssignmentInfo childInfo : rootInfo.getChildren()) {
                TreeItem<AssignmentInfo> childItem = new TreeItem<>(childInfo);
                rootItem.getChildren().add(childItem);
                addChildren(childInfo, childItem);
            }
        }
    }

    private void loadStudent(Client client) throws IOException {
        SubmissionGetStudentMsg reply = (SubmissionGetStudentMsg) new SubmissionGetStudentMsg(id, classId).sendAndGet(client);
        if (reply == null)
            return;
        for (MsgUtil.ProblemInfo info : reply.getAssignedProblems()) {
            ProofInfo proofInfo = new ProofInfo(info, false);
            rootNodes.add(proofInfo);
            int i = 0;
            for (MsgUtil.SubmissionInfo sInfo : reply.getSubmissions().get(info.pid)) {
                ++i;
                proofInfo.addChild(new SubmissionInfo(sInfo, "Submission " + i, false));
            }
        }
    }

    private void loadInstructor(Client client) throws IOException {
        SubmissionGetInstructorMsg reply = (SubmissionGetInstructorMsg) new SubmissionGetInstructorMsg(id, classId).sendAndGet(client);
        if (reply == null)
            return;
        boolean doneProofs = false;
        for (Map.Entry<Integer, String> u : reply.getUsers().entrySet()) {
            UserInfo user = new UserInfo(u.getKey(), u.getValue());
            rootNodes.add(user);
            for (MsgUtil.ProblemInfo pInfo : reply.getAssignedProblems()) {
                if(!pInfo.checkValid())
                    continue;
                if (!doneProofs)
                    proofs.add(new ProofInfo(pInfo, true));
                ProofInfo proof = new ProofInfo(pInfo, true);
                user.addChild(proof);
                int i = 0;
                for (MsgUtil.SubmissionInfo sInfo : reply.getSubmissions().get(user.getUserId()).get(pInfo.pid)) {
                    ++i;
                    proof.addChild(new SubmissionInfo(sInfo, "Submission " + i, true));
                }
            }
            doneProofs = true;
        }
    }

    public String getName() {
        return name.get();
    }

    public TitledPane getPane() {
        return titledPane;
    }
}
