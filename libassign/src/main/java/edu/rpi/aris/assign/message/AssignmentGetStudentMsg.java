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

public class AssignmentGetStudentMsg extends Message implements ClassMessage {

    private final int cid;
    private final int aid;
    private final HashSet<MsgUtil.ProblemInfo> problems = new HashSet<>();
    private final HashMap<Integer, HashSet<MsgUtil.SubmissionInfo>> submissions = new HashMap<>();
    private String name;
    private ZonedDateTime dueDate;

    public AssignmentGetStudentMsg(int cid, int aid) {
        super(Perm.ASSIGNMENT_GET_STUDENT);
        this.cid = cid;
        this.aid = aid;
    }

    private AssignmentGetStudentMsg() {
        this(-1, -1);
    }

    @Nullable
    @Override
    public ErrorType processMessage(@NotNull Connection connection, @NotNull User user, @NotNull ServerPermissions permissions) throws Exception {
        try (PreparedStatement selectAssignment = connection.prepareStatement("SELECT problem_id, name, due_date FROM assignment WHERE id = ? AND class_id = ?;");
             PreparedStatement selectProblem = connection.prepareStatement("SELECT name, created_by, created_on, module_name, problem_hash FROM problem WHERE id = ?;");
             PreparedStatement selectSubmissions = connection.prepareStatement("SELECT id, time, short_status, status, problem_id FROM submission WHERE class_id = ? AND assignment_id = ? AND user_id = ?;")) {
            selectAssignment.setInt(1, aid);
            selectAssignment.setInt(2, cid);
            try (ResultSet assignmentRs = selectAssignment.executeQuery()) {
                while (assignmentRs.next()) {
                    if (name == null) {
                        name = assignmentRs.getString(2);
                        dueDate = NetUtil.localToUTC(assignmentRs.getTimestamp(3).toLocalDateTime());
                    }
                    int pid = assignmentRs.getInt(1);
                    selectProblem.setInt(1, pid);
                    try (ResultSet problemRs = selectProblem.executeQuery()) {
                        if (problemRs.next()) {
                            String name = problemRs.getString(1);
                            String createdBy = problemRs.getString(2);
                            ZonedDateTime createdOn = NetUtil.localToUTC(problemRs.getTimestamp(3).toLocalDateTime());
                            String module = problemRs.getString(4);
                            String problemHash = problemRs.getString(5);
                            MsgUtil.ProblemInfo problemInfo = new MsgUtil.ProblemInfo(pid, name, createdBy, createdOn, module, problemHash);
                            problems.add(problemInfo);
                        }
                    }
                }
            }
            selectSubmissions.setInt(1, cid);
            selectSubmissions.setInt(2, aid);
            selectSubmissions.setInt(3, user.uid);
            try (ResultSet submissionRs = selectSubmissions.executeQuery()) {
                while (submissionRs.next()) {
                    int sid = submissionRs.getInt(1);
                    ZonedDateTime submitted = NetUtil.localToUTC(submissionRs.getTimestamp(2).toLocalDateTime());
                    GradingStatus status;
                    try {
                        status = GradingStatus.valueOf(submissionRs.getString(3));
                    } catch (IllegalArgumentException e) {
                        status = GradingStatus.NONE;
                    }
                    String statusStr = submissionRs.getString(4);
                    int pid = submissionRs.getInt(5);
                    MsgUtil.SubmissionInfo submissionInfo = new MsgUtil.SubmissionInfo(user.uid, sid, pid, cid, aid, status, statusStr, submitted);
                    submissions.computeIfAbsent(pid, id -> new HashSet<>()).add(submissionInfo);
                }
            }
        }
        return null;
    }

    @NotNull
    @Override
    public MessageType getMessageType() {
        return MessageType.ASSIGNMENT_GET_STUDENT;
    }

    @Override
    public boolean checkValid() {
        return cid > 0 && aid > 0;
    }

    @Override
    public int getClassId() {
        return cid;
    }

    public int getAssignmentId() {
        return aid;
    }

    public ZonedDateTime getDueDate() {
        return dueDate;
    }

    public String getName() {
        return name;
    }

    public HashSet<MsgUtil.ProblemInfo> getProblems() {
        return problems;
    }

    public HashMap<Integer, HashSet<MsgUtil.SubmissionInfo>> getSubmissions() {
        return submissions;
    }
}
