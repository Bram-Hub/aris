package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.AuthType;
import edu.rpi.aris.assign.LoginAuth;
import edu.rpi.aris.assign.Perm;
import edu.rpi.aris.assign.ServerPermissions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;

public class UserListMsg extends Message {

    private static final Logger log = LogManager.getLogger();
    private final HashSet<MsgUtil.UserInfo> users = new HashSet<>();

    public UserListMsg() {
        super(Perm.USER_LIST);
    }

    @Nullable
    @Override
    public ErrorType processMessage(@NotNull Connection connection, @NotNull edu.rpi.aris.assign.User user, @NotNull ServerPermissions permissions) throws Exception {
        try (PreparedStatement selectUsers = connection.prepareStatement("SELECT id, username, full_name, default_role, auth_type FROM users;");
             ResultSet rs = selectUsers.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt(1);
                String username = rs.getString(2);
                String fullName = rs.getString(3);
                int defaultRole = rs.getInt(4);
                AuthType authType;
                try {
                    authType = AuthType.valueOf(rs.getString(5));
                } catch (IllegalArgumentException e) {
                    log.error("Invalid AuthType in database: " + rs.getString(5), e);
                    return ErrorType.UNKNOWN_ERROR;
                }
                LoginAuth auth = LoginAuth.getAuthForType(authType);
                users.add(new MsgUtil.UserInfo(id, username, fullName, defaultRole, authType, auth != null && auth.isLocalAuth()));
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
