package edu.rpi.aris.proof;

import edu.rpi.aris.rules.RuleList;
import org.apache.commons.lang3.Range;

import java.util.HashSet;

public interface LineChangeListener {

    void expressionString(String str);

    void status(Proof.Status status);

    void lineNumber(int lineNum);

    void premises(HashSet<Line> premises);

    void subProofLevel(int level);

    void selectedRule(RuleList rule);

    void statusString(String statusString);

    void errorRange(Range<Integer> range);

    void underlined(boolean underlined);

}
