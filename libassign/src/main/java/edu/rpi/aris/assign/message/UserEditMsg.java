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
import java.sql.SQLException;

public class UserEditMsg extends Message {

    private final int uid;
    private final String newName;
    private final int newDefaultRole;

    public UserEditMsg(int uid, String newName, int newDefaultRole) {
        super(Perm.USER_EDIT, false);
        this.uid = uid;
        this.newName = newName;
        this.newDefaultRole = newDefaultRole;
    }

    public UserEditMsg(int uid, String newName) {
        this(uid, newName, -1);
    }

    public UserEditMsg(int uid, int newDefaultRole) {
        this(uid, null, newDefaultRole);
    }

    private UserEditMsg() {
        this(-1, null, -1);
    }

    private void setName(Connection connection) throws SQLException {
        if (newName == null)
            return;
        try (PreparedStatement updateName = connection.prepareStatement("UPDATE users SET full_name=? WHERE id=?;")) {
            updateName.setString(1, newName);
            updateName.setInt(2, uid);
            updateName.executeUpdate();
        }
    }

    private ErrorType setDefaultRole(Connection connection, User user, ServerPermissions permissions) throws SQLException {
        if (newDefaultRole > 0) {
            if (uid == user.uid)
                return ErrorType.CANT_CHANGE_OWN_ROLE;
            try (PreparedStatement updateRole = connection.prepareStatement("UPDATE users SET default_role=? WHERE id=?;")) {
                ServerRole newRole = permissions.getRole(newDefaultRole);
                if (newRole == null)
                    return ErrorType.NOT_FOUND;
                else if (newRole.getRollRank() < user.defaultRole.getRollRank())
                    return ErrorType.UNAUTHORIZED;
                updateRole.setInt(1, newDefaultRole);
                updateRole.setInt(2, uid);
                updateRole.executeUpdate();
            }
        }
        return null;
    }

    @Override
    public @Nullable ErrorType processMessage(@NotNull Connection connection, @NotNull User user, @NotNull ServerPermissions permissions) throws Exception {
        ServerRole currentRole = null;
        try (PreparedStatement selectUserInfo = connection.prepareStatement("SELECT default_role FROM users WHERE id=?;")) {
            selectUserInfo.setInt(1, uid);
            try (ResultSet rs = selectUserInfo.executeQuery()) {
                if (rs.next())
                    currentRole = permissions.getRole(rs.getInt(1));
            }
        }
        if (currentRole == null)
            return ErrorType.NOT_FOUND;
        else if (currentRole.getRollRank() < user.defaultRole.getRollRank())
            return ErrorType.UNAUTHORIZED;
        ErrorType err;
        setName(connection);
        if ((err = setDefaultRole(connection, user, permissions)) != null)
            return err;
        return null;
    }

    @Override
    public @NotNull MessageType getMessageType() {
        return MessageType.USER_EDIT;
    }

    @Override
    public boolean checkValid() {
        return uid > 0;
    }

    public String getNewName() {
        return newName;
    }

    public int getNewDefaultRole() {
        return newDefaultRole;
    }
}
