package edu.rpi.aris.assign.client.guiold;

import edu.rpi.aris.assign.client.Client;
import edu.rpi.aris.assign.message.MsgUtil;
import edu.rpi.aris.assign.message.ProblemsGetMsg;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class ProblemList {

    private static final Logger logger = LogManager.getLogger(ProblemList.class);
    private SimpleBooleanProperty loaded = new SimpleBooleanProperty(false);
    private ObservableList<ProblemInfo> proofs = FXCollections.observableArrayList();
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
            Client client = Client.getInstance();
            try {
                client.connect();
                ProblemsGetMsg msg = (ProblemsGetMsg) new ProblemsGetMsg().sendAndGet(client);
                if (msg == null)
                    return;
                for (MsgUtil.ProblemInfo proof : msg.getProblems())
                    Platform.runLater(() -> proofs.add(new ProblemInfo(proof, true)));
                Platform.runLater(() -> loaded.set(true));
            } catch (Exception e) {
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

    public ObservableList<ProblemInfo> getProofs() {
        return proofs;
    }

    public void remove(ProblemInfo proof) {
        proofs.remove(proof);
    }
}
