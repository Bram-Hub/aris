package edu.rpi.aris.assign.client.model;

import edu.rpi.aris.assign.ServerPermissions;
import edu.rpi.aris.assign.ServerRole;
import javafx.util.StringConverter;

import java.util.HashMap;
import java.util.prefs.Preferences;

public class ServerConfig {

    public static final String DEFAULT_ASSIGNMENT_DUE_TIME = "default_assignment_due_time";
    private static final Preferences preferences = Preferences.userNodeForPackage(ServerConfig.class);
    private static final HashMap<String, String> stringProps = new HashMap<>();
    private static final HashMap<String, Integer> intProps = new HashMap<>();
    private static final HashMap<String, Boolean> boolProps = new HashMap<>();
    private static ServerPermissions permissions;
    private static StringConverter<ServerRole> roleStringConverter = new StringConverter<ServerRole>() {
        @Override
        public String toString(ServerRole object) {
            return object == null ? null : object.getName();
        }

        @Override
        public ServerRole fromString(String string) {
            for (ServerRole r : permissions.getRoles())
                if (r.getName().equals(string))
                    return r;
            return null;
        }
    };

    static {
        setDefaults();
    }

    private ServerConfig() {
    }

    private static void setDefaults() {
        stringProps.put(DEFAULT_ASSIGNMENT_DUE_TIME, "11:59 pm");
    }

    public static Integer getIntProp(String key) {
        return intProps.get(key);
    }

    public static void setIntProp(String key, int value) {
        intProps.put(key, value);
    }

    public static String getStringProp(String key) {
        return stringProps.get(key);
    }

    public static void setStringProp(String key, String value) {
        stringProps.put(key, value);
    }

    public static Boolean getBoolProp(String key) {
        return boolProps.get(key);
    }

    public static void setBoolProp(String key, boolean value) {
        boolProps.put(key, value);
    }

    public static ServerPermissions getPermissions() {
        return permissions;
    }

    public static void setPermissions(ServerPermissions permissions) {
        ServerConfig.permissions = permissions;
    }

    public static StringConverter<ServerRole> getRoleStringConverter() {
        return roleStringConverter;
    }
}
