package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.Perm;
import edu.rpi.aris.assign.ServerPermissions;
import edu.rpi.aris.assign.ServerRole;
import edu.rpi.aris.assign.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Map;

public class PermissionEditMsg extends Message {

    private HashMap<Perm, Integer> permMap = new HashMap<>();

    public PermissionEditMsg() {
        super(Perm.PERMISSIONS_EDIT);
    }

    @Override
    public @Nullable ErrorType processMessage(@NotNull Connection connection, @NotNull User user, @NotNull ServerPermissions permissions) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE permissions SET role_id = ? WHERE name = ?")) {
            for (Map.Entry<Perm, Integer> e : permMap.entrySet()) {
                statement.setInt(1, e.getValue());
                statement.setString(2, e.getKey().name());
                statement.addBatch();
            }
            statement.executeBatch();
        }
        permissions.reloadPermissions(connection);
        return null;
    }

    public void addPermission(Perm perm, ServerRole role) {
        if (perm == null || role == null)
            return;
        permMap.put(perm, role.getId());
    }

    @Override
    public @NotNull MessageType getMessageType() {
        return MessageType.EDIT_PERMISSION;
    }

    @Override
    public boolean checkValid() {
        return !permMap.containsKey(null) && !permMap.containsValue(null);
    }

    public HashMap<Perm, Integer> getPermMap() {
        return permMap;
    }
}
