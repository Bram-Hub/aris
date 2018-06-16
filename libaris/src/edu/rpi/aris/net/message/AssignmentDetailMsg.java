package edu.rpi.aris.net.message;

import com.google.gson.JsonObject;
import edu.rpi.aris.net.MessageBuildException;
import edu.rpi.aris.net.MessageParseException;
import edu.rpi.aris.net.User;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

public class AssignmentDetailMsg extends Message {

    private int id, classId;

    AssignmentDetailMsg() {
        this(-1, -1);
    }

    public AssignmentDetailMsg(int id, int classId) {
        super(MessageType.GET_ASSIGNMENT_DETAIL);
        this.id = id;
        this.classId = classId;
    }

    @Override
    protected void parseMessage(JsonObject jsonMsg) throws MessageParseException {
        classId = getInt(jsonMsg, CLASS_ID, -1, true);
        id = getInt(jsonMsg, ID, -1, true);
        if (classId <= 0)
            throw new MessageParseException("Invalid class id: " + classId);
        if (id <= 0)
            throw new MessageParseException("Invalid class id: " + classId);
    }

    @Override
    protected void parseReply(JsonObject jsonMsg) throws MessageParseException {

    }

    @Override
    public ErrorType processMessage(Connection connection, User user) throws SQLException, IOException {
        return null;
    }

    @Override
    public Pair<JsonObject, byte[]> buildMessage() throws MessageBuildException {
        JsonObject obj = new JsonObject();
        if (id == -1 || classId == -1)
            throw new MessageBuildException("assignment id and class id must be set");
        obj.addProperty(CLASS_ID, classId);
        obj.addProperty(ID, id);
        return new ImmutablePair<>(obj, null);
    }

    @Override
    public Pair<JsonObject, byte[]> buildReplyMessage() throws MessageBuildException {
        return null;
    }
}
