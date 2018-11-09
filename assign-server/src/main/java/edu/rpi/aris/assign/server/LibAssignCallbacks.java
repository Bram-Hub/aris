package edu.rpi.aris.assign.server;

import edu.rpi.aris.assign.ServerCallbacks;

public class LibAssignCallbacks extends ServerCallbacks {

    @Override
    public void scheduleForGrading(int submissionId) {
        Grader.getInstance().addToGradeQueue(submissionId);
    }

    @Override
    public long getMaxSubmissionSize() {
        return AssignServerMain.getServer().getConfig().getMaxSubmissionSize();
    }

}
