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

    public void togglePremise(int selected, Line premise) {
        Line line = lines.get(selected);
        if (line != null && premise.lineNumber.get() < selected)
            line.togglePremise(premise);
    }

    public void delete(int lineNum) {
        if (lineNum > 0) {
            for (Line l : lines)
                l.lineDeleted(lines.get(lineNum));
            lines.remove(lineNum);
            for (int i = lineNum; i < lines.size(); ++i)
                lineLookup.put(lines.get(i), i);
        }
    }

    public void delete(Line line) {
        if (line != null)
            delete(lineLookup.get(line));
    }

    public static class Line {

        private SimpleIntegerProperty lineNumber = new SimpleIntegerProperty();
        private SimpleStringProperty expressionString = new SimpleStringProperty();
        private HashSet<Line> highlightLines = new HashSet<>();

        public IntegerProperty lineNumberProperty() {
            return lineNumber;
        }

        public HashSet<Line> getHighlightLines() {
            return highlightLines;
        }

        public void togglePremise(Line premise) {
            if (!highlightLines.remove(premise))
                highlightLines.add(premise);
        }

        private void lineDeleted(Line deletedLine) {
            highlightLines.remove(deletedLine);
        }

    }

}
