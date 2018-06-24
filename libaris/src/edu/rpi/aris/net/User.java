package edu.rpi.aris.net;

public class User {

    public final int uid;
    public final String username;
    public final String userType;

    public User(int uid, String username, String userType) {
        this.uid = uid;
        this.username = username;
        this.userType = userType;
    }

}
