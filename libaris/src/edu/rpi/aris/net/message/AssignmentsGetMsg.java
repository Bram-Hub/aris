package edu.rpi.aris.net.message;

import edu.rpi.aris.net.NetUtil;
import edu.rpi.aris.net.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.ArrayList;

public class AssignmentsGetMsg extends Message {

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

    @Override
    public ErrorType processMessage(Connection connection, User user) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT a.name, a.due_date, u2.username, a.id FROM assignment a, users u, users u2, class c, user_class uc WHERE uc.user_id = u.id AND uc.class_id = c.id AND a.class_id = uc.class_id AND a.assigned_by = u2.id AND u.username = ? AND c.id = ? GROUP BY a.id, a.name, a.due_date, u2.username ORDER BY a.due_date;")) {
            statement.setString(1, user.username);
            statement.setInt(2, classId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String assignmentName = rs.getString(1);
                    ZonedDateTime dueDate = NetUtil.localToUTC(rs.getTimestamp(2).toLocalDateTime());
                    String assignedBy = rs.getString(3);
                    int assignmentId = rs.getInt(4);
                    assignments.add(new MsgUtil.AssignmentData(assignmentName, assignedBy, dueDate, assignmentId));
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
