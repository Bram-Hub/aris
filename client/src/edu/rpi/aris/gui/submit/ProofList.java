package edu.rpi.aris.gui.submit;

import edu.rpi.aris.Main;
import edu.rpi.aris.net.client.Client;
import edu.rpi.aris.net.message.ProblemsGetMsg;
import edu.rpi.aris.net.message.MsgUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

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
                ProblemsGetMsg msg = (ProblemsGetMsg) new ProblemsGetMsg().sendAndGet(client);
                if (msg == null)
                    return;
                for (MsgUtil.ProblemInfo proof : msg.getProblems())
                    Platform.runLater(() -> proofs.add(new ProofInfo(proof, true)));
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
