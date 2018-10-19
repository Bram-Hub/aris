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
import java.util.LinkedHashMap;
import java.util.Map;

public class SubmissionGetInstructorMsg extends Message {

    private final int aid;
    private final int cid;

    private final LinkedHashMap<Integer, String> users = new LinkedHashMap<>();
    private final ArrayList<MsgUtil.ProblemInfo> assignedProblems = new ArrayList<>();
    private final HashMap<Integer, HashMap<Integer, ArrayList<MsgUtil.SubmissionInfo>>> submissions = new HashMap<>();

    public SubmissionGetInstructorMsg(int aid, int cid) {
        super(null);
        this.aid = aid;
        this.cid = cid;
    }

    // DO NOT REMOVE!! Default constructor is required for gson deserialization
    private SubmissionGetInstructorMsg() {
        this(0,0);
    }

    public HashMap<Integer, HashMap<Integer, ArrayList<MsgUtil.SubmissionInfo>>> getSubmissions() {
        return submissions;
    }

    public ArrayList<MsgUtil.ProblemInfo> getAssignedProblems() {
        return assignedProblems;
    }

    public LinkedHashMap<Integer, String> getUsers() {
        return users;
    }

    @Nullable
    @Override
    public ErrorType processMessage(@NotNull Connection connection, @NotNull User user, @NotNull ServerPermissions permissions) throws SQLException {
        if (!user.isAdmin())
            return ErrorType.UNAUTHORIZED;
        try (PreparedStatement userStatement = connection.prepareStatement("SELECT u.id, u.username FROM users u, user_class uc WHERE uc.user_id = u.id AND u.user_type = 'student' AND uc.class_id = ? ORDER BY u.username;");
             PreparedStatement problems = connection.prepareStatement("SELECT p.id, p.name, p.created_by, p.created_on, p.module_name FROM problem p, assignment a WHERE a.problem_id = p.id AND a.class_id = ? AND a.id = ? ORDER BY p.name;");
             PreparedStatement userSubmissions = connection.prepareStatement("SELECT u.id, s.id, s.problem_id, s.time, s.status, s.short_status FROM users u, assignment a, submission s, problem p WHERE a.class_id = ? AND a.id = ? AND u.user_type = 'student' AND s.class_id = a.class_id AND s.assignment_id = a.id AND s.user_id = u.id AND p.id = s.problem_id ORDER BY u.username, p.name, s.time DESC;")) {
            userStatement.setInt(1, cid);
            try (ResultSet rs = userStatement.executeQuery()) {
                while (rs.next()) {
                    int uid = rs.getInt(1);
                    String username = rs.getString(2);
                    users.put(uid, username);
                }
            }
            problems.setInt(1, cid);
            problems.setInt(2, aid);
            try (ResultSet rs = problems.executeQuery()) {
                while (rs.next()) {
                    int pid = rs.getInt(1);
                    String pName = rs.getString(2);
                    String createdBy = rs.getString(3);
                    ZonedDateTime timestamp = NetUtil.localToUTC(rs.getTimestamp(4).toLocalDateTime());
                    String moduleName = rs.getString(5);
                    assignedProblems.add(new MsgUtil.ProblemInfo(pid, pName, createdBy, timestamp, moduleName));
                }
            }
            userSubmissions.setInt(1, cid);
            userSubmissions.setInt(2, aid);
            try (ResultSet rs = userSubmissions.executeQuery()) {
                while (rs.next()) {
                    int uid = rs.getInt(1);
                    int sid = rs.getInt(2);
                    int pid = rs.getInt(3);
                    ZonedDateTime timestamp = NetUtil.localToUTC(rs.getTimestamp(4).toLocalDateTime());
                    String statusStr = rs.getString(5);
                    GradingStatus status = GradingStatus.valueOf(rs.getString(6));
                    MsgUtil.SubmissionInfo sInfo = new MsgUtil.SubmissionInfo(uid, sid, pid, cid, aid, status, statusStr, timestamp);
                    submissions.computeIfAbsent(uid, id -> new HashMap<>()).computeIfAbsent(pid, id -> new ArrayList<>()).add(sInfo);
                }
            }
        }
        return null;
    }

    @NotNull
    @Override
    public MessageType getMessageType() {
        return MessageType.GET_SUBMISSIONS_INST;
    }

//    private final LinkedHashMap<Integer, String> users = new LinkedHashMap<>();
//    private final ArrayList<MsgUtil.ProblemInfo> assignedProblems = new ArrayList<>();
//    private final HashMap<Integer, HashMap<Integer, ArrayList<MsgUtil.SubmissionInfo>>> submissions = new HashMap<>();

    @Override
    public boolean checkValid() {
        for (Map.Entry<Integer, String> u : users.entrySet())
            if (u.getKey() == null || u.getValue() == null)
                return false;
        for (MsgUtil.ProblemInfo info : assignedProblems)
            if (info == null || !info.checkValid())
                return false;
        for (Map.Entry<Integer, HashMap<Integer, ArrayList<MsgUtil.SubmissionInfo>>> sub : submissions.entrySet()) {
            if (sub.getKey() == null || sub.getValue() == null)
                return false;
            for (Map.Entry<Integer, ArrayList<MsgUtil.SubmissionInfo>> s : sub.getValue().entrySet()) {
                if (s.getKey() == null || s.getValue() == null)
                    return false;
                for (MsgUtil.SubmissionInfo info : s.getValue())
                    if (info == null || !info.checkValid())
                        return false;
            }
        }
        return aid > 0 && cid > 0;
    }
}
