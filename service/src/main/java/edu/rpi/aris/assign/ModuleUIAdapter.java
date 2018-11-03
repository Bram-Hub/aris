package edu.rpi.aris.assign;

public class ModuleUIAdapter implements ModuleUIListener {

    @Override
    public boolean guiCloseRequest(boolean hasUnsavedChanges) {
        return true;
    }

    @Override
    public void guiClosed() {
    }

    @Override
    public boolean saveProblemLocally() {
        return false;
    }

    @Override
    public void uploadProblem() {
    }
}
