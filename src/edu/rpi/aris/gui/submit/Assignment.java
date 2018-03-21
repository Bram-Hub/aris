package edu.rpi.aris.gui.submit;

import edu.rpi.aris.Main;
import edu.rpi.aris.net.NetUtil;
import edu.rpi.aris.net.client.Client;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
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
import java.util.stream.Collectors;

public class Assignment {

    private static Logger logger = LogManager.getLogger(Assignment.class);
    private final String assignedBy;
    private final int id, classId;
    private long dueDate;
    private String name;
    private TitledPane titledPane;
    private VBox tableBox = new VBox();
    private SimpleBooleanProperty loaded = new SimpleBooleanProperty(false);
    private ObservableMap<AssignmentInfo, ArrayList<AssignmentInfo>> proofs = FXCollections.observableHashMap();

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
        proofs.clear();
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
                    proofs.clear();
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
        for (Map.Entry<AssignmentInfo, ArrayList<AssignmentInfo>> entry : proofs.entrySet()) {
            AssignmentInfo rootInfo = entry.getKey();
            TreeItem<AssignmentInfo> proof = new TreeItem<>(rootInfo);
            proof.getChildren().addAll(entry.getValue().stream().map(TreeItem::new).collect(Collectors.toList()));
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

    private void loadStudent(Client client) throws IOException {
        try {
            client.sendMessage(NetUtil.GET_ASSIGNMENT_DETAIL);
            client.sendMessage(classId + "|" + id);
            String numProofStr = client.readMessage();
            if (numProofStr.startsWith(NetUtil.ERROR) || numProofStr.startsWith(NetUtil.INVALID))
                throw new IOException(numProofStr);
            int numProof = Integer.parseInt(numProofStr);
            HashMap<Integer, String[]> proofs = new HashMap<>();
            for (int i = 0; i < numProof; ++i) {
                String[] split = client.readMessage().split("\\|");
                if (split.length != 2)
                    throw new IOException("Server sent invalid response");
                int pid = Integer.parseInt(split[0]);
                proofs.put(pid, split);
            }
            String numSubmissionStr = client.readMessage();
            if (numSubmissionStr.startsWith(NetUtil.ERROR) || numSubmissionStr.startsWith(NetUtil.INVALID))
                throw new IOException(numSubmissionStr);
            int numSubmission = Integer.parseInt(numSubmissionStr);
            HashMap<Integer, ArrayList<String[]>> submissions = new HashMap<>();
            for (int i = 0; i < numSubmission; ++i) {
                String[] split = client.readMessage().split("\\|");
                if (split.length != 4)
                    throw new IOException("Server sent invalid response");
                int pid = Integer.parseInt(split[1]);
                submissions.computeIfAbsent(pid, id -> new ArrayList<>()).add(split);
            }
            for (Map.Entry<Integer, String[]> e : proofs.entrySet()) {
                String[] proofData = e.getValue();
                StudentInfo proofInfo = new StudentInfo(-1, Integer.parseInt(proofData[0]), classId, id, URLDecoder.decode(proofData[1], "UTF-8"), -1, null);
                ArrayList<AssignmentInfo> subs = this.proofs.compute(proofInfo, (i, j) -> new ArrayList<>());
                int i = 0;
                if (submissions.containsKey(e.getKey()))
                    for (String[] sub : submissions.get(e.getKey())) {
                        ++i;
                        long timestamp = NetUtil.DATE_FORMAT.parse(URLDecoder.decode(sub[2], "UTF-8")).getTime();
                        subs.add(new StudentInfo(Integer.parseInt(sub[0]), e.getKey(), classId, id, "Submission " + i, timestamp, URLDecoder.decode(sub[3], "UTF-8")));
                    }
                if (subs.size() > 0) {
                    subs.sort(Collections.reverseOrder());
                    proofInfo.setStatus(((StudentInfo) subs.get(0)).getStatus());
                    proofInfo.setTimestamp(((StudentInfo) subs.get(0)).getTimestamp());
                } else
                    proofInfo.setStatus(NetUtil.STATUS_NO_SUBMISSION);
            }
        } catch (ParseException | NumberFormatException e) {
            throw new IOException("Server sent invalid response");
        }
    }

    private void loadInstructor(Client client) throws IOException {
    }

    public String getName() {
        return name;
    }

    public TitledPane getPane() {
        return titledPane;
    }
}
