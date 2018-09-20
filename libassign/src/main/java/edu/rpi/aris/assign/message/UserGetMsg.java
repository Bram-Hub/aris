package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.Perm;
import edu.rpi.aris.assign.ServerPermissions;
import edu.rpi.aris.assign.ServerRole;
import edu.rpi.aris.assign.User;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class UserGetMsg extends Message {

    private int userId;
    private int defaultRole;
    private ServerPermissions permissions;
    private HashMap<Integer, Pair<String, Integer>> classes = new HashMap<>();

    public UserGetMsg() {
        super(Perm.USER_GET);
    }

    public ServerRole getDefaultRole() {
        return permissions == null ? null : permissions.getRole(defaultRole);
    }

    public HashMap<Integer, Pair<String, Integer>> getClasses() {
        return classes;
    }

    public int getUserId() {
        return userId;
    }

    public ServerPermissions getPermissions() {
        return permissions;
    }

    @Override
    public ErrorType processMessage(Connection connection, User user, ServerPermissions permissions) throws SQLException {
        userId = user.uid;
        this.permissions = permissions;
        try (PreparedStatement getInfo = connection.prepareStatement(user.isAdmin() ? "SELECT id, name FROM class;" : "SELECT c.id, c.name, uc.role_id FROM class c, users u, user_class uc WHERE u.id = uc.user_id AND c.id = uc.class_id AND u.id = ?")) {
            defaultRole = user.defaultRole.getId();
            if (!user.isAdmin())
                getInfo.setInt(1, userId);
            try (ResultSet infoRs = getInfo.executeQuery()) {
                while (infoRs.next())
                    classes.put(infoRs.getInt(1), new ImmutablePair<>(infoRs.getString(2), user.isAdmin() ? permissions.getAdminRole().getId() : permissions.getRole(infoRs.getInt(3)).getId()));
            }
        }
        return null;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.GET_USER_INFO;
    }

    @Override
    public boolean checkValid() {
        for (Map.Entry<Integer, Pair<String, Integer>> c : classes.entrySet())
            if (c.getKey() == null || c.getValue() == null || c.getValue().getLeft() == null)
                return false;
        return true;
    }

}
