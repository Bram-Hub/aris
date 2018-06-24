package edu.rpi.aris.gui.submit;

import edu.rpi.aris.net.GradingStatus;
import edu.rpi.aris.net.NetUtil;
import edu.rpi.aris.net.message.MsgUtil;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

public class SubmissionInfo extends AssignmentInfo implements EventHandler<ActionEvent> {

    private final int classId;
    private final int assignmentId;
    private final int userId;
    private final int submissionId;
    private long timestamp;
    private String status;
    private String name;
    private String dateString;
    private GradingStatus gradingStatus;
    private int proofId;
    private Button btn;

    public SubmissionInfo(MsgUtil.SubmissionInfo info, String subName, boolean isInstructor) {
        this(info.uid, info.sid, info.pid, info.cid, info.aid, subName, NetUtil.UTCToMilli(info.getSubmissionTime()), info.statusStr, info.status, isInstructor);
    }

    public SubmissionInfo(int userId, int submissionId, int proofId, int classId, int assignmentId, String name, long timestamp, String status, GradingStatus gradingStatus, boolean isInstructor) {
        this.userId = userId;
        this.submissionId = submissionId;
        this.proofId = proofId;
        this.classId = classId;
        this.assignmentId = assignmentId;
        this.status = status;
        this.name = name;
        this.gradingStatus = gradingStatus;

        setTimestamp(timestamp);

        btn = new Button(isInstructor() ? "View Submission" : "Open Submission");
        btn.setOnAction(this);
        btn.setAlignment(Pos.CENTER);
    }

    public String getStatus() {
        return status;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        if (timestamp > 0) {
            dateString = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(timestamp));
        } else
            dateString = null;
    }

    @Override
    public Object getColumnData(int columnNum) {
        switch (columnNum) {
            case 0:
                return name;
            case 1:
                return status;
            case 2:
                return dateString;
            case 3:
                return btn;
            default:
                return null;
        }
    }

    @Override
    public void addChild(AssignmentInfo info) {
    }

    @Override
    public ArrayList<AssignmentInfo> getChildren() {
        return null;
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
        return Long.compare(timestamp, s.timestamp);
    }

    public int getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public GradingStatus getGradingStatus() {
        return gradingStatus;
    }

    public String getDate() {
        return dateString;
    }
}
