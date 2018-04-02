package edu.rpi.aris.proof;

import org.apache.commons.lang3.Range;

public interface StatusChangeListener {

    void statusString(Line line, String statusString);

    void errorRange(Line line, Range<Integer> range);

    void statusString(Goal goal, String statusString);

    void errorRange(Goal goal, Range<Integer> range);

}
