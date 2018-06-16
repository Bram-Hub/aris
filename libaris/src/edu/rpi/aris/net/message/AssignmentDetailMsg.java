package edu.rpi.aris.net.message;

import edu.rpi.aris.net.User;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

public class AssignmentDetailMsg extends Message {

    private int id, classId;

    public AssignmentDetailMsg(int id, int classId) {
        this.id = id;
        this.classId = classId;
    }

    @Override
    public ErrorType processMessage(Connection connection, User user) throws SQLException, IOException {
        return null;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.GET_ASSIGNMENT_DETAIL;
    }

}
