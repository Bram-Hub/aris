package edu.rpi.aris.gui.event;

import edu.rpi.aris.gui.MainWindow;
import edu.rpi.aris.gui.Proof;

public class PremiseChangeEvent extends HistoryEvent {

    private final int selectedLineIndex;
    private final int premiseLineIndex;
    private final boolean wasSelected;

    public PremiseChangeEvent(int selectedLineIndex, int premiseLineIndex, boolean wasSelected) {
        this.selectedLineIndex = selectedLineIndex;
        this.premiseLineIndex = premiseLineIndex;
        this.wasSelected = wasSelected;
    }

    @Override
    public void undoEvent(MainWindow window) {
        Proof.Line premiseLine = window.getProof().getLines().get(premiseLineIndex);
        window.getProof().setPremise(selectedLineIndex, premiseLine, wasSelected);
        window.requestFocus(window.getProofLines().get(selectedLineIndex));
        window.updateHighlighting(selectedLineIndex);
    }

    @Override
    public void redoEvent(MainWindow window) {
        Proof.Line premiseLine = window.getProof().getLines().get(premiseLineIndex);
        window.getProof().setPremise(selectedLineIndex, premiseLine, !wasSelected);
        window.requestFocus(window.getProofLines().get(selectedLineIndex));
        window.updateHighlighting(selectedLineIndex);
    }

}
