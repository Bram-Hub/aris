package edu.rpi.aris.assign.server.auth;

import edu.rpi.aris.assign.DBUtils;

public class LocalLoginAuth extends LoginAuth {

    private static final LocalLoginAuth instance = new LocalLoginAuth();

    private LocalLoginAuth() {
    }

    public static LoginAuth getInstance() {
        return instance;
    }

    @Override
    protected String checkAuth(String user, String pass, String salt, String savedHash) {
        return DBUtils.checkPass(pass, salt, savedHash) ? null : "Invalid password or access token";
    }

    @Override
    public boolean isSupported() {
        return true;
    }
}
