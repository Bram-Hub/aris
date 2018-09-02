package edu.rpi.aris.assign;

public interface ModuleUIListener {

    boolean guiCloseRequest(boolean hasUnsavedChanges);

    void guiClosed();

    void saveProblemLocally();

    void uploadProblem();

}
