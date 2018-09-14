package edu.rpi.aris.assign;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

public class ServerPermissions {

    private static final Logger log = LogManager.getLogger();

    private final HashMap<Integer, ServerRole> rollMap = new HashMap<>();
    private final HashMap<String, Permission> permissionMap = new HashMap<>();

    public ServerPermissions(Connection connection) throws SQLException {
        loadPermissions(connection);
    }

    private void loadPermissions(Connection connection) throws SQLException {
        try (PreparedStatement selectRoles = connection.prepareStatement("")) {

        }
    }

    public ServerRole getRole(int roleId) {
        return rollMap.get(roleId);
    }

    public Permission getPermission(Perm perm) {
        return perm == null ? null : getPermission(perm.name());
    }

    public Permission getPermission(String permissionName) {
        return permissionMap.get(permissionName);
    }

    public boolean hasPermission(int userRoll, String permissionName) {
        return hasPermission(rollMap.get(userRoll), permissionMap.get(permissionName));
    }

    public boolean hasPermission(ServerRole userRoll, Permission permission) {
        if (permission == null)
            return false;
        return hasPermission(userRoll, rollMap.get(permission.getRollId()));
    }

    public boolean hasPermission(ServerRole userRoll, ServerRole permissionRole) {
        if (userRoll == null || permissionRole == null)
            return false;
        return userRoll.getRollRank() <= permissionRole.getRollRank();
    }

    public boolean hasClassPermission(int uid, int cid, Permission permission, Connection connection) {
        if (permission == null)
            return false;
        try (PreparedStatement selectRoleId = connection.prepareStatement("SELECT role_id FROM user_class WHERE user_id = ? AND class_id = ?;")) {
            selectRoleId.setInt(1, uid);
            selectRoleId.setInt(2, cid);
            try (ResultSet rs = selectRoleId.executeQuery()) {
                if (rs.next()) {
                    ServerRole role = rollMap.get(rs.getInt(1));
                    return hasPermission(role, permission);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to check user permissions", e);
        }
        return false;
    }

    public ServerRole getAdminRole() {
        for (ServerRole r : rollMap.values())
            if (r.getRollRank() <= 0)
                return r;
        return null;
    }

    public ServerRole getLowestRole() {
        ServerRole role = null;
        for (ServerRole r : rollMap.values())
            if (role == null || r.getRollRank() > role.getRollRank())
                role = r;
        return role;
    }
}
