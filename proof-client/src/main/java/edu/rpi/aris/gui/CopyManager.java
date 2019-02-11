package edu.rpi.aris.gui;

import edu.rpi.aris.assign.EditMode;
import javafx.util.Pair;

public class CopyManager {

    private MainWindow window;

    private int start;
    private int end;
    private boolean multilineSelect = false;

    public CopyManager(MainWindow window) {
        this.window = window;
        start = -1;
        end = -1;
    }

    private boolean checkEditMode(LineInterface line) {
        if (window.editMode == EditMode.UNRESTRICTED_EDIT || window.editMode == EditMode.CREATE_EDIT_PROBLEM)
            return true;
        return !line.isGoal() && line.getLineNum() >= window.getProof().getNumPremises();
    }

    public void copy() {
        copy(false);
    }

    private LineInterface getLine(int lineNum) {
        try {
            if (lineNum >= 0) {
                return window.getProofLines().get(lineNum);
            } else if (lineNum < -1) {
                return window.getGoalLines().get(lineNum * -1 - 2);
            }
        } catch (IndexOutOfBoundsException ignored) {
        }
        return null;
    }

    private ProofLine getPLine(int lineNum) {
        LineInterface l = getLine(lineNum);
        if (l instanceof ProofLine)
            return (ProofLine) l;
        return null;
    }

    private int getLvl(ProofLine line) {
        return line.getModel().getSubProofLevel() - (line.isAssumption() ? 1 : 0);
    }

    private LineInterface getLine() {
        return getLine(window.selectedLineProperty().get());
    }

    private void select(int... lines) {
        for (int i : lines) {
            LineInterface line = getLine(i);
            if (line != null)
                line.select();
        }
    }

    private void copy(boolean cut) {
        LineInterface line = getLine();
        if (line == null)
            return;
        if (cut && checkEditMode(line))
            line.cut();
        else
            line.copy();
    }

    private int findSubproofEnd(ProofLine line) {
        if (!line.isAssumption())
            return line.getLineNum();
        int endNum;
        for (endNum = line.getLineNum() + 1; endNum < window.numLines(); ++endNum) {
            ProofLine l = getPLine(endNum);
            if (l == null)
                return line.getLineNum();
            if ((l.isAssumption() && l.getModel().getSubProofLevel() == line.getModel().getSubProofLevel()) || l.getModel().getSubProofLevel() < line.getModel().getSubProofLevel())
                break;
        }
        return endNum - 1;
    }

    private ProofLine findAssumptionForLvl(ProofLine line, int lvl) {
        if (line == null || getLvl(line) == lvl)
            return line;
        ProofLine l = getPLine(line.getLineNum() - 1);
        return findAssumptionForLvl(l, lvl);
    }

    private Pair<Integer, Integer> getCommonLine(int start, int end) {
        if (start < window.getProof().getNumPremises() || end >= window.numLines())
            return null;
        ProofLine startLine = getPLine(start);
        ProofLine endLine = getPLine(end);
        if (startLine == null || endLine == null)
            return null;
        if (getLvl(startLine) == getLvl(endLine)) {
            return endLine.isAssumption() ? new Pair<>(start, findSubproofEnd(endLine)) : new Pair<>(start, end);
        } else {
            if (getLvl(startLine) < getLvl(endLine)) {
                ProofLine endAssumption = findAssumptionForLvl(endLine, getLvl(startLine));
                return new Pair<>(start, findSubproofEnd(endAssumption));
            } else {
                startLine = findAssumptionForLvl(startLine, getLvl(endLine));
                return startLine == null ? null : new Pair<>(startLine.getLineNum(), end);
            }

        }
    }

    private Pair<Integer, Integer> checkCommonParent(int start, int end) {
        ProofLine startLine = getPLine(start);
        if (startLine == null)
            return null;
        int minLvl = getLvl(startLine);
        if (minLvl == 0)
            return new Pair<>(start, end);
        int parentLvl = minLvl;
        ProofLine p;
        for (int i = start + 1; i <= end; ++i) {
            p = getPLine(i);
            if (p == null)
                return null;
            int l;
            if ((l = getLvl(p)) < parentLvl)
                parentLvl = l;
        }
        if (parentLvl == minLvl) {
            return new Pair<>(start, end);
        } else {
            startLine = findAssumptionForLvl(startLine, parentLvl);
            if (startLine == null)
                return null;
            ProofLine endAssumption = findAssumptionForLvl(getPLine(end), parentLvl);
            return endAssumption == null ? null : new Pair<>(startLine.getLineNum(), findSubproofEnd(endAssumption));
        }
    }

    private Pair<Integer, Integer> getSelectableRange(int start, int end) {
        Pair<Integer, Integer> p = getCommonLine(start, end);
        return p == null ? null : checkCommonParent(p.getKey(), p.getValue());
    }

    public void cut() {
        copy(true);
    }

    public void paste() {
        LineInterface line = getLine();
        if (line != null && checkEditMode(line))
            line.paste();
    }

    public void deselect() {
        start = -1;
        end = -1;
        multilineSelect = false;
        window.getProofLines().forEach(ProofLine::deselect);
    }

    public void selectRange(int clickedNum) {
        LineInterface current = getLine();
        LineInterface clicked = getLine(clickedNum);
        if (clicked == null || clicked.isGoal())
            return;
        int rangeStart, rangeEnd;
        if (multilineSelect) {
            rangeStart = Math.min(start, clickedNum);
            rangeEnd = Math.max(end, clickedNum);
        } else {
            int currentNum = current == null || current.isGoal() ? clickedNum : current.getLineNum();
            rangeStart = Math.min(currentNum, clickedNum);
            rangeEnd = Math.max(currentNum, clickedNum);
        }
        Pair<Integer, Integer> range = getSelectableRange(rangeStart, rangeEnd);
        if (range != null) {
            start = range.getKey();
            end = range.getValue();
            multilineSelect = true;
            for (int i = start; i <= end; ++i)
                select(i);
        }
    }

//    public void selectUp() {
//        if (multilineSelect) {
//            if (start > 0) {
//                start--;
//                select(start);
//            }
//        } else {
//            LineInterface line = getLine();
//            if (line == null || line.getLineNum() <= 0 || line.isGoal())
//                return;
//            end = line.getLineNum();
//            start = end - 1;
//            multilineSelect = true;
//            select(end, start);
//        }
//    }
//
//    public void selectDown() {
//        if (multilineSelect) {
//            if (end < window.numLines() - 1) {
//                end++;
//                select(end);
//            }
//        } else {
//            LineInterface line = getLine();
//            if (line == null || line.getLineNum() >= window.numLines() - 1 || line.isGoal())
//                return;
//            start = line.getLineNum();
//            end = start + 1;
//            multilineSelect = true;
//            select(end, start);
//        }
//    }
}
