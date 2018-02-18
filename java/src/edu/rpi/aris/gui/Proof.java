package edu.rpi.aris.gui;

import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;

import java.util.HashSet;

public class Proof {

    private ObservableMap<Line, Integer> lineLookup = FXCollections.observableHashMap();
    private ObservableList<Line> lines = FXCollections.observableArrayList();
    private SimpleIntegerProperty numLines = new SimpleIntegerProperty();

    public Proof() {
        numLines.bind(Bindings.size(lines));
    }

    public IntegerProperty numLinesProperty() {
        return numLines;
    }

    public ObservableList<Line> getLines() {
        return lines;
    }

    public Proof.Line addLine(int index) {
        if (index <= lines.size()) {
            Line l = new Line();
            lines.add(index, l);
            lineLookup.put(l, index);
            for (int i = index + 1; i < lines.size(); ++i)
                lineLookup.put(lines.get(i), i);
            l.lineNumberProperty().bind(Bindings.createIntegerBinding(() -> lineLookup.get(l), lineLookup));
            return l;
        } else
            return null;
    }

    public static class Line {

        private SimpleIntegerProperty lineNumber = new SimpleIntegerProperty();
        private SimpleStringProperty expressionString = new SimpleStringProperty();
        private HashSet<Line> premises = new HashSet<>();
        private HashSet<LineDeletionListener> listeners = new HashSet<>();

        public IntegerProperty lineNumberProperty() {
            return lineNumber;
        }

        public void delete() {
            for (LineDeletionListener l : listeners)
                l.lineDeleted();
        }

        public boolean addLineDeletionListener(LineDeletionListener listener) {
            return listeners.add(listener);
        }

        public boolean removeLineDeletionListener(LineDeletionListener listener) {
            return listeners.remove(listener);
        }

    }

}
