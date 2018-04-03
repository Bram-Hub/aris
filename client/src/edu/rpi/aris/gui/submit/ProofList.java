package edu.rpi.aris.gui.submit;

import edu.rpi.aris.Main;
import edu.rpi.aris.net.NetUtil;
import edu.rpi.aris.net.client.Client;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URLDecoder;
import java.text.ParseException;

public class ProofList {

    private static final Logger logger = LogManager.getLogger(ProofList.class);
    private SimpleBooleanProperty loaded = new SimpleBooleanProperty(false);
    private ObservableList<ProofInfo> proofs = FXCollections.observableArrayList();
    private boolean listenerAdded = false;

    private synchronized void addListener() {
        if (listenerAdded)
            return;
        AssignmentWindow.instance.getClientInfo().loadedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) {
                loaded.set(false);
                proofs.clear();
            }
        });
        listenerAdded = true;
    }

    public void load(boolean reload) {
        addListener();
        if (reload)
            loaded.set(false);
        if (loaded.get() || !AssignmentWindow.instance.getClientInfo().isInstructorProperty().get())
            return;
        proofs.clear();
        new Thread(() -> {
            Client client = Main.getClient();
            try {
                client.connect();
                client.sendMessage(NetUtil.GET_PROOFS);
                String proofInfo;
                while (!(proofInfo = Client.checkError(client.readMessage())).equals(NetUtil.DONE)) {
                    String[] split = Client.checkSplit(proofInfo, 4);
                    int pid;
                    try {
                        pid = Integer.parseInt(split[0]);
                    } catch (NumberFormatException e) {
                        throw new IOException("Server sent invalid proof id", e);
                    }
                    String name = URLDecoder.decode(split[1], "UTF-8");
                    String createdBy = URLDecoder.decode(split[2], "UTF-8");
                    long timestamp;
                    try {
                        timestamp = NetUtil.DATE_FORMAT.parse(URLDecoder.decode(split[3], "UTF-8")).getTime();
                    } catch (ParseException e) {
                        logger.error("Failed to parse date string: " + split[3]);
                        timestamp = 0;
                    }
                    long finalTimestamp = timestamp;
                    Platform.runLater(() -> proofs.add(new ProofInfo(pid, name, createdBy, finalTimestamp, true)));
                }
                Platform.runLater(() -> loaded.set(true));
            } catch (IOException e) {
                Platform.runLater(() -> {
                    loaded.set(false);
                    proofs.clear();
                });
                System.out.println("Connection failed");
                //TODO: show error to user
            } finally {
                client.disconnect();
            }
        }).start();
    }

    public ObservableList<ProofInfo> getProofs() {
        return proofs;
    }

    public void remove(ProofInfo proof) {
        proofs.remove(proof);
    }
}
