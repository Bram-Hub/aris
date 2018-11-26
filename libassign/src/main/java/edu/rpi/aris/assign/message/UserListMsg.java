package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.Perm;
import edu.rpi.aris.assign.ServerPermissions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;

public class UserListMsg extends Message {

    private final HashSet<MsgUtil.UserInfo> users = new HashSet<>();

    public UserListMsg() {
        super(Perm.USER_LIST);
    }

    @Nullable
    @Override
    public ErrorType processMessage(@NotNull Connection connection, @NotNull edu.rpi.aris.assign.User user, @NotNull ServerPermissions permissions) throws Exception {
        try (PreparedStatement selectUsers = connection.prepareStatement("SELECT id, username, full_name, default_role FROM users;");
             ResultSet rs = selectUsers.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt(1);
                String username = rs.getString(2);
                String fullName = rs.getString(3);
                int defaultRole = rs.getInt(4);
                users.add(new MsgUtil.UserInfo(id, username, fullName, defaultRole));
            }
        }
        return null;
    }

    @NotNull
    @Override
    public MessageType getMessageType() {
        return MessageType.USER_LIST;
    }

    @Override
    public boolean checkValid() {
        return true;
    }

    public HashSet<MsgUtil.UserInfo> getUsers() {
        return users;
    }

}
