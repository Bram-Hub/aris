package edu.rpi.aris.assign;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public abstract class LoginAuth {

    private static final HashMap<AuthType, LoginAuth> authMap = new HashMap<>();

    protected LoginAuth() {
    }

    public static LoginAuth getAuthForType(AuthType type) {
        if (type == null)
            return null;
        return authMap.get(type);
    }

    public static void registerLoginAuth(LoginAuth auth) {
        if (auth == null)
            return;
        for (AuthType t : auth.handlesTypes())
            authMap.put(t, auth);
    }

    /**
     * @param user      the username
     * @param pass      the password
     * @param salt      the stored password salt from the database
     * @param savedHash the stored password hash from the database
     * @return null if the password is correct or a String with the error message
     */
    public abstract String checkAuth(String user, String pass, String salt, String savedHash);

    public abstract boolean isSupported();

    public abstract boolean isLocalAuth();

    public abstract boolean isValidUsername(String username);

    @NotNull
    public abstract AuthType[] handlesTypes();

}
