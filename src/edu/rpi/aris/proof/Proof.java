package edu.rpi.aris.proof;

import edu.rpi.aris.gui.event.PremiseChangeEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

public class Proof {

    private final HashSet<String> authors = new HashSet<>();
    private ArrayList<Line> lines = new ArrayList<>();
    private ArrayList<Goal> goals = new ArrayList<>();
    private int numPremises = 0;

    public Proof(String author) {
        authors.add(author);
    }

    public Proof(Collection<String> authors, String author) {
        this(author);
        this.authors.addAll(authors);
    }

    public Line getLine(int index) {
        return lines.get(index);
    }

    public Goal getGoal(int index) {
        return goals.get(index);
    }

    public int getNumLines() {
        return lines.size();
    }

    public int getNumPremises() {
        return numPremises;
    }

    private void setNumPremises(int numPremises) {
        this.numPremises = numPremises;
    }

    public Line addLine(int index, boolean isAssumption, int subProofLevel) {
        if (index <= lines.size()) {
            Line l = new Line(subProofLevel, isAssumption, this);
            lines.add(index, l);
            for (int i = 0; i < lines.size(); ++i)
                lines.get(i).setLineNum(i);
            if (isAssumption && subProofLevel == 0)
                setNumPremises(getNumPremises() + 1);
            return l;
        } else
            return null;
    }

    public Line addPremise() {
        Line line = addLine(getNumPremises(), true, 0);
        line.setUnderlined(line.getLineNum() == getNumPremises() - 1);
        for (int i = 0; i < line.getLineNum(); ++i)
            getLine(i).setUnderlined(false);
        return line;
    }

    public Goal addGoal(int index) {
        Goal goal = new Goal();
        goals.add(index, goal);
        for (int i = index; i < goals.size(); ++i)
            goals.get(i).setGoalNum(i);
        return goal;
    }

    public void removeGoal(int goalNum) {
        goals.remove(goalNum);
    }

    public HashSet<Integer> getPossiblePremiseLines(Line line) {
        HashSet<Integer> possiblePremise = new HashSet<>();
        int maxLvl = line.getSubProofLevel() + 1;
        for (int i = line.getLineNum() - 1; i >= 0; i--) {
            Line p = lines.get(i);
            if (p.getSubProofLevel() == maxLvl && p.isAssumption()) {
                possiblePremise.add(i);
            } else if (p.getSubProofLevel() < maxLvl) {
                possiblePremise.add(i);
                if (p.getSubProofLevel() < maxLvl && p.isAssumption())
                    maxLvl = p.getSubProofLevel();
            }
        }
        return possiblePremise;
    }

    public PremiseChangeEvent togglePremise(int selected, Line premise) {
        if (selected < 0)
            return null;
        Line line = lines.get(selected);
        if (line.isAssumption() || premise.getLineNum() >= selected)
            return null;
        HashSet<Integer> canSelect = getPossiblePremiseLines(line);
        for (int i = premise.getLineNum(); i >= 0; i--) {
            if (canSelect.contains(i)) {
                premise = lines.get(i);
                break;
            }
        }
        boolean wasSelected = line.removePremise(premise);
        if (!wasSelected)
            line.addPremise(premise);
        return new PremiseChangeEvent(selected, premise.getLineNum(), wasSelected);
    }

    public PremiseChangeEvent setPremise(int selected, Line premise, boolean isSelected) {
        if (selected < 0)
            return null;
        Line line = lines.get(selected);
        if (line.isAssumption() || premise.getLineNum() >= selected)
            return null;
        HashSet<Integer> canSelect = getPossiblePremiseLines(line);
        for (int i = premise.getLineNum(); i >= 0; i--) {
            if (canSelect.contains(i)) {
                premise = lines.get(i);
                break;
            }
        }
        boolean wasSelected = line.getPremises().contains(premise);
        if (isSelected)
            line.addPremise(premise);
        else
            line.removePremise(premise);
        return new PremiseChangeEvent(selected, premise.getLineNum(), wasSelected);
    }

    public HashSet<Line> getHighlighted(Line line) {
        HashSet<Line> highlight = new HashSet<>(line.getPremises());
        for (Line p : line.getPremises()) {
            HashSet<Line> highlighted = new HashSet<>();
            int lineNum = p.getLineNum();
            if (p.isAssumption() && lineNum + 1 < lines.size()) {
                int indent = p.getSubProofLevel();
                Line l = lines.get(lineNum + 1);
                while (l != null && (l.getSubProofLevel() > indent || (l.getSubProofLevel() == indent && !l.isAssumption()))) {
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
        if (lineNum > 0 || (getNumPremises() > 1 && lineNum >= 0)) {
            for (Line l : lines)
                l.lineDeleted(lines.get(lineNum));
            Line removed = lines.remove(lineNum);
            for (int i = lineNum + 1; i < lines.size(); ++i)
                lines.get(i).setLineNum(i - 1);
            if (getNumPremises() > 1 && lineNum < getNumPremises()) {
                if (removed.isUnderlined() && lineNum > 0)
                    lines.get(lineNum - 1).setUnderlined(true);
                setNumPremises(getNumPremises() - 1);
            }
        }
    }

    public ArrayList<Status> verifyProof() {
        ArrayList<Status> goalStatus = new ArrayList<>();
        for (Goal g : goals) {
            if (g.getExpression() == null && !g.buildExpression())
                goalStatus.add(Status.INVALID_EXPRESSION);
            else {
                ArrayList<Line> lines = findGoals(g.getExpression());
                if (lines.size() == 0) {
                    g.setStatus(Status.INVALID_CLAIM);
                    g.setStatusString("This goal is not a top level statement in the proof");
                    goalStatus.add(Status.INVALID_CLAIM);
                } else {
                    boolean valid = false;
                    for (Line l : lines) {
                        if (recursiveLineVerification(l)) {
                            valid = true;
                            break;
                        }
                    }
                    g.setStatus(valid ? Status.CORRECT : Status.INVALID_CLAIM);
                    g.setStatusString(valid ? "Congratulations! You proved the goal!" : "The goal does not follow from the support steps");
                    goalStatus.add(g.getStatus());
                }
            }
        }
        return goalStatus;
    }

    private boolean recursiveLineVerification(Line l) {
        if (l.isAssumption())
            return true;
        if (l.getStatus() == Status.NONE || l.getStatus() == Status.CORRECT) {
            if (l.getStatus() != Status.CORRECT && !l.verifyClaim())
                return false;
            for (Line p : l.getPremises())
                if (!recursiveLineVerification(p))
                    return false;
            return true;
        } else
            return false;
    }

    private ArrayList<Line> findGoals(Expression e) {
        ArrayList<Line> goals = new ArrayList<>();
        if (e == null)
            return goals;
        for (int i = getNumLines() - 1; i >= 0; --i) {
            Line l = lines.get(i);
            if (l.getExpression() == null)
                l.buildExpression();
            if (l.getSubProofLevel() == 0 && l.getExpression() != null && l.getExpression().equals(e))
                goals.add(l);
        }
        return goals;
    }

    Line getSubProofConclusion(Line assumption, Line goal) {
        int lvl = assumption.getSubProofLevel();
        if (!assumption.isAssumption() || lvl == 0)
            return null;
        for (int i = assumption.getLineNum() + 1; i < getNumLines(); ++i) {
            Line l = lines.get(i);
            if (l == goal)
                return null;
            if (l.getSubProofLevel() < lvl || (l.getSubProofLevel() == lvl && l.isAssumption()))
                return lines.get(i - 1);
        }
        return null;
    }

    void resetGoalStatus() {
        for (Goal g : goals) {
            g.setStatus(Status.NONE);
            g.buildExpression();
        }
    }

    public HashSet<String> getAuthors() {
        return authors;
    }

    public int getNumGoals() {
        return goals.size();
    }

    public enum Status {

        NONE("no_icon.png"),
        INVALID_EXPRESSION("warning.png"),
        NO_RULE("question_mark.png"),
        INVALID_CLAIM("red_x.png"),
        CORRECT("check_mark.png");

        public final String imgName;

        Status(String imgLoc) {
            imgName = imgLoc;
        }

    }

}
