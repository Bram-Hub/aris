package edu.rpi.aris.gui.event;

import edu.rpi.aris.gui.MainWindow;
import edu.rpi.aris.proof.Line;

import java.util.*;

public class LineChangedEvent extends HistoryEvent {

    private SortedMap<Integer, Line> changed;
    private HashMap<Integer, HashSet<Integer>> premises = new HashMap<>();
    private boolean deleted;

    public LineChangedEvent(SortedMap<Integer, Line> changed, boolean deleted) {
        Objects.requireNonNull(changed);
        this.changed = changed;
        this.deleted = deleted;
        for (Map.Entry<Integer, Line> e : changed.entrySet()) {
            HashSet<Integer> p = new HashSet<>();
            for (Line l : e.getValue().getPremises())
                p.add(l.getLineNum());
            premises.put(e.getKey(), p);
        }
    }

    private void deleteLine(MainWindow window) {
        ArrayList<Integer> lineNumbers = new ArrayList<>(changed.keySet());
        lineNumbers.sort(Comparator.reverseOrder());
        for (int i : lineNumbers)
            window.removeLine(i);
    }

    private void addLine(MainWindow window) {
        for (Map.Entry<Integer, Line> l : changed.entrySet()) {
            Line p = l.getValue();
            Line line = window.getProof().addLine(l.getKey(), p.isAssumption(), p.getSubProofLevel());
            line.setExpressionString(p.getExpressionString());
            line.setSelectedRule(p.getSelectedRule());
            for (int i : premises.get(l.getKey()))
                window.getProof().setPremise(l.getKey(), window.getProof().getLine(i), true);
            window.addProofLine(line);
        }
        window.requestFocus(window.getProofLines().get(changed.firstKey()));
    }

    @Override
    public void undoEvent(MainWindow window) {
        if (deleted)
            addLine(window);
        else
            deleteLine(window);
    }

    @Override
    public void redoEvent(MainWindow window) {
        if (deleted)
            deleteLine(window);
        else
            addLine(window);
    }

}
