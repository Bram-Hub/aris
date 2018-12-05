package edu.rpi.aris.assign;

import edu.rpi.aris.assign.message.ConnectionInitMsg;

public class User {

    public final int uid;
    public final String username;
    public final ServerRole defaultRole;
    public final AuthType authType;
    public final boolean canChangePassword;
    private boolean resetPass;

    public User(int uid, String username, ServerRole defaultRole, AuthType authType, boolean resetPass, boolean canChangePassword) {
        this.uid = uid;
        this.username = username;
        this.defaultRole = defaultRole;
        this.authType = authType;
        this.resetPass = resetPass && canChangePassword;
        this.canChangePassword = canChangePassword;
    }

    public User(ConnectionInitMsg msg, String username) {
        this(msg.getUserId(), username, msg.getDefaultRole(), msg.getUserAuthType(), false, true);//msg.canChangePassword());
    }

    public boolean requireReset() {
        return resetPass;
    }

    public void resetPass() {
        resetPass = false;
    }

    public boolean isAdmin() {
        return defaultRole.getRollRank() <= 0;
    }

}
