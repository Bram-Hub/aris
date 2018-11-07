package edu.rpi.aris.server;

import edu.rpi.aris.LibAris;
import edu.rpi.aris.assign.ArisServerModule;
import edu.rpi.aris.assign.AutoGrader;
import edu.rpi.aris.proof.SaveInfoListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ArisServer implements ArisServerModule<LibAris>, SaveInfoListener {

    private static final Logger log = LogManager.getLogger();
    private static ArisServer instance;
    private final ArisGrader grader = new ArisGrader();

    private ArisServer() {
    }

    public static ArisServer getInstance() {
        if (instance == null)
            instance = new ArisServer();
        return instance;
    }

    @Override
    public boolean notArisFile(String filename, String programName, String programVersion) {
        return false;
    }

    @Override
    public void integrityCheckFailed(String filename) {
        log.error("INTEGRITY CHECK FAILED: " + filename);
    }

    @Override
    public AutoGrader<LibAris> getAutoGrader() {
        return grader;
    }
}
