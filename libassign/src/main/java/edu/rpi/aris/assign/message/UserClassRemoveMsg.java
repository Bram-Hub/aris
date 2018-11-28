package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.Perm;
import edu.rpi.aris.assign.ServerPermissions;
import edu.rpi.aris.assign.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class UserClassRemoveMsg extends Message implements ClassMessage {

    private final int cid;
    private final int uid;

    public UserClassRemoveMsg(int cid, int uid) {
        super(Perm.CLASS_EDIT);
        this.cid = cid;
        this.uid = uid;
    }

    //DO NOT REMOVE
    private UserClassRemoveMsg() {
        this(-1, -1);
    }

    @Override
    public int getClassId() {
        return cid;
    }

    @Override
    public @Nullable ErrorType processMessage(@NotNull Connection connection, @NotNull User user, @NotNull ServerPermissions permissions) throws Exception {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM user_class WHERE user_id = ? AND class_id = ?;")) {
            delete.setInt(1, uid);
            delete.setInt(2, cid);
            delete.executeUpdate();
        }
        return null;
    }

    @Override
    public @NotNull MessageType getMessageType() {
        return MessageType.USER_CLASS_REMOVE;
    }

    @Override
    public boolean checkValid() {
        return cid > 0 && uid > 0;
    }

    public int getUid() {
        return uid;
    }
}
