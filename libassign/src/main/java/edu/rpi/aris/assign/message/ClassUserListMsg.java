package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;

public class ClassUserListMsg extends Message implements ClassMessage {

    private static final Logger log = LogManager.getLogger();
    private final int cid;
    private final HashMap<Integer, Pair<String, String>> usersNotInClass = new HashMap<>();
    private final HashMap<Integer, MsgUtil.UserInfo> userInClass = new HashMap<>();

    public ClassUserListMsg(int cid) {
        super(Perm.CLASS_EDIT);
        this.cid = cid;
    }

    // DO NOT REMOVE: required for gson
    private ClassUserListMsg() {
        this(-1);
    }

    @Override
    public int getClassId() {
        return cid;
    }

    @Override
    public @Nullable ErrorType processMessage(@NotNull Connection connection, @NotNull User user, @NotNull ServerPermissions permissions) throws Exception {
        try (PreparedStatement selectUsers = connection.prepareStatement("SELECT id, username, full_name FROM users;");
             PreparedStatement selectInClass = connection.prepareStatement("SELECT u.id, u.username, u.full_name, uc.role_id, u.auth_type FROM users u, user_class uc WHERE uc.user_id = u.id AND uc.class_id = ?;")) {
            try (ResultSet rs = selectUsers.executeQuery()) {
                while (rs.next()) {
                    int uid = rs.getInt(1);
                    String username = rs.getString(2);
                    String fullName = rs.getString(3);
                    usersNotInClass.put(uid, new Pair<>(username, fullName));
                }
            }
            selectInClass.setInt(1, cid);
            try (ResultSet rs = selectInClass.executeQuery()) {
                while (rs.next()) {
                    int uid = rs.getInt(1);
                    String username = rs.getString(2);
                    String fullName = rs.getString(3);
                    int classRole = rs.getInt(4);
                    AuthType authType;
                    try {
                        authType = AuthType.valueOf(rs.getString(5));
                    } catch (IllegalArgumentException e) {
                        log.error("Invalid AuthType in database: " + rs.getString(5), e);
                        return ErrorType.UNKNOWN_ERROR;
                    }
                    LoginAuth auth = LoginAuth.getAuthForType(authType);
                    userInClass.put(uid, new MsgUtil.UserInfo(uid, username, fullName, classRole, authType, auth != null && auth.canReset()));
                    usersNotInClass.remove(uid);
                }
            }
        }
        return null;
    }

    @Override
    public @NotNull MessageType getMessageType() {
        return MessageType.CLASS_USER_LIST;
    }

    @Override
    public boolean checkValid() {
        return cid > 0;
    }

    public HashMap<Integer, Pair<String, String>> getUsersNotInClass() {
        return usersNotInClass;
    }

    public HashMap<Integer, MsgUtil.UserInfo> getUserInClass() {
        return userInClass;
    }
}
