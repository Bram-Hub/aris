package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.LibAssign;
import edu.rpi.aris.assign.ServerPermissions;
import edu.rpi.aris.assign.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;

public class AuthMessage extends Message {

    private static final Logger log = LogManager.getLogger();

    private final String username;
    private boolean isAccessToken;
    private String version;
    private String passAccessToken;
    private Auth status;

    public AuthMessage(String username, String passAccessToken, boolean isAccessToken) {
        super(null, true);
        this.username = username;
        this.passAccessToken = passAccessToken;
        this.isAccessToken = isAccessToken;
        this.version = LibAssign.VERSION;
    }

    public AuthMessage(Auth status) {
        this(null, null, false);
        this.status = status;
    }

    @Nullable
    @Override
    public ErrorType processMessage(@NotNull Connection connection, @NotNull User user, @NotNull ServerPermissions permissions) throws Exception {
        return ErrorType.UNAUTHORIZED;
    }

    @NotNull
    @Override
    public MessageType getMessageType() {
        return MessageType.AUTH;
    }

    @Override
    public boolean checkValid() {
        return true;
    }

    public String getVersion() {
        return version;
    }

    public String getUsername() {
        return username;
    }

    public boolean isAccessToken() {
        return isAccessToken;
    }

    public String getPassAccessToken() {
        return passAccessToken;
    }

    public void setPassAccessToken(String passAccessToken) {
        this.passAccessToken = passAccessToken;
    }

    public Auth getStatus() {
        return status;
    }

    public void setStatus(Auth status) {
        this.status = status;
    }

    public void setIsAccessToken(boolean isAccessToken) {
        this.isAccessToken = isAccessToken;
    }

    public enum Auth {
        BAN,
        ERROR,
        FAIL,
        INVALID,
        OK,
        RESET,
        UNSUPPORTED_VERSION
    }
}
