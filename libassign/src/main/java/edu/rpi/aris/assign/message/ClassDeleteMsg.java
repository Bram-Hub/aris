package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.Perm;
import edu.rpi.aris.assign.ServerPermissions;
import edu.rpi.aris.assign.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ClassDeleteMsg extends Message {

    private final int cid;

    public ClassDeleteMsg(int cid) {
        this.cid = cid;
    }

    // DO NOT REMOVE!! Default constructor is required for gson deserialization
    private ClassDeleteMsg() {
        cid = 0;
    }

    @Override
    public ErrorType processMessage(Connection connection, User user, ServerPermissions permissions) throws SQLException {
        if (!user.isAdmin() && !permissions.hasClassPermission(user.uid, cid, permissions.getPermission(Perm.CLASS_CREATE_DELETE), connection))
            return ErrorType.UNAUTHORIZED;
        try (PreparedStatement deleteClass = connection.prepareStatement("DELETE FROM class WHERE id = ?;")) {
            deleteClass.setInt(1, cid);
            deleteClass.executeUpdate();
        }
        return null;
    }

    public int getClassId() {
        return cid;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.DELETE_CLASS;
    }

    @Override
    public boolean checkValid() {
        return cid > 0;
    }
}
