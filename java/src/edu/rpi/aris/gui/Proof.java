package edu.rpi.aris.gui;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.HashSet;

public class Proof {

    private ObservableList<Line> lines = FXCollections.observableArrayList();

    public Proof() {
        lines.addListener((ListChangeListener<Line>) change -> {
            while (change.next()) {
                if(change.wasRemoved()) {

                } else {
                    for (int i = change.getFrom(); i < change.getTo(); i++) {
                        change.getList().get(i).setLineNumber(i + 1);
                    }
                }
            }
        });
    }

    public Proof.Line addLine(int index) {
        if (index <= lines.size()) {
            Line l = new Line(index);
            lines.add(index, l);
            return l;
        } else
            return null;
    }

    public static class Line {
        private SimpleIntegerProperty lineNumber = new SimpleIntegerProperty();
        private SimpleStringProperty expressionString = new SimpleStringProperty();
        private HashSet<Integer> premises = new HashSet<>();
        private HashSet<LineDeletionListener> listeners = new HashSet<>();

        public Line(int lineNumber) {
            this.lineNumber.set(lineNumber + 1);
        }

        public void setLineNumber(int lineNumber) {
            this.lineNumber.set(lineNumber);
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
