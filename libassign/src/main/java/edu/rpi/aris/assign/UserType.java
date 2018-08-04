package edu.rpi.aris.assign;

import java.util.HashMap;

public enum UserType {

    ADMIN(0, "admin"),
    INSTRUCTOR(1, "instructor"),
    TA(2, "ta"),
    STUDENT(3, "student");

    private static final HashMap<String, UserType> readableNameMap = new HashMap<>();

    static {
        for (UserType t : UserType.values()) {
            readableNameMap.put(t.readableName, t);
        }
    }

    public final int permissionLevel;
    public final String readableName;

    UserType(int permissionLevel, String readableName) {
        this.permissionLevel = permissionLevel;
        this.readableName = readableName;
    }

    public static boolean hasPermission(User requester, UserType minLevel) {
        return requester.userType.permissionLevel <= minLevel.permissionLevel;
    }

    public static UserType getFromReadableName(String readableName) {
        return readableNameMap.get(readableName);
    }

}
