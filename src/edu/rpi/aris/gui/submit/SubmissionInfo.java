package edu.rpi.aris.gui.submit;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;

import java.text.DateFormat;
import java.util.Date;

public class SubmissionInfo implements AssignmentInfo, EventHandler<ActionEvent> {

    private final int classId;
    private final int assignmentId;
    private final int userId;
    private final int submissionId;
    private final boolean isSubmission;
    private long timestamp;
    private String status, name;
    private int proofId;
    private Object[] data = new Object[getNumColumns()];
    private String[] names = new String[]{"Proof", "Submission Status", "Date Submitted", "Open Proof"};

    public SubmissionInfo(int userId, int submissionId, int proofId, int classId, int assignmentId, String name, long timestamp, String status) {
        this.userId = userId;
        this.submissionId = submissionId;
        this.proofId = proofId;
        this.classId = classId;
        this.assignmentId = assignmentId;
        this.timestamp = timestamp;
        this.status = status;
        this.name = name;

        isSubmission = submissionId > 0;

        Button openProof = new Button(isSubmission ? "Open Submission" : "Open Template");
        openProof.setOnAction(this);
        openProof.setAlignment(Pos.CENTER);

        data[0] = name;
        setStatus(status);
        setTimestamp(timestamp);
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
        if (timestamp > 0) {
            data[2] = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(timestamp));
        } else
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
        if (!(o instanceof SubmissionInfo))
            return -1;
        SubmissionInfo s = (SubmissionInfo) o;
        if (isSubmission)
            return Long.compare(timestamp, s.timestamp);
        else
            return name.compareTo(s.name);
    }
}
