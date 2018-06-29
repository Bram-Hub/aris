package edu.rpi.aris.net.server;

import edu.rpi.aris.LibAris;
import edu.rpi.aris.net.DBUtils;
import edu.rpi.aris.net.MessageCommunication;
import edu.rpi.aris.net.NetUtil;
import edu.rpi.aris.net.User;
import edu.rpi.aris.net.message.ErrorMsg;
import edu.rpi.aris.net.message.ErrorType;
import edu.rpi.aris.net.message.Message;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.text.ParseException;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;

public abstract class ClientHandler implements Runnable, MessageCommunication {

    private static final Logger logger = LogManager.getLogger(ClientHandler.class);

    private static final SecureRandom random = new SecureRandom();
    private static PassiveExpiringMap<String, String> banList = new PassiveExpiringMap<>(60 * 60 * 1000);
    private static PassiveExpiringMap<String, HashSet<Long>> loginAttempts = new PassiveExpiringMap<>(10 * 60 * 1000);
    private final SSLSocket socket;
    private DatabaseManager dbManager;
    private String clientVersion;
    private DataInputStream in;
    private DataOutputStream out;
    private User user;

    ClientHandler(SSLSocket socket, DatabaseManager dbManager) {
        this.socket = socket;
        this.dbManager = dbManager;
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
            logger.info("Connection successful");
            if (banList.containsKey(socket.getInetAddress().toString())) {
                logger.info("IP address is temp banned. Disconnecting");
                out.writeUTF(NetUtil.AUTH_BAN);
                out.flush();
                return;
            }
            logger.info("Waiting for client version");
            clientVersion = in.readUTF();
            logger.info("Version: " + clientVersion);
            if (!checkVersion()) {
                sendMessage(NetUtil.INVALID_VERSION);
                return;
            } else
                sendMessage(NetUtil.ARIS_NAME + " " + LibAris.VERSION);
            String versionVerify = in.readUTF();
            if (!versionVerify.equals(NetUtil.VERSION_OK))
                return;
            logger.info("Waiting for client auth");
            if (!verifyAuth()) {
                logger.info("Auth failed");
                return;
            }
            logger.info("Auth complete");
            messageWatch();
        } catch (Throwable e) {
            logger.error("Socket error", e);
        } finally {
            disconnect();
        }
    }

    private boolean checkVersion() {
        String[] split = clientVersion.split(" ");
        if (split.length != 2) {
            logger.error("Invalid client version string: " + clientVersion);
            return false;
        }
        if (!split[0].equals(NetUtil.ARIS_NAME)) {
            logger.error("Invalid client program name: " + split[0]);
            return false;
        }
        if (NetUtil.versionCompare(LibAris.VERSION, split[1]) < 0) {
            logger.warn("Client's version is newer than server");
            logger.warn("This may or may not cause problems");
        }
        return true;
    }

    private boolean verifyAuth() throws IOException {
        String authString = in.readUTF();
        String[] auth = authString.split("\\|");
        if (auth.length != 4 || !auth[0].equals(NetUtil.AUTH) || !(auth[1].equals(NetUtil.AUTH_PASS) || auth[1].equals(NetUtil.AUTH_ACCESS_TOKEN))) {
            sendMessage(NetUtil.AUTH_INVALID);
            return false;
        }
        String username = URLDecoder.decode(auth[2], "UTF-8").toLowerCase();
        logger.info("Authenticating user: " + username);
        String pass = URLDecoder.decode(auth[3], "UTF-8");
        try (Connection connection = dbManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT salt, password_hash, access_token, id, user_type FROM users WHERE username = ?;")) {
            statement.setString(1, username);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    String salt = set.getString(1);
                    String savedHash = set.getString(auth[1].equals(NetUtil.AUTH_PASS) ? 2 : 3);
                    int userId = set.getInt(4);
                    String userType = set.getString(5);
                    if (DBUtils.checkPass(pass, salt, savedHash)) {
                        String access_token = generateAccessToken();
                        MessageDigest digest = DBUtils.getDigest();
                        digest.update(Base64.getDecoder().decode(salt));
                        String hashed = Base64.getEncoder().encodeToString(digest.digest(access_token.getBytes()));
                        try (PreparedStatement updateAccessToken = connection.prepareStatement("UPDATE users SET access_token = ? WHERE username = ?;")) {
                            updateAccessToken.setString(1, hashed);
                            updateAccessToken.setString(2, username);
                            updateAccessToken.executeUpdate();
                        }
                        sendMessage(NetUtil.AUTH_OK + " " + URLEncoder.encode(access_token, "UTF-8"));
                        user = new User(userId, username, userType);
                        return true;
                    } else {
                        if (auth[1].equals(NetUtil.AUTH_PASS)) {
                            if (updateBanList()) {
                                logger.info("Client has been banned for 60 minutes");
                                sendMessage(NetUtil.AUTH_BAN);
                            } else
                                sendMessage(NetUtil.AUTH_FAIL);
                        } else
                            sendMessage(NetUtil.AUTH_FAIL);
                        return false;
                    }
                } else {
                    if (updateBanList()) {
                        logger.info("IP address has been banned for 60 minutes due to repeated failed login attempts");
                        sendMessage(NetUtil.AUTH_BAN);
                    } else
                        sendMessage(NetUtil.AUTH_FAIL);
                    return false;
                }
            }
        } catch (SQLException e) {
            logger.error("Error while verifying auth", e);
            sendMessage(NetUtil.AUTH_ERR);
            return false;
        }
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

    private String generateAccessToken() {
        byte[] tokenBytes = new byte[256];
        random.nextBytes(tokenBytes);
        return Base64.getEncoder().encodeToString(tokenBytes);
    }

    private void messageWatch() {
        try {
            //noinspection InfiniteLoopStatement
            try {
                Message msg = Message.get(this);
                if (msg != null) {
                    try (Connection connection = dbManager.getConnection()) {
                        try {
                            connection.setAutoCommit(false);
                            ErrorType error = msg.processMessage(connection, user);
                            if (error == null) {
                                connection.commit();
                                msg.send(this);
                            } else {
                                connection.rollback();
                                logger.error(msg.getMessageType().name() + " processing failed with error: " + error.name());
                                if (msg instanceof ErrorMsg)
                                    msg.send(this);
                                else
                                    new ErrorMsg(error).send(this);
                            }
                        } catch (IOException | SQLException e) {
                            connection.rollback();
                            throw e;
                        } catch (Exception e) {
                            connection.rollback();
                            new ErrorMsg(ErrorType.EXCEPTION, e.getClass().getCanonicalName() + ": " + e.getMessage()).send(this);
                            throw e;
                        }
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

    private void getProofs() throws SQLException, IOException {
        if (!user.userType.equals(NetUtil.USER_INSTRUCTOR)) {
            sendMessage(NetUtil.UNAUTHORIZED);
            return;
        }
        try (Connection connection = dbManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT id, name, created_by, created_on FROM proof ORDER BY created_on DESC;")) {
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt(1);
                    String name = URLEncoder.encode(rs.getString(2), "UTF-8");
                    String createdBy = URLEncoder.encode(rs.getString(3), "UTF-8");
                    String createdOn = URLEncoder.encode(NetUtil.DATE_FORMAT.format(rs.getTimestamp(4)), "UTF-8");
                    sendMessage(id + "|" + name + "|" + createdBy + "|" + createdOn);
                }
            }
        }
        sendMessage(NetUtil.DONE);
    }

    private void getSubmissions() throws IOException, SQLException {
        String[] assignmentData = in.readUTF().split("\\|");
        if ((assignmentData.length != 3 && user.userType.equals(NetUtil.USER_STUDENT)) || (assignmentData.length != 4 && user.userType.equals(NetUtil.USER_INSTRUCTOR)))
            return;
        int cid, aid, pid, uid = user.uid;
        try {
            cid = Integer.parseInt(assignmentData[0]);
            aid = Integer.parseInt(assignmentData[1]);
            pid = Integer.parseInt(assignmentData[2]);
            if (user.userType.equals(NetUtil.USER_INSTRUCTOR))
                uid = Integer.parseInt(assignmentData[3]);
        } catch (NumberFormatException e) {
            return;
        }
        try (Connection connection = dbManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT id, timestamp, status, data FROM submission WHERE class_id = ? AND assignment_id = ? AND proof_id = ? AND user_id = ?;")) {
            statement.setInt(1, cid);
            statement.setInt(2, aid);
            statement.setInt(3, pid);
            statement.setInt(4, uid);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt(1);
                    String timestamp = URLEncoder.encode(NetUtil.DATE_FORMAT.format(rs.getTimestamp(2)), "UTF-8");
                    String status = URLEncoder.encode(rs.getString(3), "UTF-8");
                    sendMessage(id + "|" + timestamp + "|" + status);
                }
            }
        }
        sendMessage(NetUtil.DONE);
    }

    private void createSubmission() throws IOException, SQLException {
        String[] submissionData = in.readUTF().split("\\|");
        if (submissionData.length != 3)
            sendMessage(NetUtil.INVALID);
        int cid, aid, pid;
        try {
            cid = Integer.parseInt(submissionData[0]);
            aid = Integer.parseInt(submissionData[1]);
            pid = Integer.parseInt(submissionData[2]);
        } catch (NumberFormatException e) {
            sendMessage(NetUtil.INVALID);
            return;
        }
        try (Connection connection = dbManager.getConnection();
             PreparedStatement subVerify = connection.prepareStatement("SELECT count(*) FROM assignment a, users u, user_class uc WHERE id a.class_id = ? AND a.id = ? AND a.proof_id = ? AND a.class_id = uc.class_id AND u.id = uc.user_id AND u.id = ?;");
             PreparedStatement insert = connection.prepareStatement("INSERT INTO submission VALUES(NULL, ?, ?, ?, ?, ?, now(), 'grading');")) {
            subVerify.setInt(1, cid);
            subVerify.setInt(2, aid);
            subVerify.setInt(3, pid);
            subVerify.setInt(4, user.uid);
            try (ResultSet rs = subVerify.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    sendMessage(NetUtil.OK);
                    String sizeStr = in.readUTF();
                    long size;
                    try {
                        size = Long.parseLong(sizeStr);
                    } catch (NumberFormatException e) {
                        throw new IOException("Invalid size");
                    }
                    if (size > NetUtil.MAX_FILE_SIZE) {
                        sendMessage(NetUtil.TOO_LARGE);
                        return;
                    }
                    byte[] data = new byte[(int) size];
                    if (size != in.read(data)) {
                        sendMessage(NetUtil.ERROR);
                        return;
                    }
                    insert.setInt(1, cid);
                    insert.setInt(2, aid);
                    insert.setInt(3, user.uid);
                    insert.setInt(4, pid);
                    ByteArrayInputStream is = new ByteArrayInputStream(data);
                    insert.setBinaryStream(5, is);
                    insert.executeUpdate();
                    sendMessage(NetUtil.OK);
                    // TODO: schedule for grading
                } else {
                    sendMessage(NetUtil.INVALID);
                }
            }
        }
    }

    private void updateAssignment() throws IOException, SQLException {
        if (!user.userType.equals(NetUtil.USER_INSTRUCTOR)) {
            sendMessage(NetUtil.UNAUTHORIZED);
            return;
        }
        boolean done = false;
        while (!done) {
            String[] str = in.readUTF().split("\\|");
            if (str.length == 1 && str[0].equals(NetUtil.DONE)) {
                done = true;
                continue;
            }
            if (str.length != 4) {
                sendMessage(NetUtil.INVALID);
                return;
            }
            int aid, cid;
            try {
                cid = Integer.parseInt(str[1]);
                aid = Integer.parseInt(str[2]);
            } catch (NumberFormatException e) {
                sendMessage(NetUtil.ERROR);
                return;
            }
            try (Connection connection = dbManager.getConnection()) {
                switch (str[0]) {
                    case NetUtil.RENAME:
                        String name = URLDecoder.decode(str[3], "UTF-8");
                        try (PreparedStatement statement = connection.prepareStatement("UPDATE assignment SET name = ? WHERE id = ? AND class_id = ?;")) {
                            statement.setString(1, name);
                            statement.setInt(2, aid);
                            statement.setInt(3, cid);
                            statement.executeUpdate();
                        }
                        break;
                    case NetUtil.ADD_PROOF:
                        int pid;
                        try {
                            pid = Integer.parseInt(str[3]);
                        } catch (NumberFormatException e) {
                            sendMessage(NetUtil.ERROR);
                            return;
                        }
                        try (PreparedStatement select = connection.prepareStatement("SELECT name, due_date, assigned_by FROM assignment WHERE id = ? AND class_id = ?;");
                             PreparedStatement addProof = connection.prepareStatement("INSERT INTO assignment VALUES(?, ?, ?, ?, ?, ?);")) {
                            select.setInt(1, aid);
                            select.setInt(2, cid);
                            try (ResultSet rs = select.executeQuery()) {
                                if (!rs.next()) {
                                    sendMessage(NetUtil.ERROR);
                                    return;
                                }
                                String n = rs.getString(1);
                                String due_date = rs.getString(2);
                                String assigned = rs.getString(3);
                                addProof.setInt(1, aid);
                                addProof.setInt(2, cid);
                                addProof.setInt(3, pid);
                                addProof.setString(4, n);
                                addProof.setString(5, due_date);
                                addProof.setString(6, assigned);
                                addProof.executeUpdate();
                            }
                        }
                        break;
                    case NetUtil.REMOVE_PROOF:
                        try {
                            pid = Integer.parseInt(str[3]);
                        } catch (NumberFormatException e) {
                            sendMessage(NetUtil.ERROR);
                            return;
                        }
                        try (PreparedStatement removeAssignment = connection.prepareStatement("DELETE FROM assignment WHERE id = ? AND class_id = ? AND proof_id = ?;")) {
                            removeAssignment.setInt(1, aid);
                            removeAssignment.setInt(2, cid);
                            removeAssignment.setInt(3, pid);
                            removeAssignment.executeUpdate();
                        }
                        break;
                    case NetUtil.CHANGE_DUE:
                        Date time;
                        try {
                            time = NetUtil.DATE_FORMAT.parse(str[3]);
                        } catch (ParseException e) {
                            sendMessage(NetUtil.ERROR);
                            return;
                        }
                        try (PreparedStatement statement = connection.prepareStatement("UPDATE assignment SET due_date = ? WHERE id = ? AND class_id = ?;")) {
                            statement.setTimestamp(1, new Timestamp(time.getTime()));
                            statement.setInt(2, aid);
                            statement.setInt(3, cid);
                            statement.executeUpdate();
                        }
                        break;
                    case NetUtil.DONE:
                        done = true;
                        break;
                    default:
                        sendMessage(NetUtil.INVALID);
                        return;
                }
            }
        }
        sendMessage("OK");
    }

    private void updateProof() throws IOException, SQLException {
        String[] proofData = in.readUTF().split("\\|");
        if (!user.userType.equals(NetUtil.USER_INSTRUCTOR)) {
            sendMessage(NetUtil.UNAUTHORIZED);
            return;
        }
        if (proofData.length != 3) {
            sendMessage(NetUtil.INVALID);
            return;
        }
        int id;
        try {
            id = Integer.parseInt(proofData[1]);
        } catch (NumberFormatException e) {
            sendMessage(NetUtil.ERROR);
            return;
        }
        switch (proofData[0]) {
            case NetUtil.RENAME:
                String name = proofData[2];
                try (Connection connection = dbManager.getConnection();
                     PreparedStatement updateName = connection.prepareStatement("UPDATE proof SET name = ? WHERE id = ?")) {
                    updateName.setString(1, name);
                    updateName.setInt(2, id);
                    updateName.executeUpdate();
                }
                break;
            case NetUtil.UPLOAD:
                long size;
                try {
                    size = Long.parseLong(proofData[2]);
                } catch (NumberFormatException e) {
                    sendMessage(NetUtil.ERROR);
                    return;
                }
                try (Connection connection = dbManager.getConnection();
                     PreparedStatement updateData = connection.prepareStatement("UPDATE proof SET data = ? WHERE id = ?")) {
                    if (size > 0) {
                        if (size > NetUtil.MAX_FILE_SIZE) {
                            sendMessage(NetUtil.TOO_LARGE);
                            return;
                        }
                        byte[] data = new byte[(int) size];
                        if (size != in.read(data)) {
                            sendMessage(NetUtil.ERROR);
                            return;
                        }
                        ByteArrayInputStream bis = new ByteArrayInputStream(data);
                        updateData.setBinaryStream(1, bis);
                        updateData.setInt(2, id);
                        updateData.executeUpdate();
                    }
                }
                break;
            default:
                sendMessage(NetUtil.INVALID);
                return;
        }
        sendMessage(NetUtil.OK);
    }

    private void createUser() throws IOException, SQLException {
        String[] userData = in.readUTF().split("\\|");
        if (!user.userType.equals(NetUtil.USER_INSTRUCTOR)) {
            sendMessage(NetUtil.UNAUTHORIZED);
            return;
        }
        if (userData.length != 3) {
            sendMessage(NetUtil.INVALID);
            return;
        }
        String username = URLDecoder.decode(userData[0], "UTF-8").toLowerCase();
        String userType = URLDecoder.decode(userData[1], "UTF-8").toLowerCase();
        String password = URLDecoder.decode(userData[2], "UTF-8");
        try (Connection connection = dbManager.getConnection();
             PreparedStatement count = connection.prepareStatement("SELECT count(*) FROM users WHERE username = ?;")) {
            count.setString(1, username);
            try (ResultSet rs = count.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    sendMessage(NetUtil.USER_EXISTS);
                    return;
                }
            }
        }
        Pair<String, String> res = dbManager.createUser(username, password, user.userType);
        if (res.getValue().equals(NetUtil.OK)) {
            if (!res.getKey().equals(password))
                sendMessage(NetUtil.OK + " " + URLEncoder.encode(res.getKey(), "UTF-8"));
            else
                sendMessage(NetUtil.OK);
        } else
            sendMessage(res.getValue());
    }

    private void deleteUser() throws IOException, SQLException {
        String strId = in.readUTF();
        if (!user.userType.equals(NetUtil.USER_INSTRUCTOR)) {
            sendMessage(NetUtil.UNAUTHORIZED);
            return;
        }
        int id;
        try {
            id = Integer.parseInt(strId);
        } catch (NumberFormatException e) {
            sendMessage(NetUtil.ERROR);
            return;
        }
        try (Connection connection = dbManager.getConnection();
             PreparedStatement deleteUser = connection.prepareStatement("DELETE FROM users WHERE id = ?;");) {
            deleteUser.setInt(1, id);
            deleteUser.executeUpdate();
        }
        sendMessage(NetUtil.OK);
    }

    private void deleteClass() throws IOException, SQLException {
        String idStr = in.readUTF();
        if (!user.userType.equals(NetUtil.USER_INSTRUCTOR)) {
            sendMessage(NetUtil.UNAUTHORIZED);
            return;
        }
        int id;
        try {
            id = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            sendMessage(NetUtil.ERROR);
            return;
        }
        try (Connection connection = dbManager.getConnection();
             PreparedStatement deleteClass = connection.prepareStatement("DELETE FROM class WHERE id = ?;")) {
            deleteClass.setInt(1, id);
            deleteClass.executeUpdate();
        }
        sendMessage(NetUtil.OK);
    }

    private void updateClass() throws IOException, SQLException {
        String[] classData = in.readUTF().split("\\|");
        if (!user.userType.equals(NetUtil.USER_INSTRUCTOR)) {
            sendMessage(NetUtil.UNAUTHORIZED);
            return;
        }
        if (classData.length != 2) {
            sendMessage(NetUtil.ERROR);
            return;
        }
        int id;
        try {
            id = Integer.parseInt(classData[0]);
        } catch (NumberFormatException e) {
            sendMessage(NetUtil.ERROR);
            return;
        }
        try (Connection connection = dbManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE class SET name = ? WHERE id = ?;")) {
            statement.setString(1, URLDecoder.decode(classData[1], "UTF-8"));
            statement.setInt(2, id);
            statement.executeUpdate();
        }
        sendMessage(NetUtil.OK);
    }

    @Override
    public String readMessage() throws IOException {
        return in.readUTF();
    }

    @Override
    public void sendMessage(String msg) throws IOException {
        out.writeUTF(msg);
        out.flush();
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