package edu.rpi.aris.assign.server.auth;

import edu.rpi.aris.assign.AuthType;
import edu.rpi.aris.assign.LoginAuth;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class PAMLoginAuth extends LoginAuth {

    private static final String LIB_NAME = "assign_pam";
    private static final Logger log = LogManager.getLogger();
    private static final PAMLoginAuth instance = new PAMLoginAuth();

    static {
        edu.rpi.aris.util.SharedObjectLoader.loadLib(LIB_NAME);
    }

    private PAMLoginAuth() {
    }

    static void register() {
        LoginAuth.registerLoginAuth(instance);
    }

    static PAMLoginAuth getInstance() {
        return instance;
    }

    private static native PAMResponse pam_authenticate(String user, String pass);

    @Override
    public String checkAuth(String user, String pass, String salt, String savedHash) {
        PAMResponse response = pam_authenticate(user, pass);
        if (response == null)
            return "PAM library returned no response";
        if (response.success)
            return null;
        return response.error == null ? "PAM encountered an unknown error" : response.error;
    }

    @Override
    public boolean isSupported() {
        return edu.rpi.aris.util.SharedObjectLoader.isLoaded(LIB_NAME);
    }

    @Override
    public boolean isLocalAuth() {
        return false;
    }

    @Override
    public boolean isValidUsername(String username) {
        if (username == null || username.trim().length() == 0)
            return false;
        try {
            Process process = Runtime.getRuntime().exec("id -u \"" + username + "\"");
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            log.error("Failed to check if user \"" + username + "\" exists", e);
            return false;
        }
    }

    @NotNull
    @Override
    public AuthType[] handlesTypes() {
        return new AuthType[]{AuthType.PAM};
    }

}
