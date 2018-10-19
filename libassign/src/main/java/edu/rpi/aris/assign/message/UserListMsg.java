package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.Perm;
import edu.rpi.aris.assign.ServerPermissions;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;

public class UserListMsg extends Message {

    private final HashSet<User> users = new HashSet<>();

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
                users.add(new User(id, username, fullName, defaultRole));
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

    public HashSet<User> getUsers() {
        return users;
    }

    public static class User implements Comparable<User> {

        public final int uid;
        @NotNull
        public final String username;
        @NotNull
        public final String fullName;
        public final int defaultRole;

        public User(int uid, @NotNull String username, @NotNull String fullName, int defaultRole) {
            this.uid = uid;
            this.username = username;
            this.fullName = fullName;
            this.defaultRole = defaultRole;
        }

        private User() {
            uid = 0;
            username = "";
            fullName = "";
            defaultRole = 0;
        }

        @Contract(value = "null -> false", pure = true)
        @Override
        public boolean equals(Object obj) {
            return obj instanceof User && uid == ((User) obj).uid;
        }

        @Override
        public int hashCode() {
            return uid;
        }

        @Override
        public int compareTo(@NotNull UserListMsg.User o) {
            return username.compareTo(o.username);
        }
    }

}
