package edu.rpi.aris.gui.submit;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;

import java.text.DateFormat;
import java.util.Date;

public class StudentInfo implements AssignmentInfo, EventHandler<ActionEvent>, Comparable<AssignmentInfo> {

    private final int classId;
    private final int assignmentId;
    private final int submissionId;
    private final boolean isSubmission;
    private long timestamp;
    private String status, name;
    private int proofId;
    private Object[] data = new Object[getNumColumns()];
    private String[] names = new String[]{"Proof", "Submission Status", "Date Submitted", "Open Proof"};

    public StudentInfo(int submissionId, int proofId, int classId, int assignmentId, String name, long timestamp, String status) {
        this.submissionId = submissionId;
        this.proofId = proofId;
        this.classId = classId;
        this.assignmentId = assignmentId;
        this.timestamp = timestamp;
        this.status = status;
        this.name = name;

        isSubmission = submissionId > 0;

        Button openProof = new Button("Open");
        openProof.setOnAction(this);

        data[0] = name;
        data[1] = status;
        if (timestamp > 0)
            data[2] = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(timestamp));
        else
            data[2] = null;
        data[3] = openProof;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        data[1] = status;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        if (timestamp > 0)
            data[2] = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(timestamp));
        else
            data[2] = null;
    }

    @Override
    public int getNumColumns() {
        return 4;
    }

    @Override
    public Object getColumnData(int columnNum) {
        return data[columnNum];
    }

    @Override
    public String getColumnName(int columnNum) {
        return names[columnNum];
    }

    @Override
    public void handle(ActionEvent event) {
        System.out.println("Open proof " + proofId);
    }

    @Override
    public int compareTo(AssignmentInfo o) {
        if (!(o instanceof StudentInfo))
            return -1;
        StudentInfo s = (StudentInfo) o;
        if (isSubmission)
            return Long.compare(timestamp, s.timestamp);
        else
            return name.compareTo(s.name);
    }
}
