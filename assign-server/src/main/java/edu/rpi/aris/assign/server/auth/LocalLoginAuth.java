package edu.rpi.aris.assign.server.auth;

import edu.rpi.aris.assign.AuthType;
import edu.rpi.aris.assign.DBUtils;
import edu.rpi.aris.assign.LoginAuth;
import org.jetbrains.annotations.NotNull;

public class LocalLoginAuth extends LoginAuth {

    private static final LocalLoginAuth instance = new LocalLoginAuth();

    private LocalLoginAuth() {
    }

    public static LoginAuth getInstance() {
        return instance;
    }

    static void register() {
        LoginAuth.registerLoginAuth(instance);
    }

    @Override
    public String checkAuth(String user, String pass, String salt, String savedHash) {
        return DBUtils.checkPass(pass, salt, savedHash) ? null : "Invalid password or access token";
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public boolean isLocalAuth() {
        return true;
    }

    @Override
    public boolean isValidUsername(String username) {
        return username != null && username.trim().length() > 0;
    }

    @NotNull
    @Override
    public AuthType[] handlesTypes() {
        return new AuthType[]{AuthType.LOCAL, AuthType.ACCESS_TOKEN};
    }
}
