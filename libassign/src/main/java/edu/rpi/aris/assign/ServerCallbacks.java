package edu.rpi.aris.assign;

public abstract class ServerCallbacks {

    private static ServerCallbacks instance = new ServerCallbacks() {
        @Override
        public void scheduleForGrading(int submissionId) {
        }

        @Override
        public long getMaxSubmissionSize() {
            return -1;
        }
    };

    public static void setServerCallbacks(ServerCallbacks callbacks) {
        if (callbacks == null)
            return;
        instance = callbacks;
    }

    public static ServerCallbacks getInstance() {
        return instance;
    }

    public abstract void scheduleForGrading(int submissionId);

    public abstract long getMaxSubmissionSize();

}
