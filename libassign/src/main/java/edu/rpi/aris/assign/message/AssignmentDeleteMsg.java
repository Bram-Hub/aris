package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.Perm;
import edu.rpi.aris.assign.ServerPermissions;
import edu.rpi.aris.assign.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class AssignmentDeleteMsg extends Message {

    private final int cid;
    private final int aid;

    public AssignmentDeleteMsg(int cid, int aid) {
        this.cid = cid;
        this.aid = aid;
    }

    // DO NOT REMOVE!! Default constructor is required for gson deserialization
    private AssignmentDeleteMsg() {
        aid = cid = 0;
    }

    @Override
    public ErrorType processMessage(Connection connection, User user, ServerPermissions permissions) throws SQLException {
        if (!user.isAdmin() && !permissions.hasClassPermission(user.uid, cid, permissions.getPermission(Perm.ASSIGNMENT_DELETE), connection))
            return ErrorType.UNAUTHORIZED;
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM assignment WHERE id = ? AND class_id = ?;")) {
            statement.setInt(1, aid);
            statement.setInt(2, cid);
            statement.executeUpdate();
        }
        return null;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.DELETE_ASSIGNMENT;
    }

    @Override
    public boolean checkValid() {
        return cid > 0 && aid > 0;
    }

    public int getCid() {
        return cid;
    }

    public int getAid() {
        return aid;
    }

}
