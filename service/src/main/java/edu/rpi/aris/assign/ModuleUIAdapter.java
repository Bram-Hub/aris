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
    public void saveProblemLocally() {
    }

    @Override
    public void uploadProblem() {
    }
}
