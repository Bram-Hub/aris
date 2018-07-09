package edu.rpi.aris.proof;

public interface SaveInfoListener {

    boolean notArisFile(String filename, String programName, String programVersion);

    void integrityCheckFailed(String filename);

}
