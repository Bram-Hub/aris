package edu.rpi.aris.gui.submit;

public interface AssignmentInfo extends Comparable<AssignmentInfo> {

    int getNumColumns();

    Object getColumnData(int columnNum);

    String getColumnName(int columnNum);

}
