package edu.rpi.aris.assign.server;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import edu.rpi.aris.assign.*;
import edu.rpi.aris.assign.message.*;
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
                user = authMsg.checkAuth(connection, permissions);
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

//    private boolean verifyAuth(AuthMessage msg) throws IOException {
//        String authString = in.readUTF();
//        String[] auth = authString.split("\\|");
//        if (auth.length != 4 || !auth[0].equals(NetUtil.AUTH) || !(auth[1].equals(NetUtil.AUTH_PASS) || auth[1].equals(NetUtil.AUTH_ACCESS_TOKEN))) {
//            sendMessage(NetUtil.AUTH_INVALID);
//            return false;
//        }
//
//        Thread.currentThread().setName(Thread.currentThread().getName() + "/" + username);
//        logger.info("Authenticating user: " + username);
//        String pass = URLDecoder.decode(auth[3], "UTF-8");
//        try (Connection connection = dbManager.getConnection();
//             PreparedStatement statement = connection.prepareStatement("SELECT salt, password_hash, access_token, id, default_role, force_reset FROM users WHERE username = ?;")) {
//            statement.setString(1, username);
//            try (ResultSet set = statement.executeQuery()) {
//                if (set.next()) {
//                    String salt = set.getString(1);
//                    String savedHash = set.getString(auth[1].equals(NetUtil.AUTH_PASS) ? 2 : 3);
//                    int userId = set.getInt(4);
//                    ServerRole userRole;
//                    try {
//                        userRole = permissions.getRole(set.getInt(5));
//                    } catch (IllegalArgumentException e) {
//                        logger.error("Failed to parse UserType", e);
//                        userRole = permissions.getLowestRole();
//                        try (PreparedStatement updateUserType = connection.prepareStatement("UPDATE users SET default_role = ? WHERE username = ?;")) {
//                            updateUserType.setInt(1, userRole.getId());
//                            updateUserType.setString(2, username);
//                            updateUserType.executeUpdate();
//                        }
//                    }
//                    boolean forceReset = set.getBoolean(6);
//                    if (DBUtils.checkPass(pass, salt, savedHash)) {
//                        String access_token = generateAccessToken();
//                        MessageDigest digest = DBUtils.getDigest();
//                        digest.update(Base64.getDecoder().decode(salt));
//                        String hashed = Base64.getEncoder().encodeToString(digest.digest(access_token.getBytes()));
//                        try (PreparedStatement updateAccessToken = connection.prepareStatement("UPDATE users SET access_token = ? WHERE username = ?;")) {
//                            updateAccessToken.setString(1, hashed);
//                            updateAccessToken.setString(2, username);
//                            updateAccessToken.executeUpdate();
//                        }
//                        logger.info("Password expired reset required");
//                        sendMessage((forceReset ? NetUtil.AUTH_RESET : NetUtil.AUTH_OK) + " " + URLEncoder.encode(access_token, "UTF-8"));
//                        user = new User(userId, username, userRole, forceReset);
//                        return false;
//                    } else {
//                        if (auth[1].equals(NetUtil.AUTH_PASS)) {
//                            if (updateBanList()) {
//                                logger.info("Client has been banned for 60 minutes");
//                                sendMessage(NetUtil.AUTH_BAN);
//                            } else
//                                sendMessage(NetUtil.AUTH_FAIL);
//                        } else
//                            sendMessage(NetUtil.AUTH_FAIL);
//                        return false;
//                    }
//                } else {
//                    if (updateBanList()) {
//                        logger.info("IP address has been banned for 60 minutes due to repeated failed login attempts");
//                        sendMessage(NetUtil.AUTH_BAN);
//                    } else
//                        sendMessage(NetUtil.AUTH_FAIL);
//                    return false;
//                }
//            }
//        } catch (SQLException e) {
//            logger.error("Error while verifying auth", e);
//            sendMessage(NetUtil.AUTH_ERR);
//            return false;
//        }
//    }

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
            //noinspection InfiniteLoopStatement
            try {
                Message msg = Message.get(this);
                if (msg == null)
                    return;
                try (Connection connection = dbManager.getConnection()) {
                    try {
                        connection.setAutoCommit(false);
                        ErrorType error;
                        Perm perm = msg.getPermission();
                        if (user.requireReset()) {
                            if (msg instanceof UserEditMsg && ((UserEditMsg) msg).isChangePass() && user.username.equals(((UserEditMsg) msg).getUsername()))
                                error = msg.processMessage(connection, user, permissions);
                            else
                                error = ErrorType.RESET_PASS;
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
                        } else {
                            connection.rollback();
                            logger.error(msg.getMessageType().name() + " processing failed with error: " + error.name());
                            if (msg instanceof ErrorMsg)
                                msg.send(this);
                            else {
                                if (error == ErrorType.UNAUTHORIZED)
                                    new ErrorMsg(error, perm == null ? null : perm.name()).send(this);
                                else
                                    new ErrorMsg(error).send(this);
                            }
                        }
                    } catch (IOException | SQLException e) {
                        connection.rollback();
                        throw e;
                    } catch (Throwable e) {
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
            // ignored so we don't print an exception whenever client disconnects
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

//    @Override
//    public JsonElement readMessage(Gson gson) throws IOException {
//        return gson.fromJson(reader, Message.class);
//    }
//
//    @Override
//    public void sendMessage(JsonElement msg) throws IOException {
//        out.writeUTF(msg);
//        out.flush();
//    }

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
        throw new RuntimeException("Not implemented");
    }

    public void disconnect() {
        try {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    logger.error("Failed to close input stream", e);
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    logger.error("Failed to close output stream", e);
                }
            }
            try {
                socket.close();
            } catch (IOException e) {
                logger.error("Failed to close socket");
            }
            logger.info("Disconnected");
        } finally {
            onDisconnect(this);
        }
    }

    public abstract void onDisconnect(ClientHandler clientHandler);

}