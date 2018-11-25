package edu.rpi.aris.assign.client.handlers;

import edu.rpi.aris.assign.client.ResponseHandler;
import edu.rpi.aris.assign.client.model.CurrentUser;
import edu.rpi.aris.assign.client.model.SingleAssignment;
import edu.rpi.aris.assign.message.SubmissionRefresh;
import javafx.application.Platform;
import javafx.scene.control.TreeItem;

import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

public class SubmissionRefreshHandler implements ResponseHandler<SubmissionRefresh> {

    private final HashMap<Integer, TreeItem<SingleAssignment.Submission>> subs;
    private final ReentrantLock lock;

    public SubmissionRefreshHandler(HashMap<Integer, TreeItem<SingleAssignment.Submission>> subs, ReentrantLock lock) {
        this.subs = subs;
        this.lock = lock;
    }

    @Override
    public void response(SubmissionRefresh message) {
        Platform.runLater(() -> {
            CurrentUser.getInstance().finishLoading();
            message.getInfo().forEach((id, info) -> {
                TreeItem<SingleAssignment.Submission> item = subs.get(id);
                if (item != null)
                    item.getValue().updateInfo(info, item);
            });
        });
    }

    @Override
    public void onError(boolean suggestRetry, SubmissionRefresh msg) {
        if (!suggestRetry)
            SingleAssignment.cancelGradeCheck();
        Platform.runLater(CurrentUser.getInstance()::finishLoading);
    }

    @Override
    public ReentrantLock getLock() {
        return lock;
    }
}
