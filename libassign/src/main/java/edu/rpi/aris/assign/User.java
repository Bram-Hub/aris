package edu.rpi.aris.assign;

public class User {

    public final int uid;
    public final String username;
    public final UserType userType;
    private boolean resetPass;

    public User(int uid, String username, UserType userType, boolean resetPass) {
        this.uid = uid;
        this.username = username;
        this.userType = userType;
        this.resetPass = resetPass;
    }

    public boolean requireReset() {
        return resetPass;
    }

    public void resetPass() {
        resetPass = false;
    }

}
