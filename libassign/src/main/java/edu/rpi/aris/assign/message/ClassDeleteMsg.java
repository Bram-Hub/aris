package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.Perm;
import edu.rpi.aris.assign.ServerPermissions;
import edu.rpi.aris.assign.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ClassDeleteMsg extends Message {

    private final int cid;

    public ClassDeleteMsg(int cid) {
        super(Perm.CLASS_CREATE_DELETE);
        this.cid = cid;
    }

    // DO NOT REMOVE!! Default constructor is required for gson deserialization
    private ClassDeleteMsg() {
        this(0);
    }

    @Nullable
    @Override
    public ErrorType processMessage(@NotNull Connection connection, @NotNull User user, @NotNull ServerPermissions permissions) throws SQLException {
        try (PreparedStatement deleteClass = connection.prepareStatement("DELETE FROM class WHERE id = ?;")) {
            deleteClass.setInt(1, cid);
            deleteClass.executeUpdate();
        }
        return null;
    }

    public int getClassId() {
        return cid;
    }

    @NotNull
    @Override
    public MessageType getMessageType() {
        return MessageType.DELETE_CLASS;
    }

    @Override
    public boolean checkValid() {
        return cid > 0;
    }
}
