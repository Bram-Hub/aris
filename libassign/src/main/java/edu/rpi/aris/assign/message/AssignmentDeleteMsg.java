package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.Perm;
import edu.rpi.aris.assign.ServerPermissions;
import edu.rpi.aris.assign.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class AssignmentDeleteMsg extends Message implements ClassMessage {

    private final int cid;
    private final int aid;

    public AssignmentDeleteMsg(int cid, int aid) {
        super(Perm.ASSIGNMENT_DELETE);
        this.cid = cid;
        this.aid = aid;
    }

    // DO NOT REMOVE!! Default constructor is required for gson deserialization
    private AssignmentDeleteMsg() {
        this(0, 0);
    }

    @Nullable
    @Override
    public ErrorType processMessage(@NotNull Connection connection, @NotNull User user, @NotNull ServerPermissions permissions) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM assignment WHERE id = ? AND class_id = ?;")) {
            statement.setInt(1, aid);
            statement.setInt(2, cid);
            statement.executeUpdate();
        }
        return null;
    }

    @NotNull
    @Override
    public MessageType getMessageType() {
        return MessageType.DELETE_ASSIGNMENT;
    }

    @Override
    public boolean checkValid() {
        return cid > 0 && aid > 0;
    }

    public int getClassId() {
        return cid;
    }

    public int getAid() {
        return aid;
    }

}
