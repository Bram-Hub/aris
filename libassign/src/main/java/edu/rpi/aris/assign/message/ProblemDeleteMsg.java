package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.Perm;
import edu.rpi.aris.assign.ServerPermissions;
import edu.rpi.aris.assign.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    @Nullable
    @Override
    public ErrorType processMessage(@NotNull Connection connection, @NotNull User user, @NotNull ServerPermissions permissions) throws SQLException {
        try (PreparedStatement deleteProblem = connection.prepareStatement("DELETE FROM problem WHERE id = ?;")) {
            deleteProblem.setInt(1, pid);
            deleteProblem.executeUpdate();
        }
        return null;
    }

    @NotNull
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
