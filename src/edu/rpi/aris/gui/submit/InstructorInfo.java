package edu.rpi.aris.gui.submit;

public class InstructorInfo implements AssignmentInfo {

    public InstructorInfo() {

    }

    @Override
    public int getNumColumns() {
        return 0;
    }

    @Override
    public Object getColumnData(int columnNum) {
        return null;
    }

    @Override
    public String getColumnName(int columnNum) {
        return null;
    }

    @Override
    public int compareTo(AssignmentInfo o) {
        return 0;
    }
}
