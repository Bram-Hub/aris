package edu.rpi.aris.assign;

public interface ModuleUIListener {

    boolean guiCloseRequest(boolean hasUnsavedChanges);

    void guiClosed();

    boolean saveProblemLocally();

    void uploadProblem();

}
