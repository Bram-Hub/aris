package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.Perm;
import edu.rpi.aris.assign.ServerPermissions;
import edu.rpi.aris.assign.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ProblemDeleteMsg extends Message {

    private final int pid;

    public ProblemDeleteMsg(int pid) {
        super(Perm.PROBLEM_DELETE);
        this.pid = pid;
    }

    // DO NOT REMOVE!! Default constructor is required for gson deserialization
    private ProblemDeleteMsg() {
        this(0);
    }

    @Override
    public ErrorType processMessage(Connection connection, User user, ServerPermissions permissions) throws SQLException {
        if (!permissions.hasPermission(user, Perm.PROBLEM_DELETE))
            return ErrorType.UNAUTHORIZED;
        try (PreparedStatement deleteProblem = connection.prepareStatement("DELETE FROM problem WHERE id = ?;")) {
            deleteProblem.setInt(1, pid);
            deleteProblem.executeUpdate();
        }
        return null;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.DELETE_PROBLEM;
    }

    @Override
    public boolean checkValid() {
        return pid > 0;
    }

    public int getPid() {
        return pid;
    }

}
