package edu.rpi.aris.gui;

import edu.rpi.aris.proof.Claim;
import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.SentenceUtil;
import edu.rpi.aris.rules.RuleList;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;

import java.text.ParseException;
import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;

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
            Line l = new Line(subproofLevel, isAssumption, this);
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
        int maxLvl = line.subProofLevel.get() + 1;
        for (int i = selected - 1; i >= 0; i--) {
            Line p = lines.get(i);
            if (p.subProofLevel.get() == maxLvl && p.isAssumption) {
                canSelect.add(i);
            } else if (p.subProofLevel.get() < maxLvl) {
                canSelect.add(i);
                if (p.subProofLevel.get() < maxLvl && p.isAssumption)
                    maxLvl = p.subProofLevel.get();
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
                int indent = p.subProofLevelProperty().get();
                Proof.Line l = lines.get(lineNum + 1);
                while (l != null && (l.subProofLevelProperty().get() > indent || (l.subProofLevelProperty().get() == indent && !l.isAssumption()))) {
                    highlighted.add(l);
                    if (lineNum + 1 == lines.size())
                        l = null;
                    else
                        l = lines.get(++lineNum);
                }
            }
            if (!highlighted.contains(line))
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
        private SimpleIntegerProperty subProofLevel = new SimpleIntegerProperty();
        private SimpleBooleanProperty underlined = new SimpleBooleanProperty();
        private SimpleObjectProperty<RuleList> selectedRule = new SimpleObjectProperty<>(null);
        private Expression expression = null;
        private Claim claim = null;
        private SimpleStringProperty statusMsg = new SimpleStringProperty();
        private Timer parseTimer = null;
        private Proof proof;

        private Line(int subProofLevel, boolean assumption, Proof proof) {
            isAssumption = assumption;
            underlined.set(isAssumption);
            this.proof = proof;
            this.subProofLevel.set(subProofLevel);
            expressionString.addListener((observableValue, oldVal, newVal) -> {
                synchronized (Line.this) {
                    expression = null;
                    claim = null;
                    startTimer();
                }
            });
        }

        public IntegerProperty lineNumberProperty() {
            return lineNumber;
        }

        public IntegerProperty subProofLevelProperty() {
            return subProofLevel;
        }

        public boolean isAssumption() {
            return isAssumption;
        }

        public HashSet<Line> getPremises() {
            return premises;
        }

        private void addPremise(Line premise) {
            premises.add(premise);
        }

        private boolean removePremise(Line premise) {
            return premises.remove(premise);
        }

        private void lineDeleted(Line deletedLine) {
            removePremise(deletedLine);
        }

        private synchronized void buildExpression() {
            String str = expressionString.get();
            if (str.trim().length() > 0) {
                try {
                    claim = null;
                    expression = new Expression(SentenceUtil.toPolishNotation(str));
                    Platform.runLater(() -> statusMsg.set(""));
                } catch (ParseException e) {
                    Platform.runLater(() -> statusMsg.set(e.getMessage()));
                    expression = null;
                }
            } else {
                expression = null;
            }
        }

        private synchronized void buildClaim() {
            stopTimer();
            buildExpression();
            if (expression == null)
                return;
            if (selectedRule.get() == null)
                statusMsg.set("Rule Not Specified");
            statusMsg.set("Claim construction code not complete");
        }

        public synchronized boolean verifyClaim() {
            buildClaim();
            if (claim != null) {
                String result = claim.isValidClaim();
                if (result == null)
                    statusMsg.set("Line is Correct!");
                else
                    statusMsg.set(result);
                return result == null;
            }
            return false;
        }

        public SimpleBooleanProperty isUnderlined() {
            return underlined;
        }

        public SimpleStringProperty expressionStringProperty() {
            return expressionString;
        }

        public SimpleObjectProperty<RuleList> selectedRuleProperty() {
            return selectedRule;
        }

        public SimpleStringProperty statusMsgProperty() {
            return statusMsg;
        }

        private synchronized void startTimer() {
            stopTimer();
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    buildExpression();
                }
            };
            parseTimer = new Timer();
            parseTimer.schedule(task, 1000);
        }

        private synchronized void stopTimer() {
            if (parseTimer != null) {
                parseTimer.cancel();
                parseTimer = null;
            }
        }

    }

}
