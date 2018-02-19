package edu.rpi.aris.gui;

import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
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
    private SimpleIntegerProperty numPremises = new SimpleIntegerProperty();

    public Proof() {
        numLines.bind(Bindings.size(lines));
    }

    public IntegerProperty numLinesProperty() {
        return numLines;
    }

    public IntegerProperty numPremises() {
        return numPremises;
    }

    public ObservableList<Line> getLines() {
        return lines;
    }

    public Proof.Line addLine(int index, boolean isAssumption, int subproofLevel) {
        if (index <= lines.size()) {
            Line l = new Line(subproofLevel, isAssumption);
            lines.add(index, l);
            lineLookup.put(l, index);
            for (int i = index + 1; i < lines.size(); ++i)
                lineLookup.put(lines.get(i), i);
            l.lineNumberProperty().bind(Bindings.createIntegerBinding(() -> lineLookup.get(l), lineLookup));
            return l;
        } else
            return null;
    }

    public Proof.Line addPremise() {
        Line line = addLine(numPremises.get(), true, 0);
        line.isUnderlined().bind(Bindings.createBooleanBinding(() -> line.lineNumber.get() == numPremises.get() - 1, numPremises));
        numPremises.set(numPremises.get() + 1);
        return line;
    }

    public void togglePremise(int selected, Line premise) {
        Line line = lines.get(selected);
        if (line.isAssumption)
            return;
        HashSet<Integer> canSelect = new HashSet<>();
        int maxLvl = line.subproofLevel.get() + 1;
        for (int i = selected - 1; i >= 0; i--) {
            Line p = lines.get(i);
            if (p.subproofLevel.get() == maxLvl && p.isAssumption) {
                canSelect.add(i);
            } else if (p.subproofLevel.get() < maxLvl) {
                canSelect.add(i);
                if (p.subproofLevel.get() < maxLvl && p.isAssumption)
                    maxLvl = p.subproofLevel.get();
            }
        }
        for (int i = premise.lineNumber.get(); i >= 0; i--) {
            if (canSelect.contains(i)) {
                premise = lines.get(i);
                break;
            }
        }
        if (!line.removePremise(premise))
            line.addPremise(premise);
    }

    public HashSet<Line> getHighlighted(Line line) {
        HashSet<Line> highlight = new HashSet<>(line.premises);
        for (Line p : line.premises) {
            HashSet<Line> highlighted = new HashSet<>();
            int lineNum = p.lineNumber.get();
            if (p.isAssumption() && lineNum + 1 < lines.size()) {
                int indent = p.subproofLevelProperty().get();
                Proof.Line l = lines.get(lineNum + 1);
                while (l != null && (l.subproofLevelProperty().get() > indent || (l.subproofLevelProperty().get() == indent && !l.isAssumption()))) {
                    highlighted.add(l);
                    if (lineNum + 1 == lines.size())
                        l = null;
                    else
                        l = lines.get(++lineNum);
                }
            }
            if(!highlighted.contains(line))
                highlight.addAll(highlighted);
            highlight.add(p);
        }
        return highlight;
    }

    public void delete(int lineNum) {
        if (lineNum > 0 || (numPremises.get() > 1 && lineNum >= 0)) {
            for (Line l : lines)
                l.lineDeleted(lines.get(lineNum));
            lines.remove(lineNum);
            for (int i = lineNum; i < lines.size(); ++i)
                lineLookup.put(lines.get(i), i);
            if (numPremises.get() > 1 && lineNum < numPremises.get()) {
                numPremises.set(numPremises.get() - 1);
            }
        }
    }

    public void delete(Line line) {
        if (line != null)
            delete(lineLookup.get(line));
    }

    public static class Line {

        private final boolean isAssumption;
        private SimpleIntegerProperty lineNumber = new SimpleIntegerProperty();
        private SimpleStringProperty expressionString = new SimpleStringProperty();
        private HashSet<Line> premises = new HashSet<>();
        //        private HashSet<Line> highlightLines = new HashSet<>();
        private SimpleIntegerProperty subproofLevel = new SimpleIntegerProperty();
        private SimpleBooleanProperty underlined = new SimpleBooleanProperty();

        public Line(int subproofLevel, boolean assumption) {
            isAssumption = assumption;
            underlined.set(isAssumption);
            this.subproofLevel.set(subproofLevel);
        }

        public IntegerProperty lineNumberProperty() {
            return lineNumber;
        }

        public IntegerProperty subproofLevelProperty() {
            return subproofLevel;
        }

        public boolean isAssumption() {
            return isAssumption;
        }

//        public HashSet<Line> getHighlightLines() {
//            return highlightLines;
//        }

        public HashSet<Line> getPremises() {
            return premises;
        }

        public void addPremise(Line premise) {
            premises.add(premise);
        }

        public boolean removePremise(Line premise) {
            return premises.remove(premise);
        }

//        private void addHighlight(Line line) {
//            highlightLines.add(line);
//        }
//
//        private void removeHighlight(Line line) {
//            highlightLines.remove(line);
//        }

        private void lineDeleted(Line deletedLine) {
            removePremise(deletedLine);
        }

        private void setUnderlined(boolean underlined) {
            this.underlined.set(underlined);
        }

        public SimpleBooleanProperty isUnderlined() {
            return underlined;
        }
    }

}
