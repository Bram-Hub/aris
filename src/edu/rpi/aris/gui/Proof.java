package edu.rpi.aris.gui;

import edu.rpi.aris.proof.Claim;
import edu.rpi.aris.proof.Expression;
import edu.rpi.aris.proof.Premise;
import edu.rpi.aris.proof.SentenceUtil;
import edu.rpi.aris.rules.RuleList;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.*;
import javafx.scene.image.Image;

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
        if (line.isAssumption || premise.lineNumber.get() >= selected)
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

    private Line getSubProofConclusion(Line assumption, Line goal) {
        int lvl = assumption.subProofLevel.get();
        if (!assumption.isAssumption || lvl == 0)
            return null;
        for (int i = assumption.lineNumber.get() + 1; i < numLines.get(); ++i) {
            Line l = lines.get(i);
            if (l == goal)
                return null;
            if (l.subProofLevel.get() < lvl || (l.subProofLevel.get() == lvl && l.isAssumption))
                return lines.get(i - 1);
        }
        return null;
    }

    public enum Status {

        NONE("no_icon.png"),
        INVALID_EXPRESSION("warning.png"),
        NO_RULE("question_mark.png"),
        INVALID_CLAIM("red_x.png"),
        CORRECT("check_mark.png");

        public final Image img;

        Status(String imgLoc) {
            img = new Image(Status.class.getResourceAsStream(imgLoc));
        }

    }

    public static class Line {

        private final boolean isAssumption;
        private SimpleIntegerProperty lineNumber = new SimpleIntegerProperty();
        private SimpleStringProperty expressionString = new SimpleStringProperty();
        private ObservableSet<Line> premises = FXCollections.observableSet();
        private SimpleIntegerProperty subProofLevel = new SimpleIntegerProperty();
        private SimpleBooleanProperty underlined = new SimpleBooleanProperty();
        private SimpleObjectProperty<RuleList> selectedRule = new SimpleObjectProperty<>(null);
        private SimpleObjectProperty<Status> status = new SimpleObjectProperty<>(Status.NONE);
        private Expression expression = null;
        private Claim claim = null;
        private SimpleStringProperty statusMsg = new SimpleStringProperty();
        private Timer parseTimer = null;
        private Proof proof;
        private ChangeListener<String> expressionChangeListener = (observableValue, s, t1) -> status.set(Status.NONE);

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
            selectedRule.addListener((observableValue, oldVal, newVal) -> verifyClaim());
            premises.addListener((SetChangeListener<Line>) change -> verifyClaim());
        }

        public IntegerProperty lineNumberProperty() {
            return lineNumber;
        }

        public IntegerProperty subProofLevelProperty() {
            return subProofLevel;
        }

        public SimpleObjectProperty<Status> statusProperty() {
            return status;
        }

        public boolean isAssumption() {
            return isAssumption;
        }

        public synchronized ObservableSet<Line> getPremises() {
            return premises;
        }

        private synchronized void addPremise(Line premise) {
            premises.add(premise);
            premise.expressionString.addListener(expressionChangeListener);
        }

        private synchronized boolean removePremise(Line premise) {
            premise.expressionString.removeListener(expressionChangeListener);
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
                    setStatus("");
                    status.set(Status.NONE);
                } catch (ParseException e) {
                    setStatus(e.getMessage());
                    status.set(Status.INVALID_EXPRESSION);
                    expression = null;
                }
            } else {
                expression = null;
                setStatus("");
                status.set(Status.NONE);
            }
        }

        private void setStatus(String status) {
            Platform.runLater(() -> statusMsg.set(status));
        }

        private synchronized void buildClaim() {
            buildExpression();
            if (expression == null || isAssumption)
                return;
            if (selectedRule.get() == null) {
                setStatus("Rule Not Specified");
                status.set(Status.NO_RULE);
                return;
            }
            Premise[] premises = new Premise[this.premises.size()];
            int i = 0;
            for (Line p : this.premises) {
                p.stopTimer();
                p.buildExpression();
                if (p.expression == null) {
                    setStatus("The expression at line " + (p.lineNumber.get() + 1) + " is invalid");
                    status.set(Status.INVALID_CLAIM);
                    return;
                }
                Line conclusion;
                if (p.isAssumption && (conclusion = proof.getSubProofConclusion(p, this)) != null)
                    premises[i] = new Premise(p.expression, conclusion.expression);
                else
                    premises[i] = new Premise(p.expression);
                ++i;
            }
            claim = new Claim(expression, premises, selectedRule.get().rule);
        }

        public boolean verifyClaim() {
            return verifyClaim(true);
        }

        private synchronized boolean verifyClaim(boolean stopTimer) {
            if (stopTimer)
                stopTimer();
            buildClaim();
            if (claim != null) {
                String result = claim.isValidClaim();
                if (result == null) {
                    setStatus("Line is Correct!");
                    status.set(Status.CORRECT);
                } else {
                    setStatus(result);
                    status.set(Status.INVALID_CLAIM);
                }
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
            if (!Aris.isGUI())
                return;
            stopTimer();
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    synchronized (Line.this) {
                        parseTimer = null;
                        verifyClaim(false);
                    }
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
