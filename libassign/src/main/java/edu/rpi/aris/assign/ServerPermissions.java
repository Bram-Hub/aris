package edu.rpi.aris.assign;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ServerPermissions {

    private static final Logger log = LogManager.getLogger();

    private transient final ReentrantReadWriteLock lock;

    private final HashMap<Integer, ServerRole> roleMap = new HashMap<>();
    private final HashMap<String, Permission> permissionMap = new HashMap<>();

    public ServerPermissions(Connection connection) throws SQLException {
        this();
        loadPermissions(connection);
    }

    private ServerPermissions() {
        lock = new ReentrantReadWriteLock(true);
    }

    public void reloadPermissions(Connection connection) throws SQLException {
        try {
            lock.writeLock().lock();
            roleMap.clear();
            permissionMap.clear();
            loadPermissions(connection);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void loadPermissions(Connection connection) throws SQLException {
        try (PreparedStatement selectRoles = connection.prepareStatement("SELECT id, name, role_rank FROM role;");
             PreparedStatement selectPerms = connection.prepareStatement("SELECT name, role_id FROM permissions;");
             ResultSet roleRs = selectRoles.executeQuery()) {
            lock.writeLock().lock();
            while (roleRs.next()) {
                ServerRole r = new ServerRole(roleRs.getInt(1), roleRs.getString(2), roleRs.getInt(3));
                roleMap.put(r.getId(), r);
            }
            HashMap<String, Integer> rawPermissions = new HashMap<>();
            try (ResultSet permRs = selectPerms.executeQuery()) {
                while (permRs.next())
                    rawPermissions.put(permRs.getString(1), permRs.getInt(2));
            }
            try (PreparedStatement addPerm = connection.prepareStatement("INSERT INTO permissions (name, role_id) VALUES (?, ?);")) {
                for (Perm p : Perm.values()) {
                    Integer roleId = rawPermissions.get(p.name());
                    if (roleId == null) {
                        ServerRole role = getAdminRole();
                        for (ServerRole r : roleMap.values()) {
                            if (r.getRollRank() > role.getRollRank() && r.getRollRank() <= p.defaultRoleRank)
                                role = r;
                        }
                        addPerm.setString(1, p.name());
                        addPerm.setInt(2, role.getId());
                        addPerm.addBatch();
                        roleId = role.getId();
                    }
                    permissionMap.put(p.name(), new Permission(p.name(), roleId));
                }
                addPerm.executeBatch();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public ServerRole getRole(int roleId) {
        try {
            lock.readLock().lock();
            return roleMap.get(roleId);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Permission getPermission(Perm perm) {
        try {
            lock.readLock().lock();
            return permissionMap.get(perm.name());
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean hasPermission(ServerRole userRole, Perm permission) {
        try {
            lock.readLock().lock();
            return permission != null && hasPermission(userRole, permissionMap.get(permission.name()));
        } finally {
            lock.readLock().unlock();
        }
    }

    private boolean hasPermission(ServerRole userRoll, Permission permission) {
        try {
            lock.readLock().lock();
            return permission != null && hasPermission(userRoll, roleMap.get(permission.getRollId()));
        } finally {
            lock.readLock().unlock();
        }
    }

    private boolean hasPermission(ServerRole userRoll, ServerRole permissionRole) {
        return userRoll != null && permissionRole != null && userRoll.getRollRank() <= permissionRole.getRollRank();
    }

    public boolean hasPermission(User user, Perm perm) {
        try {
            lock.readLock().lock();
            return perm != null && hasPermission(user, permissionMap.get(perm.name()));
        } finally {
            lock.readLock().unlock();
        }
    }

    private boolean hasPermission(User user, Permission permission) {
        return user != null && (user.isAdmin() || hasPermission(user.defaultRole, permission));
    }

    public boolean hasClassPermission(User user, int cid, Perm permission, Connection connection) {
        try {
            lock.readLock().lock();
            return permission != null && hasClassPermission(user, cid, permissionMap.get(permission.name()), connection);
        } finally {
            lock.readLock().unlock();
        }
    }

    private boolean hasClassPermission(User user, int cid, Permission permission, Connection connection) {
        if (user.isAdmin())
            return true;
        if (permission == null)
            return false;
        try (PreparedStatement selectRoleId = connection.prepareStatement("SELECT role_id FROM user_class WHERE user_id = ? AND class_id = ?;")) {
            selectRoleId.setInt(1, user.uid);
            selectRoleId.setInt(2, cid);
            try (ResultSet rs = selectRoleId.executeQuery()) {
                lock.readLock().lock();
                if (rs.next()) {
                    ServerRole role = roleMap.get(rs.getInt(1));
                    return hasPermission(role, permission);
                }
            } finally {
                lock.readLock().unlock();
            }
        } catch (SQLException e) {
            log.error("Failed to check user permissions", e);
        }
        return false;
    }

    public ServerRole getAdminRole() {
        try {
            lock.readLock().lock();
            for (ServerRole r : roleMap.values())
                if (r.getRollRank() <= 0)
                    return r;
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    public ServerRole getLowestRole() {
        try {
            lock.readLock().lock();
            ServerRole role = null;
            for (ServerRole r : roleMap.values())
                if (role == null || r.getRollRank() > role.getRollRank())
                    role = r;
            return role;
        } finally {
            lock.readLock().unlock();
        }
    }

    public Collection<ServerRole> getRoles() {
        return roleMap.values();
    }
}
