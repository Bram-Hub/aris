package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;

public class AssignmentGetInstructorMsg extends Message implements ClassMessage {

    private final int cid;
    private final int aid;
    private final HashSet<MsgUtil.ProblemInfo> problems = new HashSet<>();
    private final HashMap<Integer, HashMap<Integer, HashSet<MsgUtil.SubmissionInfo>>> submissions = new HashMap<>(); // (uid, (pid, submissions))
    private final HashMap<Integer, Pair<String, String>> users = new HashMap<>(); // (uid, (username, full name))
    private String name;
    private ZonedDateTime dueDate;

    public AssignmentGetInstructorMsg(int cid, int aid) {
        super(Perm.ASSIGNMENT_GET_INSTRUCTOR);
        this.cid = cid;
        this.aid = aid;
    }

    private AssignmentGetInstructorMsg() {
        this(-1, -1);
    }

    @Nullable
    @Override
    public ErrorType processMessage(@NotNull Connection connection, @NotNull User user, @NotNull ServerPermissions permissions) throws Exception {
        try (PreparedStatement selectAssignment = connection.prepareStatement("SELECT problem_id, name, due_date FROM assignment WHERE id = ? AND class_id = ?;");
             PreparedStatement selectProblem = connection.prepareStatement("SELECT name, created_by, created_on, module_name, problem_hash FROM problem WHERE id = ?;");
             PreparedStatement selectUsers = connection.prepareStatement("SELECT u.id, u.username, u.full_name FROM users u, user_class uc WHERE uc.user_id = u.id AND uc.class_id = ? AND uc.role_id = ?;");
             PreparedStatement selectSubmissions = connection.prepareStatement("SELECT id, time, short_status, status, problem_id, user_id, grade FROM submission WHERE class_id = ? AND assignment_id = ?;")) {
            selectUsers.setInt(1, cid);
            selectUsers.setInt(2, permissions.getPermission(Perm.SUBMISSION_CREATE).getRollId());
            try (ResultSet userRs = selectUsers.executeQuery()) {
                while (userRs.next())
                    this.users.put(userRs.getInt(1), new Pair<>(userRs.getString(2), userRs.getString(3)));
            }
            selectAssignment.setInt(1, aid);
            selectAssignment.setInt(2, cid);
            try (ResultSet assignRs = selectAssignment.executeQuery()) {
                while (assignRs.next()) {
                    if (name == null) {
                        name = assignRs.getString(2);
                        dueDate = NetUtil.localToUTC(assignRs.getTimestamp(3).toLocalDateTime());
                    }
                    int pid = assignRs.getInt(1);
                    selectProblem.setInt(1, pid);
                    try (ResultSet probRs = selectProblem.executeQuery()) {
                        if (!probRs.next())
                            continue;
                        String name = probRs.getString(1);
                        String createdBy = probRs.getString(2);
                        ZonedDateTime createdOn = NetUtil.localToUTC(probRs.getTimestamp(3).toLocalDateTime());
                        String module = probRs.getString(4);
                        String problemHash = probRs.getString(5);
                        MsgUtil.ProblemInfo problemInfo = new MsgUtil.ProblemInfo(pid, name, createdBy, createdOn, module, problemHash);
                        problems.add(problemInfo);
                    }
                }
            }
            selectSubmissions.setInt(1, cid);
            selectSubmissions.setInt(2, aid);
            try (ResultSet subRs = selectSubmissions.executeQuery()) {
                while (subRs.next()) {
                    int sid = subRs.getInt(1);
                    ZonedDateTime submitted = NetUtil.localToUTC(subRs.getTimestamp(2).toLocalDateTime());
                    GradingStatus status;
                    try {
                        status = GradingStatus.valueOf(subRs.getString(3));
                    } catch (IllegalArgumentException e) {
                        status = GradingStatus.NONE;
                    }
                    String statusStr = subRs.getString(4);
                    int pid = subRs.getInt(5);
                    int uid = subRs.getInt(6);
                    double grade = subRs.getDouble(7);
                    MsgUtil.SubmissionInfo info = new MsgUtil.SubmissionInfo(uid, sid, pid, cid, aid, grade, status, statusStr, submitted);
                    HashMap<Integer, HashSet<MsgUtil.SubmissionInfo>> subGroups = submissions.computeIfAbsent(uid, id -> new HashMap<>());
                    subGroups.computeIfAbsent(pid, id -> new HashSet<>()).add(info);
                }
            }
        }
        return null;
    }

    @NotNull
    @Override
    public MessageType getMessageType() {
        return MessageType.ASSIGNMENT_GET_INSTRUCTOR;
    }

    @Override
    public boolean checkValid() {
        return cid > 0 && aid > 0;
    }

    @Override
    public int getClassId() {
        return cid;
    }

    public ZonedDateTime getDueDate() {
        return dueDate;
    }

    public HashSet<MsgUtil.ProblemInfo> getProblems() {
        return problems;
    }

    public HashMap<Integer, HashMap<Integer, HashSet<MsgUtil.SubmissionInfo>>> getSubmissions() {
        return submissions;
    }

    public HashMap<Integer, Pair<String, String>> getUsers() {
        return users;
    }

    public String getName() {
        return name;
    }
}
