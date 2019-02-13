package edu.rpi.aris.gui;

import edu.rpi.aris.assign.EditMode;
import edu.rpi.aris.proof.Line;
import edu.rpi.aris.proof.Proof;
import edu.rpi.aris.rules.RuleList;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.util.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

public class CopyManager {

    private static final DataFormat dataFormat = new DataFormat("application/aris-clipboard");
    private static Logger log = LogManager.getLogger();
    private MainWindow window;
    private int start;
    private int end;
    private boolean multilineSelect = false;
    private Clipboard clipboard = Clipboard.getSystemClipboard();

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

    private ProofLine getPLine() {
        LineInterface l = getLine();
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
        if (multilineSelect) {
            String text = selectionToString();
            HashMap<DataFormat, Object> data = new HashMap<>();
            data.put(dataFormat, text);
            clipboard.setContent(data);
            if (cut) {
                // copy to local variables to prevent concurrent modification
                int start = this.start;
                int end = this.end;
                window.getHistory().startEventBundle();
                ProofLine root = getPLine(start);
                if (root == null)
                    return;
                int lvl = root.getModel().getSubProofLevel();
                for (int i = end; i >= start; --i) {
                    ProofLine line = getPLine(i);
                    if (line == null)
                        continue;
                    if (line.getModel().getSubProofLevel() == lvl)
                        window.deleteLine(i);
                }
                window.getHistory().finalizeEventBundle();
            }
        } else {
            LineInterface line = getLine();
            if (line == null)
                return;
            if (cut && checkEditMode(line))
                line.cut();
            else
                line.copy();
        }
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

    private PasteLine parsePasteLine(String line, int parentLvl) {
        String[] split = line.split("\\|");
        if (split.length != 5)
            return null;
        try {
            boolean isAssumption = Boolean.parseBoolean(split[0]);
            int lvl = Integer.parseInt(split[1]);
            if (lvl < 0 || (isAssumption ? lvl > parentLvl + 1 : lvl > parentLvl))
                return null;
            String expr = URLDecoder.decode(split[2], "UTF-8");
            RuleList rule = split[3].equals("null") ? null : RuleList.valueOf(split[3]);
            return new PasteLine(isAssumption, lvl, expr, rule, split[4].equals("null") ? null : Arrays.asList(split[4].split(",")));
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            log.error("Failed to parse paste data", e);
            return null;
        }
    }

    private PasteLine[] parsePasteData(String data) {
        String[] split = data.split("\n");
        PasteLine[] lines = new PasteLine[split.length];
        int lvl = 0;
        for (int i = 0; i < lines.length; ++i) {
            lines[i] = parsePasteLine(split[i], lvl);
            if (lines[i] == null)
                return null;
            lvl = lines[i].lvl;
        }
        return lines;
    }

    private String getLineString(ProofLine proofLine, int rootIndent) {
        try {
            Line line = proofLine.getModel();
            StringBuilder premises = new StringBuilder();
            for (Line p : line.getPremises()) {
                if (p.getLineNum() >= start && p.getLineNum() <= end) {
                    premises.append("l.").append(p.getLineNum() - start);
                } else {
                    premises.append("h.").append(p.hashCode());
                }
                premises.append(",");
            }
            if (premises.length() > 0)
                premises.deleteCharAt(premises.length() - 1);
            else
                premises.append("null");
            return line.isAssumption() + "|"
                    + (line.getSubProofLevel() - rootIndent) + "|"
                    + URLEncoder.encode(line.getExpressionString(), "UTF-8") + "|"
                    + line.getSelectedRule() + "|"
                    + premises.toString();
        } catch (UnsupportedEncodingException e) {
            log.error("Failed to convert ProofLine to string", e);
            return null;
        }
    }

    private String selectionToString() {
        if (!multilineSelect)
            return null;
        ProofLine line = getPLine(start);
        if (line == null)
            return null;
        int baseIndent = line.getModel().getSubProofLevel() - (line.isAssumption() ? 1 : 0);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= end; ++i) {
            line = getPLine(i);
            if (line == null)
                return null;
            sb.append(getLineString(line, baseIndent));
            if (i != end)
                sb.append("\n");
        }
        return sb.toString();
    }

    private Pair<Integer, Integer> getSelectableRange(int start, int end) {
        Pair<Integer, Integer> p = getCommonLine(start, end);
        return p == null ? null : checkCommonParent(p.getKey(), p.getValue());
    }

    private void insertLine(PasteLine pasteLine, int baseIndent, int realLineNum, int relativeLineNum) {
        Proof proof = window.getProof();
        Line line = proof.addLine(realLineNum, pasteLine.isAssumption, baseIndent + pasteLine.lvl);
        line.setExpressionString(pasteLine.expr);
        if (!pasteLine.isAssumption)
            line.setSelectedRule(pasteLine.rule);
        for (String pStr : pasteLine.prems) {
            Line premiseLine = null;
            try {
                if (pStr.startsWith("l.")) {
                    String[] split = pStr.split("\\.");
                    if (split.length == 2) {
                        int relNum = Integer.parseInt(split[1]);
                        if (relNum < relativeLineNum)
                            premiseLine = proof.getLine(realLineNum - relativeLineNum + relNum);
                    }
                } else if (pStr.startsWith("h.")) {
                    String[] split = pStr.split("\\.");
                    if (split.length == 2) {
                        int hashCode = Integer.parseInt(split[1]);
                        for (int i = 0; i < realLineNum; ++i) {
                            Line l = proof.getLine(i);
                            if (l.hashCode() == hashCode) {
                                premiseLine = l;
                                break;
                            }
                        }
                    }
                }
            } catch (NumberFormatException e) {
                log.error("Failed to parse premise on paste", e);
                premiseLine = null;
            }
            if (premiseLine != null)
                proof.setPremise(realLineNum, premiseLine, true);
        }
        window.addProofLine(line);
    }

    public void cut() {
        copy(true);
    }

    public void paste() {
        deselect();
        Object content;
        if ((content = clipboard.getContent(dataFormat)) != null) {
            String pasteStr = (String) content;
            PasteLine[] data = parsePasteData(pasteStr);
            ProofLine currentLine = getPLine();
            if (currentLine == null || data == null || data.length == 0)
                return;
            int baseIndent = currentLine.getModel().getSubProofLevel();
            int startLine = currentLine.getLineNum() + 1;
            window.getHistory().startEventBundle();
            for (int i = 0; i < data.length; ++i) {
                insertLine(data[i], baseIndent, startLine + i, i);
            }
            window.getHistory().finalizeEventBundle();
        } else if (clipboard.getString() != null) {
            LineInterface line = getLine();
            if (line != null && checkEditMode(line))
                line.paste();
        }
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
        if (multilineSelect)
            deselect();
        int currentNum = current == null || current.isGoal() ? clickedNum : current.getLineNum();
        rangeStart = Math.min(currentNum, clickedNum);
        rangeEnd = Math.max(currentNum, clickedNum);
        Pair<Integer, Integer> range = getSelectableRange(rangeStart, rangeEnd);
        if (range != null) {
            start = range.getKey();
            end = range.getValue();
            multilineSelect = true;
            for (int i = start; i <= end; ++i)
                select(i);
        }
    }

    private static class PasteLine {

        final boolean isAssumption;
        final int lvl;
        final String expr;
        final RuleList rule;
        final ArrayList<String> prems = new ArrayList<>();

        public PasteLine(boolean isAssumption, int lvl, String expr, RuleList rule, Collection<String> prems) {
            this.isAssumption = isAssumption;
            this.lvl = lvl;
            this.expr = expr;
            this.rule = rule;
            if (prems != null)
                this.prems.addAll(prems);
        }

    }

}
