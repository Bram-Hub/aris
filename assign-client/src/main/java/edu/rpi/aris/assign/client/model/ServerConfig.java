package edu.rpi.aris.assign.client.model;

import edu.rpi.aris.assign.ServerPermissions;
import edu.rpi.aris.assign.client.ConfigProp;

import java.util.HashMap;
import java.util.prefs.Preferences;

public class ServerConfig {

    public static final String DEFAULT_ASSIGNMENT_DUE_TIME = "default_assignment_due_time";
    private static final Preferences preferences = Preferences.userNodeForPackage(ServerConfig.class);
    private static final HashMap<String, ConfigProp<String>> stringProps = new HashMap<>();
    private static final HashMap<String, ConfigProp<Integer>> intProps = new HashMap<>();
    private static final HashMap<String, ConfigProp<Boolean>> boolProps = new HashMap<>();
    private static ServerPermissions permissions;

    static {
        setDefaults();
    }

    private ServerConfig() {
    }

    private static void setDefaults() {
        stringProps.put(DEFAULT_ASSIGNMENT_DUE_TIME, new ConfigProp<>(preferences, DEFAULT_ASSIGNMENT_DUE_TIME, preferences.get(DEFAULT_ASSIGNMENT_DUE_TIME, "11:59 pm")));
    }

    public static Integer getIntProp(String key) {
        ConfigProp<Integer> p = intProps.get(key);
        return p == null ? null : p.getValue();
    }

    public static void setIntProp(String key, int value) {
        intProps.computeIfAbsent(key, k -> new ConfigProp<>(preferences, key, value)).setValue(value);
    }

    public static String getStringProp(String key) {
        ConfigProp<String> p = stringProps.get(key);
        return p == null ? null : p.getValue();
    }

    public static void setStringProp(String key, String value) {
        stringProps.computeIfAbsent(key, k -> new ConfigProp<>(preferences, key, value)).setValue(value);
    }

    public static Boolean getBoolProp(String key) {
        ConfigProp<Boolean> p = boolProps.get(key);
        return p == null ? null : p.getValue();
    }

    public static void setBoolProp(String key, boolean value) {
        boolProps.computeIfAbsent(key, k -> new ConfigProp<>(preferences, key, value)).setValue(value);
    }

    public static ServerPermissions getPermissions() {
        return permissions;
    }

    public static void setPermissions(ServerPermissions permissions) {
        ServerConfig.permissions = permissions;
    }
}
