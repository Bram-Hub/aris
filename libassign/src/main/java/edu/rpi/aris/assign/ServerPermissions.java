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

    private final HashMap<Integer, ServerRole> roleMap = new HashMap<>();
    private final HashMap<String, Permission> permissionMap = new HashMap<>();

    public ServerPermissions(Connection connection) throws SQLException {
        loadPermissions(connection);
    }

    private void loadPermissions(Connection connection) throws SQLException {
        try (PreparedStatement selectRoles = connection.prepareStatement("SELECT id, name, role_rank FROM role;");
             PreparedStatement selectPerms = connection.prepareStatement("SELECT name, role_id FROM permissions;");
             ResultSet roleRs = selectRoles.executeQuery()) {
            while (roleRs.next()) {
                ServerRole r = new ServerRole(roleRs.getInt(1), roleRs.getString(2), roleRs.getInt(3));
                roleMap.put(r.getId(), r);
            }
            HashMap<String, Integer> rawPermissions = new HashMap<>();
            try (ResultSet permRs = selectPerms.executeQuery()) {
                while (permRs.next())
                    rawPermissions.put(permRs.getString(1), permRs.getInt(2));
            }
            if (rawPermissions.size() == 0) {
                loadDefaultPermissions(connection);
                return;
            }
            for (Perm p : Perm.values()) {
                Integer roleId = rawPermissions.get(p.name());
                if (roleId == null) {
                    ServerRole role = getAdminRole();
                    for (ServerRole r : roleMap.values()) {
                        if (r.getRollRank() > role.getRollRank() && r.getRollRank() <= p.defaultRoleRank)
                            role = r;
                    }
                    roleId = role.getId();
                }
                permissionMap.put(p.name(), new Permission(p.name(), roleId));
            }
        }
    }

    private void loadDefaultPermissions(Connection connection) throws SQLException {
        try (PreparedStatement addPerm = connection.prepareStatement("INSERT INTO permissions (name, role_id) VALUES (?, ?);")) {
            for (Perm p : Perm.values()) {
                ServerRole role = getAdminRole();
                for (ServerRole r : roleMap.values()) {
                    if (r.getRollRank() > role.getRollRank() && r.getRollRank() <= p.defaultRoleRank)
                        role = r;
                }
                addPerm.setString(1, p.name());
                addPerm.setInt(2, role.getId());
                addPerm.addBatch();
                permissionMap.put(p.name(), new Permission(p.name(), role.getId()));
            }
            addPerm.executeBatch();
        }
    }

    public ServerRole getRole(int roleId) {
        return roleMap.get(roleId);
    }

    public Permission getPermission(Perm perm) {
        return perm == null ? null : getPermission(perm.name());
    }

    public Permission getPermission(String permissionName) {
        return permissionMap.get(permissionName);
    }

    public boolean hasPermission(int userRoll, String permissionName) {
        return hasPermission(roleMap.get(userRoll), permissionMap.get(permissionName));
    }

    public boolean hasPermission(ServerRole userRole, Perm permission) {
        return permission != null && hasPermission(userRole, permissionMap.get(permission.name()));
    }

    public boolean hasPermission(ServerRole userRoll, Permission permission) {
        if (permission == null)
            return false;
        return hasPermission(userRoll, roleMap.get(permission.getRollId()));
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
                    ServerRole role = roleMap.get(rs.getInt(1));
                    return hasPermission(role, permission);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to check user permissions", e);
        }
        return false;
    }

    public ServerRole getAdminRole() {
        for (ServerRole r : roleMap.values())
            if (r.getRollRank() <= 0)
                return r;
        return null;
    }

    public ServerRole getLowestRole() {
        ServerRole role = null;
        for (ServerRole r : roleMap.values())
            if (role == null || r.getRollRank() > role.getRollRank())
                role = r;
        return role;
    }

}
