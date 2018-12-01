package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.*;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;

public class BatchUserImportMsg extends Message {

    private final int addToClass;
    private final ArrayList<Triple<String, String, String>> toAdd = new ArrayList<>();
    private final ArrayList<MsgUtil.UserInfo> added = new ArrayList<>();

    public BatchUserImportMsg(int addToClass) {
        super(Perm.BATCH_USER_IMPORT, true);
        this.addToClass = addToClass;
    }

    public BatchUserImportMsg() {
        this(-1);
    }

    @Override
    public @Nullable ErrorType processMessage(@NotNull Connection connection, @NotNull User user, @NotNull ServerPermissions permissions) throws Exception {
        try {
            if (!permissions.hasPermission(user, Perm.USER_CREATE))
                return ErrorType.UNAUTHORIZED;
            if (addToClass > 0 && !permissions.hasClassPermission(user, addToClass, Perm.CLASS_EDIT, connection))
                return ErrorType.UNAUTHORIZED;
            int roleId = permissions.getLowestRole().getId();
            try (PreparedStatement addUser = connection.prepareStatement("INSERT INTO users (username, salt, password_hash, force_reset, default_role, full_name) VALUES(?, ?, ?, ?, ?, ?) ON CONFLICT (username) DO NOTHING RETURNING id;");
                 PreparedStatement addToClass = connection.prepareStatement("INSERT INTO user_class (user_id, class_id, role_id) VALUES (?, ?, ?) ON CONFLICT (user_id, class_id) DO NOTHING;")) {
                for (Triple<String, String, String> t : toAdd) {
                    String uname = t.getLeft();
                    String fullName = t.getMiddle();
                    String pass = t.getRight();
                    if (isValidUser(uname, fullName, pass)) {
                        uname = uname.toLowerCase();
                        Pair<String, String> sh = DBUtils.getSaltAndHash(pass);
                        addUser.setString(1, uname);
                        addUser.setString(2, sh.getKey());
                        addUser.setString(3, sh.getValue());
                        addUser.setBoolean(4, true);
                        addUser.setInt(5, roleId);
                        addUser.setString(6, fullName);
                        try (ResultSet rs = addUser.executeQuery()) {
                            if (rs.next()) {
                                int uid = rs.getInt(1);
                                added.add(new MsgUtil.UserInfo(uid, uname, fullName, roleId));
                                if (this.addToClass > 0) {
                                    addToClass.setInt(1, uid);
                                    addToClass.setInt(2, this.addToClass);
                                    addToClass.setInt(3, roleId);
                                    addToClass.addBatch();
                                }
                            }
                        }
                    }
                }
                addToClass.executeBatch();
            }
            return null;
        } finally {
            //clear this list to save on network usage
            toAdd.clear();
        }
    }

    @Override
    public @NotNull MessageType getMessageType() {
        return MessageType.BATCH_USER_IMPORT;
    }

    @Override
    public boolean checkValid() {
        return true;
    }

    public void addUser(String username, String fullName, String password) {
        toAdd.add(new Triple<>(username, fullName, password));
    }

    private boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }

    public ArrayList<MsgUtil.UserInfo> getAdded() {
        return added;
    }

    public ArrayList<Triple<String, String, String>> getToAdd() {
        return toAdd;
    }

    public int getAddToClass() {
        return addToClass;
    }

    private boolean isValidUser(String username, String fullName, String password) {
        return !isEmpty(username) && !isEmpty(fullName) && !isEmpty(password);
    }

}
