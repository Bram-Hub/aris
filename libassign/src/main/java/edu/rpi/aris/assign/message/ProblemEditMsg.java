package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.User;
import edu.rpi.aris.assign.UserType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ProblemEditMsg extends Message {

    private final int pid;
    private final String name;
    private final byte[] problemData;

    public ProblemEditMsg(int pid, String name) {
        this(pid, name, null);
    }

    public ProblemEditMsg(int pid, byte[] problemData) {
        this(pid, null, problemData);
    }

    public ProblemEditMsg(int pid, String name, byte[] problemData) {
        this.pid = pid;
        this.name = name;
        this.problemData = problemData;
    }

    // DO NOT REMOVE!! Default constructor is required for gson deserialization
    private ProblemEditMsg() {
        pid = 0;
        name = null;
        problemData = null;
    }

    @Override
    public ErrorType processMessage(Connection connection, User user) throws SQLException {
        if (!UserType.hasPermission(user, UserType.INSTRUCTOR))
            return ErrorType.UNAUTHORIZED;
        if (name != null) {
            try (PreparedStatement updateName = connection.prepareStatement("UPDATE problem SET name = ? WHERE id = ?")) {
                updateName.setString(1, name);
                updateName.setInt(2, pid);
                updateName.executeUpdate();
            }
        }
        if (problemData != null) {
            try (PreparedStatement updateData = connection.prepareStatement("UPDATE problem SET data = ? WHERE id = ?")) {
                updateData.setBytes(1, problemData);
                updateData.setInt(2, pid);
                updateData.executeUpdate();
            }
        }
        return null;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.EDIT_PROBLEM;
    }

    @Override
    public boolean checkValid() {
        return pid > 0;
    }
}
