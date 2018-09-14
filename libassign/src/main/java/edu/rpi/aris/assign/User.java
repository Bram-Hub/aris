package edu.rpi.aris.assign;

public class User {

    public final int uid;
    public final String username;
    public final ServerRole defaultRole;
    private boolean resetPass;

    public User(int uid, String username, ServerRole defaultRole, boolean resetPass) {
        this.uid = uid;
        this.username = username;
        this.defaultRole = defaultRole;
        this.resetPass = resetPass;
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
