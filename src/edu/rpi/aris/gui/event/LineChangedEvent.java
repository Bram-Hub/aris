package edu.rpi.aris.gui.event;

import edu.rpi.aris.gui.MainWindow;
import edu.rpi.aris.gui.Proof;

import java.util.*;

public class LineChangedEvent extends HistoryEvent {

    private SortedMap<Integer, Proof.Line> changed;
    private HashMap<Integer, HashSet<Integer>> premises = new HashMap<>();
    private boolean deleted;

    public LineChangedEvent(SortedMap<Integer, Proof.Line> changed, boolean deleted) {
        Objects.requireNonNull(changed);
        this.changed = changed;
        this.deleted = deleted;
        for (Map.Entry<Integer, Proof.Line> e : changed.entrySet()) {
            HashSet<Integer> p = new HashSet<>();
            for (Proof.Line l : e.getValue().getPremises())
                p.add(l.lineNumberProperty().get());
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
        for (Map.Entry<Integer, Proof.Line> l : changed.entrySet()) {
            Proof.Line p = l.getValue();
            Proof.Line line = window.getProof().addLine(l.getKey(), p.isAssumption(), p.subProofLevelProperty().get());
            line.expressionStringProperty().set(p.expressionStringProperty().get());
            line.selectedRuleProperty().set(p.selectedRuleProperty().get());
            for (int i : premises.get(l.getKey()))
                window.getProof().togglePremise(l.getKey(), window.getProof().getLines().get(i));
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
