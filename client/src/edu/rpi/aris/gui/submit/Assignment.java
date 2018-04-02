package edu.rpi.aris.gui.submit;

import edu.rpi.aris.Main;
import edu.rpi.aris.net.GradingStatus;
import edu.rpi.aris.net.NetUtil;
import edu.rpi.aris.net.client.Client;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.TitledPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.*;

public class Assignment {

    private static Logger logger = LogManager.getLogger(Assignment.class);
    private final String assignedBy;
    private final int id, classId;
    private long dueDate;
    private String name;
    private TitledPane titledPane;
    private VBox tableBox = new VBox();
    private SimpleBooleanProperty loaded = new SimpleBooleanProperty(false);
    private ArrayList<AssignmentInfo> rootNodes = new ArrayList<>();

    public Assignment(String name, String dueDate, String assignedBy, int id, int classId) {
        this.name = name;
        try {
            this.dueDate = NetUtil.DATE_FORMAT.parse(dueDate).getTime();
        } catch (ParseException e) {
            logger.error("Failed to parse due date", e);
            this.dueDate = -1;
        }
        this.assignedBy = assignedBy;
        this.id = id;
        this.classId = classId;
        String dateStr = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(this.dueDate));
        titledPane = new TitledPane(name + "\nDue: " + dateStr + "\nAssigned by: " + assignedBy, tableBox);
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
        tableBox.getChildren().add(view);
    }

    private void addChildren(AssignmentInfo rootInfo, TreeItem<AssignmentInfo> rootItem) {
        rootInfo.getChildren().sort(Comparator.naturalOrder());
        for (AssignmentInfo childInfo : rootInfo.getChildren()) {
            TreeItem<AssignmentInfo> childItem = new TreeItem<>(childInfo);
            rootItem.getChildren().add(childItem);
            addChildren(childInfo, childItem);
        }
    }

    private void loadStudent(Client client) throws IOException {
        try {
            client.sendMessage(NetUtil.GET_ASSIGNMENT_DETAIL);
            client.sendMessage(classId + "|" + id);
            String numProofStr = Client.checkError(client.readMessage());
            int numProof = Integer.parseInt(numProofStr);
            HashMap<Integer, String[]> proofs = new HashMap<>();
            for (int i = 0; i < numProof; ++i) {
                String[] split = Client.checkSplit(client.readMessage(), 4);
                int pid = Integer.parseInt(split[0]);
                proofs.put(pid, split);
            }
            String numSubmissionStr = Client.checkError(client.readMessage());
            int numSubmission = Integer.parseInt(numSubmissionStr);
            HashMap<Integer, ArrayList<String[]>> submissions = new HashMap<>();
            for (int i = 0; i < numSubmission; ++i) {
                String[] split = Client.checkSplit(client.readMessage(), 5);
                int pid = Integer.parseInt(split[1]);
                submissions.computeIfAbsent(pid, id -> new ArrayList<>()).add(split);
            }
            for (Map.Entry<Integer, String[]> e : proofs.entrySet()) {
                String[] proofData = e.getValue();
                int pid = Integer.parseInt(proofData[0]);
                String name = URLDecoder.decode(proofData[1], "UTF-8");
                String createdBy = URLDecoder.decode(proofData[2], "UTF-8");
                long createdOn = NetUtil.DATE_FORMAT.parse(URLDecoder.decode(proofData[3], "UTF-8")).getTime();
                ProofInfo proofInfo = new ProofInfo(pid, name, createdBy, createdOn, false);
                rootNodes.add(proofInfo);
                int i = 0;
                if (submissions.containsKey(e.getKey()))
                    for (String[] sub : submissions.get(e.getKey())) {
                        ++i;
                        long timestamp = NetUtil.DATE_FORMAT.parse(URLDecoder.decode(sub[2], "UTF-8")).getTime();
                        try {
                            int sid = Integer.parseInt(sub[0]);
                            String status = URLDecoder.decode(sub[3], "UTF-8");
                            GradingStatus gradingStatus = GradingStatus.valueOf(URLDecoder.decode(sub[4], "UTF-8"));
                            proofInfo.addChild(new SubmissionInfo(AssignmentWindow.instance.getClientInfo().getUserId(), sid, e.getKey(), classId, id, "Submission " + i, timestamp, status, gradingStatus, false));
                        } catch (IllegalArgumentException e1) {
                            throw new IOException("Invalid short_status", e1);
                        }
                    }
            }
        } catch (ParseException | NumberFormatException e) {
            throw new IOException("Server sent invalid response");
        }
    }

    private void loadInstructor(Client client) throws IOException {
        try {
            client.sendMessage(NetUtil.GET_SUBMISSION_DETAIL);
            client.sendMessage(classId + "|" + id);
            int numStudent = Integer.parseInt(Client.checkError(client.readMessage()));
            HashMap<Integer, UserInfo> users = new HashMap<>();
            for (int i = 0; i < numStudent; ++i) {
                String[] split = Client.checkSplit(client.readMessage(), 2);
                int uid = Integer.parseInt(split[0]);
                UserInfo user = new UserInfo(uid, URLDecoder.decode(split[1], "UTF-8"));
                users.put(uid, user);
                rootNodes.add(user);
            }
            int numProofs = Integer.parseInt(Client.checkError(client.readMessage()));
            // (userId, (proofId, info))
            HashMap<Integer, HashMap<Integer, ProofInfo>> proofMap = new HashMap<>();
            for (int i = 0; i < numProofs; ++i) {
                String[] split = Client.checkSplit(client.readMessage(), 4);
                int pid = Integer.parseInt(split[0]);
                String name = URLDecoder.decode(split[1], "UTF-8");
                String createdBy = URLDecoder.decode(split[2], "UTF-8");
                long timestamp = NetUtil.DATE_FORMAT.parse(URLDecoder.decode(split[3], "UTF-8")).getTime();
                for (UserInfo info : users.values()) {
                    ProofInfo proofInfo = new ProofInfo(pid, name, createdBy, timestamp, true);
                    proofMap.computeIfAbsent(info.getUserId(), uid -> new HashMap<>()).put(pid, proofInfo);
                    info.addChild(proofInfo);
                }
            }
            int numSubmissions = Integer.parseInt(Client.checkError(client.readMessage()));
            for (int i = 0; i < numSubmissions; ++i) {
                String[] split = Client.checkSplit(client.readMessage(), 5);
                int uid = Integer.parseInt(split[0]);
                int sid = Integer.parseInt(split[1]);
                int pid = Integer.parseInt(split[2]);
                long time = NetUtil.DATE_FORMAT.parse(URLDecoder.decode(split[3], "UTF-8")).getTime();
                String statusStr = URLDecoder.decode(split[4], "UTF-8");
                GradingStatus gradingStatus = null;
                try {
                    gradingStatus = GradingStatus.valueOf(URLDecoder.decode(split[5], "UTF-8"));
                } catch (IllegalArgumentException e) {
                    throw new IOException("Server sent invalid grading status", e);
                }
                UserInfo user = users.computeIfAbsent(uid, u -> new UserInfo(u, NetUtil.ERROR));
                if (user.getName().equals(NetUtil.ERROR))
                    continue;
                ProofInfo proofInfo = proofMap.get(uid).get(pid);
                if (proofInfo == null)
                    continue;
                SubmissionInfo submissionInfo = new SubmissionInfo(uid, sid, pid, classId, id, "Submission " + (proofInfo.getChildren().size() + 1), time, statusStr, gradingStatus, true);
                proofInfo.addChild(submissionInfo);
            }
        } catch (ParseException | NumberFormatException e) {
            throw new IOException("Server sent invalid response");
        }
    }

    public String getName() {
        return name;
    }

    public TitledPane getPane() {
        return titledPane;
    }
}
