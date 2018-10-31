package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.GradingStatus;
import edu.rpi.aris.assign.NetUtil;
import edu.rpi.aris.assign.ServerPermissions;
import edu.rpi.aris.assign.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SubmissionGetStudentMsg extends Message {

    private final int aid, cid;
    private final ArrayList<MsgUtil.ProblemInfo> assignedProblems = new ArrayList<>();
    private final HashMap<Integer, ArrayList<MsgUtil.SubmissionInfo>> submissions = new HashMap<>();

    public SubmissionGetStudentMsg(int aid, int cid) {
        super(null);
        this.aid = aid;
        this.cid = cid;
    }

    // DO NOT REMOVE!! Default constructor is required for gson deserialization
    private SubmissionGetStudentMsg() {
        this(0, 0);
    }

    public ArrayList<MsgUtil.ProblemInfo> getAssignedProblems() {
        return assignedProblems;
    }

    public HashMap<Integer, ArrayList<MsgUtil.SubmissionInfo>> getSubmissions() {
        return submissions;
    }

    @Nullable
    @Override
    public ErrorType processMessage(@NotNull Connection connection, @NotNull User user, @NotNull ServerPermissions permissions) throws SQLException {
        try (PreparedStatement assignments = connection.prepareStatement("SELECT p.aid, p.name, p.created_by, p.created_on, p.module_name, p.problem_hash FROM assignment a, problem p WHERE a.class_id = ? AND a.aid = ? AND a.problem_id = p.aid;");
             PreparedStatement submissions = connection.prepareStatement("SELECT aid, problem_id, time, status, short_status FROM submission WHERE class_id = ? AND assignment_id = ? AND user_id = ? ORDER BY problem_id, aid DESC;")) {
            assignments.setInt(1, cid);
            assignments.setInt(2, aid);
            try (ResultSet rs = assignments.executeQuery()) {
                while (rs.next()) {
                    int pid = rs.getInt(1);
                    String pName = rs.getString(2);
                    String createdBy = rs.getString(3);
                    ZonedDateTime timestamp = NetUtil.localToUTC(rs.getTimestamp(4).toLocalDateTime());
                    String moduleName = rs.getString(5);
                    String problemHash = rs.getString(6);
                    assignedProblems.add(new MsgUtil.ProblemInfo(pid, pName, createdBy, timestamp, moduleName, problemHash));
                }
            }
            submissions.setInt(1, cid);
            submissions.setInt(2, aid);
            submissions.setInt(3, user.uid);
            try (ResultSet rs = submissions.executeQuery()) {
                while (rs.next()) {
                    int sid = rs.getInt(1);
                    int pid = rs.getInt(2);
                    ZonedDateTime submissionTime = NetUtil.localToUTC(rs.getTimestamp(3).toLocalDateTime());
                    String statusStr = rs.getString(4);
                    GradingStatus status = GradingStatus.valueOf(rs.getString(5));
                    this.submissions.computeIfAbsent(pid, id -> new ArrayList<>()).add(new MsgUtil.SubmissionInfo(user.uid, sid, pid, cid, aid, status, statusStr, submissionTime));
                }
            }
        }
        return null;
    }

    @NotNull
    @Override
    public MessageType getMessageType() {
        return MessageType.GET_SUBMISSIONS_STUDENT;
    }

    @Override
    public boolean checkValid() {
        for (MsgUtil.ProblemInfo info : assignedProblems)
            if (info == null || !info.checkValid())
                return false;
        for (Map.Entry<Integer, ArrayList<MsgUtil.SubmissionInfo>> sub : submissions.entrySet()) {
            if (sub.getKey() == null || sub.getValue() == null)
                return false;
            for (MsgUtil.SubmissionInfo info : sub.getValue())
                if (info == null || !info.checkValid())
                    return false;
        }
        return aid > 0 && cid > 0;
    }

}
