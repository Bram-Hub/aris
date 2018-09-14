package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.NetUtil;
import edu.rpi.aris.assign.ServerPermissions;
import edu.rpi.aris.assign.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;

public class AssignmentsGetMsg extends Message {

    private static final String SELECT_ASSIGNMENTS_ADMIN = "SELECT a.name , a.due_date , u.username , a.id FROM assignment a , users u , class c WHERE a.class_id = c.id AND a.assigned_by = u.id AND c.id = ? GROUP BY a.id , a.name , a.due_date , u.username ORDER BY a.due_date;";
    private static final String SELECT_ASSIGNMENTS_NON_ADMIN = "SELECT a.name, a.due_date, u2.username, a.id FROM assignment a, users u, users u2, class c, user_class uc WHERE uc.user_id = u.id AND uc.class_id = c.id AND a.class_id = uc.class_id AND a.assigned_by = u2.id AND c.id = ? AND u.username = ? GROUP BY a.id, a.name, a.due_date, u2.username ORDER BY a.due_date;";
    private static final String SELECT_PROBLEMS = "SELECT problem_id FROM assignment WHERE class_id = ? AND id = ?;";
    private final int classId;
    private final ArrayList<MsgUtil.AssignmentData> assignments = new ArrayList<>();

    public AssignmentsGetMsg(int classId) {
        this.classId = classId;
    }

    // DO NOT REMOVE!! Default constructor is required for gson deserialization
    private AssignmentsGetMsg() {
        classId = 0;
    }

    public ArrayList<MsgUtil.AssignmentData> getAssignments() {
        return assignments;
    }

    public int getClassId() {
        return classId;
    }

    @Override
    public ErrorType processMessage(Connection connection, User user, ServerPermissions permissions) throws SQLException {
        try (PreparedStatement selectAssignments = connection.prepareStatement(user.isAdmin() ? SELECT_ASSIGNMENTS_ADMIN : SELECT_ASSIGNMENTS_NON_ADMIN)) {
            selectAssignments.setInt(1, classId);
            if (user.isAdmin())
                selectAssignments.setString(2, user.username);
            try (ResultSet assignmentsRs = selectAssignments.executeQuery();
                 PreparedStatement selectProblems = connection.prepareStatement(SELECT_PROBLEMS)) {
                selectProblems.setInt(1, classId);
                while (assignmentsRs.next()) {
                    String assignmentName = assignmentsRs.getString(1);
                    ZonedDateTime dueDate = NetUtil.localToUTC(assignmentsRs.getTimestamp(2).toLocalDateTime());
                    String assignedBy = assignmentsRs.getString(3);
                    int assignmentId = assignmentsRs.getInt(4);
                    selectProblems.setInt(2, assignmentId);
                    try (ResultSet problemsRs = selectProblems.executeQuery()) {
                        HashSet<Integer> problems = new HashSet<>();
                        while (problemsRs.next())
                            problems.add(problemsRs.getInt(1));
                        assignments.add(new MsgUtil.AssignmentData(assignmentName, assignedBy, dueDate, assignmentId, problems));
                    }
                }
            }
        }
        return null;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.GET_ASSIGNMENTS;
    }

    @Override
    public boolean checkValid() {
        for (MsgUtil.AssignmentData data : assignments)
            if (data == null || !data.checkValid())
                return false;
        return classId > 0;
    }

}
