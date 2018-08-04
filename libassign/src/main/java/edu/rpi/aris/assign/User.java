package edu.rpi.aris.assign;

public class User {

    public final int uid;
    public final String username;
    public final UserType userType;

    public User(int uid, String username, UserType userType) {
        this.uid = uid;
        this.username = username;
        this.userType = userType;
    }

}
