package edu.rpi.aris.net.message;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import edu.rpi.aris.net.MessageBuildException;
import edu.rpi.aris.net.MessageParseException;
import edu.rpi.aris.net.NetUtil;
import edu.rpi.aris.net.User;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.ArrayList;

public class AssignmentsMsg extends Message {

    private int classId;
    private ArrayList<AssignmentData> assignments = new ArrayList<>();

    AssignmentsMsg() {
        this(-1);
    }

    public AssignmentsMsg(int classId) {
        super(MessageType.GET_ASSIGNMENTS);
        this.classId = classId;
    }

    public ArrayList<AssignmentData> getAssignments() {
        return assignments;
    }

    @Override
    protected void parseMessage(JsonObject jsonMsg) throws MessageParseException {
        classId = getInt(jsonMsg, CLASS_ID, -1, true);
        if (classId <= 0)
            throw new MessageParseException("Invalid class id: " + classId);
    }

    @Override
    protected void parseReply(JsonObject jsonMsg) throws MessageParseException {
        int cid = getInt(jsonMsg, CLASS_ID, classId, false);
        if (cid != classId)
            throw new MessageParseException("Response class id does not match expected class id");
        JsonArray arr = getArray(jsonMsg, ASSIGNMENTS);
        for (JsonElement element : arr) {
            JsonObject obj = getAsObject(element);
            String name = getString(obj, NAME, null, true);
            String assignedBy = getString(obj, BY, "unknown", false);
            ZonedDateTime zdt = ZonedDateTime.parse(getString(obj, DUE, null, true), NetUtil.ZDT_FORMAT);
            int aid = getInt(obj, ID, -1, true);
            assignments.add(new AssignmentData(name, assignedBy, zdt, aid));
        }
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
                    assignments.add(new AssignmentData(assignmentName, assignedBy, dueDate, assignmentId));
                }
            }
        }
        return null;
    }

    @Override
    public Pair<JsonObject, byte[]> buildMessage() throws MessageBuildException {
        JsonObject obj = new JsonObject();
        if (classId <= 0)
            throw new MessageBuildException("Class id has not been set");
        obj.addProperty(CLASS_ID, classId);
        return new ImmutablePair<>(obj, null);
    }

    @Override
    public Pair<JsonObject, byte[]> buildReplyMessage() throws MessageBuildException {
        Pair<JsonObject, byte[]> msg = buildMessage();
        JsonObject obj = msg.getKey();
        JsonArray arr = new JsonArray();
        for (AssignmentData data : assignments) {
            JsonObject dataObj = new JsonObject();
            dataObj.addProperty(NAME, data.name);
            dataObj.addProperty(BY, data.assignedBy);
            dataObj.addProperty(DUE, data.dueDateUTC.format(NetUtil.ZDT_FORMAT));
            dataObj.addProperty(ID, data.id);
            arr.add(dataObj);
        }
        obj.add(ASSIGNMENTS, arr);
        return msg;
    }

    public static class AssignmentData {

        public final String name, assignedBy;
        public final int id;
        public final ZonedDateTime dueDateUTC;

        public AssignmentData(String name, String assignedBy, ZonedDateTime dueDateUTC, int id) {
            this.name = name;
            this.assignedBy = assignedBy;
            this.dueDateUTC = dueDateUTC;
            this.id = id;
        }

    }

}
