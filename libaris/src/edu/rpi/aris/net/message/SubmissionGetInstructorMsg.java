package edu.rpi.aris.net.message;

import edu.rpi.aris.net.GradingStatus;
import edu.rpi.aris.net.NetUtil;
import edu.rpi.aris.net.User;

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
    private final ArrayList<MsgUtil.ProofInfo> assignedProofs = new ArrayList<>();
    private final HashMap<Integer, HashMap<Integer, ArrayList<MsgUtil.SubmissionInfo>>> submissions = new HashMap<>();

    public SubmissionGetInstructorMsg(int aid, int cid) {
        this.aid = aid;
        this.cid = cid;
    }

    // DO NOT REMOVE!! Default constructor is required for gson deserialization
    private SubmissionGetInstructorMsg() {
        aid = 0;
        cid = 0;
    }

    public HashMap<Integer, HashMap<Integer, ArrayList<MsgUtil.SubmissionInfo>>> getSubmissions() {
        return submissions;
    }

    public ArrayList<MsgUtil.ProofInfo> getAssignedProofs() {
        return assignedProofs;
    }

    public LinkedHashMap<Integer, String> getUsers() {
        return users;
    }

    @Override
    public ErrorType processMessage(Connection connection, User user) throws SQLException {
        if (!user.userType.equals(NetUtil.USER_INSTRUCTOR))
            return ErrorType.UNAUTHORIZED;
        try (PreparedStatement userStatement = connection.prepareStatement("SELECT u.id, u.username FROM users u, user_class uc WHERE uc.user_id = u.id AND u.user_type = 'student' AND uc.class_id = ? ORDER BY u.username;");
             PreparedStatement proofs = connection.prepareStatement("SELECT p.id, p.name, p.created_by, p.created_on FROM proof p, assignment a WHERE a.proof_id = p.id AND a.class_id = ? AND a.id = ? ORDER BY p.name;");
             PreparedStatement userSubmissions = connection.prepareStatement("SELECT u.id, s.id, s.proof_id, s.time, s.status, s.short_status FROM users u, assignment a, submission s, proof p WHERE a.class_id = ? AND a.id = ? AND u.user_type = 'student' AND s.class_id = a.class_id AND s.assignment_id = a.id AND s.user_id = u.id AND p.id = s.proof_id ORDER BY u.username, p.name, s.time DESC;")) {
            userStatement.setInt(1, cid);
            try (ResultSet rs = userStatement.executeQuery()) {
                while (rs.next()) {
                    int uid = rs.getInt(1);
                    String username = rs.getString(2);
                    users.put(uid, username);
                }
            }
            proofs.setInt(1, cid);
            proofs.setInt(2, aid);
            try (ResultSet rs = proofs.executeQuery()) {
                while (rs.next()) {
                    int pid = rs.getInt(1);
                    String pName = rs.getString(2);
                    String createdBy = rs.getString(3);
                    ZonedDateTime timestamp = NetUtil.localToUTC(rs.getTimestamp(4).toLocalDateTime());
                    assignedProofs.add(new MsgUtil.ProofInfo(pid, pName, createdBy, timestamp));
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

    @Override
    public MessageType getMessageType() {
        return MessageType.GET_SUBMISSIONS_INST;
    }

//    private final LinkedHashMap<Integer, String> users = new LinkedHashMap<>();
//    private final ArrayList<MsgUtil.ProofInfo> assignedProofs = new ArrayList<>();
//    private final HashMap<Integer, HashMap<Integer, ArrayList<MsgUtil.SubmissionInfo>>> submissions = new HashMap<>();

    @Override
    public boolean checkValid() {
        for (Map.Entry<Integer, String> u : users.entrySet())
            if (u.getKey() == null || u.getValue() == null)
                return false;
        for (MsgUtil.ProofInfo info : assignedProofs)
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
