package edu.rpi.aris.assign.client.guiold;

import java.util.ArrayList;

public abstract class AssignmentInfo implements Comparable<AssignmentInfo> {

    private static String[] studentColumnNames = new String[]{"Name", "Submission Status", "Date Submitted", "Open Proof", ""};
    private static String[] instructorColumnNames = new String[]{};

    private int classId;
    private int assignmentId;
    private boolean isInstructor;

    public void assignmentInfo(int classId, int assignmentId, boolean isInstructor) {
        this.classId = classId;
        this.assignmentId = assignmentId;
        this.isInstructor = isInstructor;
    }

    public int getNumColumns() {
        return isInstructor ? instructorColumnNames.length : studentColumnNames.length;
    }

    public String getColumnName(int columnNum) {
        return isInstructor ? instructorColumnNames[columnNum] : studentColumnNames[columnNum];
    }

    public boolean isInstructor() {
        return isInstructor;
    }

    public int getAssignmentId() {
        return assignmentId;
    }

    public int getClassId() {
        return classId;
    }

    public abstract Object getColumnData(int columnNum);

    public abstract void addChild(AssignmentInfo info);

    public abstract ArrayList<? extends AssignmentInfo> getChildren();

}
