package edu.rpi.aris.gui.event;

import edu.rpi.aris.gui.GoalLine;
import edu.rpi.aris.gui.MainWindow;
import edu.rpi.aris.gui.ProofLine;

public class SentenceChangeEvent extends HistoryEvent {

    private final int lineNum;
    private final String oldVal;
    private final String newVal;

    public SentenceChangeEvent(int lineNum, String oldVal, String newVal) {
        this.lineNum = lineNum;
        this.oldVal = oldVal;
        this.newVal = newVal;
    }

    private void setString(MainWindow window, String str) {
        if (lineNum >= 0) {
            ProofLine line = window.getProofLines().get(lineNum);
            line.setText(str);
            line.resetLastString();
            window.requestFocus(line);
        } else if (lineNum < -1) {
            int goalNum = lineNum * -1 - 2;
            GoalLine goal = window.getGoalLines().get(goalNum);
            goal.setText(str);
            goal.resetLastString();
            window.requestFocus(goal);
        }
    }

    @Override
    public void undoEvent(MainWindow window) {
        setString(window, oldVal);
    }

    @Override
    public void redoEvent(MainWindow window) {
        setString(window, newVal);
    }
}
