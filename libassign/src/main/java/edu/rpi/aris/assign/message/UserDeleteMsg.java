package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.Perm;
import edu.rpi.aris.assign.ServerPermissions;
import edu.rpi.aris.assign.ServerRole;
import edu.rpi.aris.assign.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class UserDeleteMsg extends Message {
    private final int uid;

    public UserDeleteMsg(int uid) {
        super(Perm.USER_DELETE);
        this.uid = uid;
    }

    // DO NOT REMOVE
    private UserDeleteMsg() {
        this(-1);
    }

    @Override
    public @Nullable ErrorType processMessage(@NotNull Connection connection, @NotNull User user, @NotNull ServerPermissions permissions) throws Exception {
        if (user.uid == uid)
            return ErrorType.UNAUTHORIZED;
        try (PreparedStatement selectRole = connection.prepareStatement("SELECT default_role FROM users WHERE id = ?;");
             PreparedStatement deleteUser = connection.prepareStatement("DELETE FROM users WHERE id = ?;")) {
            selectRole.setInt(1, uid);
            ServerRole userRole = null;
            try (ResultSet rs = selectRole.executeQuery()) {
                if (rs.next())
                    userRole = permissions.getRole(rs.getInt(1));
            }
            if (userRole == null)
                return ErrorType.NOT_FOUND;
            if (userRole.getRollRank() < user.defaultRole.getRollRank())
                return ErrorType.UNAUTHORIZED;
            deleteUser.setInt(1, uid);
            deleteUser.executeUpdate();
        }
        return null;
    }

    @Override
    public @NotNull MessageType getMessageType() {
        return MessageType.USER_DELETE;
    }

    @Override
    public boolean checkValid() {
        return uid > 0;
    }

    public int getUid() {
        return uid;
    }
}
