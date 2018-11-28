package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.*;
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
    private final Pair<Integer, Integer> classRole;

    public UserEditMsg(int uid, String newName, int newDefaultRole, Pair<Integer, Integer> classRole) {
        super(Perm.USER_EDIT, false);
        this.uid = uid;
        this.newName = newName;
        this.newDefaultRole = newDefaultRole;
        this.classRole = classRole;
    }

    public UserEditMsg(int uid, Pair<Integer, Integer> classRole) {
        this(uid, null, -1, classRole);
    }

    public UserEditMsg(int uid, String newName) {
        this(uid, newName, -1, null);
    }

    public UserEditMsg(int uid, int newDefaultRole) {
        this(uid, null, newDefaultRole, null);
    }

    private UserEditMsg() {
        this(-1, null, -1, null);
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
            ServerRole newRole = permissions.getRole(newDefaultRole);
            if (newRole == null)
                return ErrorType.NOT_FOUND;
            else if (newRole.getRollRank() < user.defaultRole.getRollRank())
                return ErrorType.UNAUTHORIZED;
            try (PreparedStatement updateRole = connection.prepareStatement("UPDATE users SET default_role=? WHERE id=?;")) {
                updateRole.setInt(1, newDefaultRole);
                updateRole.setInt(2, uid);
                updateRole.executeUpdate();
            }
        }
        return null;
    }

    private ErrorType setClassRole(Connection connection, User user, ServerPermissions permissions) throws SQLException {
        if (classRole == null)
            return null;
        ServerRole newRole = permissions.getRole(classRole.getSecond());
        if (newRole == null)
            return ErrorType.NOT_FOUND;
        if (uid == user.uid)
            return ErrorType.CANT_CHANGE_OWN_ROLE;
        try (PreparedStatement getRole = connection.prepareStatement("SELECT role_id FROM user_class WHERE user_id = ? AND class_id = ?;");
             PreparedStatement setRole = connection.prepareStatement("UPDATE user_class SET role_id = ? WHERE user_id = ? AND class_id = ?;")) {
            getRole.setInt(1, user.uid);
            getRole.setInt(2, classRole.getFirst());
            ServerRole userRole = null;
            try (ResultSet rs = getRole.executeQuery()) {
                if (rs.next())
                    userRole = permissions.getRole(rs.getInt(1));
                if (userRole == null)
                    userRole = user.defaultRole;
            }
            if (newRole.getRollRank() < userRole.getRollRank() && !user.isAdmin())
                return ErrorType.UNAUTHORIZED;
            setRole.setInt(1, newRole.getId());
            setRole.setInt(2, uid);
            setRole.setInt(3, classRole.getFirst());
            setRole.executeUpdate();
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
        if ((err = setClassRole(connection, user, permissions)) != null)
            return err;
        return null;
    }

    @Override
    public @NotNull MessageType getMessageType() {
        return MessageType.USER_EDIT;
    }

    @Override
    public boolean checkValid() {
        return classRole != null ? uid > 0 && classRole.getFirst() > 0 && classRole.getSecond() > 0 : uid > 0;
    }

    public String getNewName() {
        return newName;
    }

    public int getNewDefaultRole() {
        return newDefaultRole;
    }

    public Pair<Integer, Integer> getNewClassRole() {
        return classRole;
    }
}
