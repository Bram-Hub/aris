package edu.rpi.aris.gui;

import edu.rpi.aris.assign.EditMode;

public class CopyManager {

    private MainWindow window;

    public CopyManager(MainWindow window) {
        this.window = window;
    }

    private boolean checkEditMode(ProofLine line) {
        if (window.editMode == EditMode.UNRESTRICTED_EDIT || window.editMode == EditMode.CREATE_EDIT_PROBLEM)
            return true;
        return line != null && (!line.getModel().isAssumption() || line.getModel().getSubProofLevel() != 0);
    }

    public void copy() {
        copy(false);
    }

    private void copy(boolean cut) {
        int lineNum = window.selectedLineProperty().get();
        if (lineNum >= 0) {
            ProofLine line = window.getProofLines().get(lineNum);
            if (cut && checkEditMode(line))
                line.cut();
            else
                line.copy();
        } else if (lineNum < -1) {
            GoalLine line = window.getGoalLines().get(lineNum * -1 - 2);
            if (cut && checkEditMode(null))
                line.cut();
            else
                line.copy();
        }

    }

    public void cut() {
        copy(true);
    }

    public void paste() {
        int lineNum = window.selectedLineProperty().get();
        if (lineNum >= 0) {
            ProofLine line = window.getProofLines().get(lineNum);
            if (checkEditMode(line))
                line.paste();
        } else if (lineNum < -1 && checkEditMode(null)) {
            GoalLine line = window.getGoalLines().get(lineNum * -1 - 2);
            line.paste();
        }
    }

}
