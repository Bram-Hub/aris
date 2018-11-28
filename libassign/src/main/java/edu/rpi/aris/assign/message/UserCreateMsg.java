package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;

public class UserCreateMsg extends Message {

    private final String username;
    private final String fullName;
    private final int defaultRole;
    private String password;
    private int uid;

    public UserCreateMsg(String username, String fullName, String password, int defaultRole) {
        super(Perm.USER_CREATE);
        this.username = username;
        this.fullName = fullName;
        this.password = password;
        this.defaultRole = defaultRole;
    }

    // DO NOT REMOVE
    private UserCreateMsg() {
        this(null, null, null, -1);
    }

    @Override
    public @Nullable ErrorType processMessage(@NotNull Connection connection, @NotNull User user, @NotNull ServerPermissions permissions) throws Exception {
        try {
            ServerRole role = permissions.getRole(defaultRole);
            if (role == null)
                return ErrorType.NOT_FOUND;
            if (role.getRollRank() < user.defaultRole.getRollRank())
                return ErrorType.UNAUTHORIZED;
            if (password == null || password.length() == 0)
                return ErrorType.AUTH_WEAK_PASS;
            uid = DBUtils.createUser(connection, username, password, fullName, defaultRole, true).getValue();
            if (uid <= 0)
                return ErrorType.USER_EXISTS;
            return null;
        } finally {
            password = null;
        }
    }

    @Override
    public @NotNull MessageType getMessageType() {
        return MessageType.USER_CREATE;
    }

    @Override
    public boolean checkValid() {
        return defaultRole > 0 && fullName != null && username != null && username.length() > 0;
    }

    public int getUid() {
        return uid;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public int getDefaultRole() {
        return defaultRole;
    }
}
