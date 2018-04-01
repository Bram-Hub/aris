package edu.rpi.aris.gui.submit;

import java.util.List;

public interface AssignmentInfo extends Comparable<AssignmentInfo> {

    int getNumColumns();

    Object getColumnData(int columnNum);

    String getColumnName(int columnNum);

    void addChild(AssignmentInfo info);

    List<AssignmentInfo> getChildren();

}
