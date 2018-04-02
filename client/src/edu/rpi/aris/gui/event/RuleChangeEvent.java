package edu.rpi.aris.gui.event;

import edu.rpi.aris.gui.MainWindow;
import edu.rpi.aris.proof.Line;
import edu.rpi.aris.rules.RuleList;

public class RuleChangeEvent extends HistoryEvent {

    private int lineNum;
    private RuleList oldRule, newRule;

    public RuleChangeEvent(int lineNum, RuleList oldRule, RuleList newRule) {
        this.lineNum = lineNum;
        this.oldRule = oldRule;
        this.newRule = newRule;
    }

    @Override
    public void undoEvent(MainWindow window) {
        Line l = window.getProof().getLine(lineNum);
        if (l != null) {
            l.setSelectedRule(oldRule);
            window.requestFocus(window.getProofLines().get(lineNum));
        }
    }

    @Override
    public void redoEvent(MainWindow window) {
        Line l = window.getProof().getLine(lineNum);
        if (l != null) {
            l.setSelectedRule(newRule);
            window.requestFocus(window.getProofLines().get(lineNum));
        }
    }
}
