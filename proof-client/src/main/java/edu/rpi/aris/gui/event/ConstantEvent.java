package edu.rpi.aris.gui.event;

import edu.rpi.aris.gui.MainWindow;
import edu.rpi.aris.gui.ProofLine;

public class ConstantEvent extends HistoryEvent {

    private final int lineNumber;
    private final String oldValue;
    private final String newValue;

    public ConstantEvent(int lineNumber, String oldValue, String newValue) {
        this.lineNumber = lineNumber;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    private void setConstants(MainWindow window, String constant) {
        if (lineNumber >= 0) {
            ProofLine line = window.getProofLines().get(lineNumber);
            if (line != null)
                line.setConstants(constant);
        }
    }

    @Override
    public void undoEvent(MainWindow window) {
        setConstants(window, oldValue);
    }

    @Override
    public void redoEvent(MainWindow window) {
        setConstants(window, newValue);
    }
}
