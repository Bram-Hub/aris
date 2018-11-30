package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.Perm;
import edu.rpi.aris.assign.ServerPermissions;
import edu.rpi.aris.assign.User;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.util.ArrayList;

public class BatchUserImportMsg extends Message {

    private final int addToClass;
    private final ArrayList<Triple<String, String, String>> toAdd = new ArrayList<>();

    public BatchUserImportMsg(int addToClass) {
        super(Perm.BATCH_USER_IMPORT, true);
        this.addToClass = addToClass;
    }

    public BatchUserImportMsg() {
        this(-1);
    }

    @Override
    public @Nullable ErrorType processMessage(@NotNull Connection connection, @NotNull User user, @NotNull ServerPermissions permissions) throws Exception {
        if (!permissions.hasPermission(user, Perm.USER_CREATE))
            return ErrorType.UNAUTHORIZED;
        if (addToClass > 0 && !permissions.hasClassPermission(user, addToClass, Perm.CLASS_EDIT, connection))
            return ErrorType.UNAUTHORIZED;
        return null;
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
        toAdd.add(new ImmutableTriple<>(username, fullName, password));
    }

}
