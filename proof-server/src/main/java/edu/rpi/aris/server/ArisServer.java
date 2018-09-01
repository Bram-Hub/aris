package edu.rpi.aris.server;

import edu.rpi.aris.LibAris;
import edu.rpi.aris.assign.ArisServerModule;
import edu.rpi.aris.proof.SaveInfoListener;

public class ArisServer implements ArisServerModule<LibAris>, SaveInfoListener {

    private static ArisServer instance;

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
    }
}
