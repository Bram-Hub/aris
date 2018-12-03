package edu.rpi.aris.assign.server;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import edu.rpi.aris.assign.*;
import edu.rpi.aris.assign.message.*;
import edu.rpi.aris.assign.server.auth.LoginUtil;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.HashSet;

public abstract class ClientHandler implements Runnable, MessageCommunication {

    private static final Logger logger = LogManager.getLogger(ClientHandler.class);

    private static PassiveExpiringMap<String, String> banList = new PassiveExpiringMap<>(60 * 60 * 1000);
    private static PassiveExpiringMap<String, HashSet<Long>> loginAttempts = new PassiveExpiringMap<>(10 * 60 * 1000);
    private final SSLSocket socket;
    private DatabaseManager dbManager;
    private ServerPermissions permissions;
    private DataInputStream in;
    private DataOutputStream out;
    private JsonReader reader;
    private JsonWriter writer;
    private User user;

    ClientHandler(SSLSocket socket, DatabaseManager dbManager) {
        this.socket = socket;
        this.dbManager = dbManager;
        permissions = AssignServerMain.getServer().getPermissions();
    }

    @Override
    public void run() {
        try {
            InetAddress address = socket.getInetAddress();
            //noinspection ResultOfMethodCallIgnored
            address.getHostName();
            Thread.currentThread().setName("ClientHandler-" + socket.getInetAddress().toString());
            logger.info("Incoming connection from " + socket.getInetAddress().toString());
            socket.setUseClientMode(false);
            socket.setNeedClientAuth(false);
            socket.setSoTimeout(NetUtil.SOCKET_TIMEOUT);
            socket.addHandshakeCompletedListener(handshakeCompletedEvent -> {
                logger.info("Handshake complete");
                synchronized (socket) {
                    socket.notify();
                }
            });
            logger.info("Starting handshake");
            synchronized (socket) {
                try {
                    socket.startHandshake();
                    socket.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            reader = new JsonReader(new InputStreamReader(in));
            writer = new JsonWriter(new OutputStreamWriter(out));
            writer.beginArray();
            writer.flush();
            reader.beginArray();
            logger.info("Connection successful");
            if (banList.containsKey(socket.getInetAddress().toString())) {
                logger.info("IP address is temp banned. Disconnecting");
                new AuthMessage(AuthMessage.Auth.BAN).send(this);
                return;
            }
            logger.info("Waiting for client auth message");
            Message msg = Message.get(this);
            if (!(msg instanceof AuthMessage)) {
                new AuthMessage(AuthMessage.Auth.INVALID).send(this);
                return;
            }
            AuthMessage authMsg = (AuthMessage) msg;
            logger.info("Version: " + authMsg.getVersion());
            if (!checkVersion(authMsg.getVersion())) {
                new AuthMessage(AuthMessage.Auth.UNSUPPORTED_VERSION).send(this);
                return;
            }
            user = null;
            logger.info("Checking client auth");
            try (Connection connection = dbManager.getConnection()) {
                user = LoginUtil.verifyAuth(authMsg, connection, permissions);
                if (authMsg.getStatus() == AuthMessage.Auth.FAIL) {
                    if (!authMsg.isAccessToken() && updateBanList()) {
                        logger.info("Auth banned");
                        authMsg.setStatus(AuthMessage.Auth.BAN);
                    } else
                        logger.info("Auth failed");
                    authMsg.send(this);
                    return;
                }
            } catch (SQLException e) {
                logger.error("An error occured while verifying auth");
                new AuthMessage(AuthMessage.Auth.ERROR).send(this);
                return;
            }
            authMsg.send(this);
            if (user != null) {
                logger.info("Auth complete");
                messageWatch();
            }
        } catch (Throwable e) {
            logger.error("Socket error", e);
        } finally {
            disconnect();
        }
    }

    private boolean checkVersion(String clientVersion) {
        if (NetUtil.versionCompare(LibAssign.VERSION, clientVersion) < 0) {
            logger.warn("Client's version is newer than server");
            logger.warn("This may or may not cause problems");
        }
        return true;
    }

    private boolean updateBanList() {
        String ip = socket.getInetAddress().toString();
        HashSet<Long> attempts = loginAttempts.computeIfAbsent(ip, i -> new HashSet<>());
        Calendar now = Calendar.getInstance();
        now.add(Calendar.MINUTE, -10);
        attempts.removeIf(l -> l < now.getTimeInMillis());
        attempts.add(System.currentTimeMillis());
        if (attempts.size() >= 10) {
            banList.put(ip, ip);
            return true;
        }
        return false;
    }

    private void messageWatch() {
        try {
            try {
                Message msg = Message.get(this);
                if (msg == null) {
                    new ErrorMsg(ErrorType.PARSE_ERR, "Server failed to parse message").send(this);
                    return;
                }
                try (Connection connection = dbManager.getConnection()) {
                    try {
                        connection.setAutoCommit(false);
                        ErrorType error;
                        Perm perm = msg.getPermission();
                        if (user.requireReset()) {
                            logger.warn("Password reset required");
                            if (msg instanceof UserChangePasswordMsg && user.username.equals(((UserChangePasswordMsg) msg).getUsername()))
                                error = msg.processMessage(connection, user, permissions);
                            else {
                                logger.warn("Client did not send password reset message");
                                error = ErrorType.RESET_PASS;
                            }
                        } else {
                            if (msg.hasCustomPermissionCheck()) {
                                error = msg.processMessage(connection, user, permissions);
                            } else {
                                if (msg instanceof ClassMessage ? permissions.hasClassPermission(user, ((ClassMessage) msg).getClassId(), perm, connection) : (permissions.hasPermission(user, perm)))
                                    error = msg.processMessage(connection, user, permissions);
                                else
                                    error = ErrorType.UNAUTHORIZED;
                            }
                        }
                        if (error == null) {
                            connection.commit();
                            msg.send(this);
                            logger.info("Finished processing message: " + msg.getMessageType());
                        } else {
                            connection.rollback();
                            logger.error(msg.getMessageType().name() + " processing failed with error: " + error.name());
                            logger.error("SQL changes have been rolled back");
                            if (msg instanceof ErrorMsg)
                                msg.send(this);
                            else {
                                if (error == ErrorType.UNAUTHORIZED) {
                                    logger.warn("User does not have permission: " + perm);
                                    new ErrorMsg(error, perm == null ? null : perm.name()).send(this);
                                } else
                                    new ErrorMsg(error).send(this);
                            }
                        }
                    } catch (IOException | SQLException e) {
                        logger.error("Exception occurred! Rolling back changes");
                        connection.rollback();
                        throw e;
                    } catch (Throwable e) {
                        logger.error("Exception occurred! Rolling back changes");
                        connection.rollback();
                        new ErrorMsg(ErrorType.EXCEPTION, e.getClass().getCanonicalName() + ": " + e.getMessage()).send(this);
                        throw e;
                    }
                }
            } catch (SQLException e) {
                logger.error("SQL Error", e);
                new ErrorMsg(ErrorType.SQL_ERR, e.getMessage()).send(this);
            }
        } catch (IOException ignored) {
            // ignored so we don't log an exception whenever client disconnects
        } catch (Throwable e) {
            logger.error("Unexpected error occurred", e);
        }
    }

    @Override
    public JsonReader getReader() {
        return reader;
    }

    @Override
    public JsonWriter getWriter() {
        return writer;
    }

    @Override
    public DataInputStream getInputStream() {
        return in;
    }

    @Override
    public DataOutputStream getOutputStream() {
        return out;
    }

    @Override
    public void handleErrorMsg(ErrorMsg msg) {
        System.out.println(msg);
        //TODO: implement
    }

    public void disconnect() {
        try {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // don't throw an exception if things are already closed
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                    // don't throw an exception if things are already closed
                }
            }
            try {
                socket.close();
            } catch (IOException ignored) {
                // don't throw an exception if things are already closed
            }
            logger.info("Disconnected");
        } finally {
            onDisconnect(this);
        }
    }

    public abstract void onDisconnect(ClientHandler clientHandler);

}