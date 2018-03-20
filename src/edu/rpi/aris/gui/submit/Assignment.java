package edu.rpi.aris.gui.submit;

import edu.rpi.aris.Main;
import edu.rpi.aris.net.NetUtil;
import edu.rpi.aris.net.client.Client;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.TitledPane;
import javafx.scene.control.TreeView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;

public class Assignment {

    private static Logger logger = LogManager.getLogger(Assignment.class);
    private final String assignedBy;
    private final int id, classId;
    private long dueDate;
    private String name;
    private TitledPane titledPane;
    private SimpleBooleanProperty loaded = new SimpleBooleanProperty(false);
    private ObservableList<ProofInfo> proofs = FXCollections.observableArrayList();
    private TreeView<String> treeView;

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
        titledPane = new TitledPane(name + "\nDue: " + dateStr + "\nAssigned by: " + assignedBy, null);
        Node n = titledPane.lookup("");
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
        new Thread(() -> {
            Client client = Main.getClient();
            try {
                client.connect();
                client.sendMessage(NetUtil.GET_PROOFS);
                client.sendMessage(classId + "|" + id);
                String res;
                while ((res = client.readMessage()) != null && !res.equals(NetUtil.DONE) && !res.equals(NetUtil.ERROR)) {
                    String[] split = res.split("\\|");
                    if (split.length != 3)
                        throw new IOException("Server sent invalid response");
                    int id;
                    try {
                        id = Integer.parseInt(split[0]);
                    } catch (NumberFormatException e) {
                        throw new IOException("Server sent invalid response");
                    }
                    String name = URLDecoder.decode(split[1], "UTF-8");
                    String createdBy = URLDecoder.decode(split[2], "UTF-8");
                    Platform.runLater(() -> proofs.add(new ProofInfo(id, classId, this.id, name, createdBy)));
                }
                if (res == null || res.equals(NetUtil.ERROR))
                    throw new IOException("Error fetching assignment proofs");
                Platform.runLater(() -> loaded.set(true));
            } catch (IOException e) {
                Platform.runLater(() -> {
                    proofs.clear();
                    loaded.set(false);
                });
                System.out.println("Connection failed");
                //TODO: show error to user
            } finally {
                client.disconnect();
            }
        }).start();
    }

    public String getName() {
        return name;
    }

    public TitledPane getPane() {
        return titledPane;
    }
}
