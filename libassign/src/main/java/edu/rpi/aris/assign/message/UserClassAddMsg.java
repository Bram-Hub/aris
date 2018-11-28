package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.Perm;
import edu.rpi.aris.assign.ServerPermissions;
import edu.rpi.aris.assign.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class UserClassAddMsg extends Message implements ClassMessage {

    private final int cid;
    private final HashSet<Integer> userIds = new HashSet<>();
    private final HashMap<Integer, Integer> roleIds = new HashMap<>();

    public UserClassAddMsg(int cid, Collection<Integer> userIds) {
        super(Perm.CLASS_EDIT);
        this.cid = cid;
        if (userIds != null)
            this.userIds.addAll(userIds);
    }

    //DO NOT REMOVE
    private UserClassAddMsg() {
        this(-1, null);
    }

    @Override
    public int getClassId() {
        return cid;
    }

    @Override
    public @Nullable ErrorType processMessage(@NotNull Connection connection, @NotNull User user, @NotNull ServerPermissions permissions) throws Exception {
        try (PreparedStatement insertUsers = connection.prepareStatement("INSERT INTO user_class (user_id, class_id, role_id) VALUES (?, ?, (SELECT default_role FROM users WHERE id = ?)) ON CONFLICT (user_id, class_id) DO NOTHING;");
             PreparedStatement selectRoles = connection.prepareStatement("SELECT user_id, role_id FROM user_class WHERE class_id = ? AND user_id = ANY (?);")) {
            insertUsers.setInt(2, cid);
            for (int uid : userIds) {
                insertUsers.setInt(1, uid);
                insertUsers.setInt(3, uid);
                insertUsers.addBatch();
            }
            insertUsers.executeBatch();
            selectRoles.setInt(1, cid);
            Array array = connection.createArrayOf("INTEGER", userIds.toArray());
            selectRoles.setArray(2, array);
            try (ResultSet rs = selectRoles.executeQuery()) {
                while (rs.next())
                    roleIds.put(rs.getInt(1), rs.getInt(2));
            }
        }
        return null;
    }

    @Override
    public @NotNull MessageType getMessageType() {
        return MessageType.USER_CLASS_ADD;
    }

    @Override
    public boolean checkValid() {
        return cid > 0 && userIds.size() > 0;
    }

    public HashMap<Integer, Integer> getRoleIds() {
        return roleIds;
    }

    public Set<Integer> getUserIds() {
        return userIds;
    }
}
