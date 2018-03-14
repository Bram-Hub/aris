package edu.rpi.aris.net.server;

import edu.rpi.aris.Main;
import edu.rpi.aris.net.NetUtil;
import org.apache.commons.collections.map.PassiveExpiringMap;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Calendar;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

public class ClientHandler implements Runnable {

    private static final int SOCKET_TIMEOUT = 15000;

    private static final Logger logger = LogManager.getLogger(ClientHandler.class);

    private final SSLSocket socket;
    private DatabaseManager dbManager;
    private String clientName, clientVersion;
    private DataInputStream in;
    private DataOutputStream out;
    private PassiveExpiringMap<String, String> banList = new PassiveExpiringMap<>(60, TimeUnit.MINUTES);
    private PassiveExpiringMap<String, HashSet<Long>> loginAttempts = new PassiveExpiringMap<>(10, TimeUnit.MINUTES);
    private String username;
    private int userId;

    public ClientHandler(SSLSocket socket, DatabaseManager dbManager) {
        this.socket = socket;
        this.dbManager = dbManager;
    }

    @Override
    public void run() {
        try {
            clientName = socket.getInetAddress().getHostName();
            logger.info("[" + clientName + "] Incoming connection from " + socket.getInetAddress().toString());
            socket.setUseClientMode(false);
            socket.setNeedClientAuth(false);
            socket.setSoTimeout(SOCKET_TIMEOUT);
            socket.addHandshakeCompletedListener(handshakeCompletedEvent -> {
                logger.info("[" + clientName + "] Handshake complete");
                synchronized (socket) {
                    socket.notify();
                }
            });
            logger.info("[" + clientName + "] Starting handshake");
            socket.startHandshake();
            synchronized (socket) {
                try {
                    socket.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            logger.info("[" + clientName + "] Connection successful");
            if (banList.containsKey(socket.getInetAddress().toString())) {
                out.writeUTF(NetUtil.AUTH_BAN);
                out.flush();
                disconnect();
            }
            logger.info("[" + clientName + "] Waiting for client version");
            clientVersion = in.readUTF();
            logger.info("[" + clientName + "] Version: " + clientVersion);
            if (!checkVersion()) {
                sendMessage("INVALID VERSION");
                return;
            } else
                sendMessage(NetUtil.ARIS_NAME + " " + Main.VERSION);
            String versionVerify = in.readUTF();
            if (!versionVerify.equals("VERSION OK"))
                return;
            logger.info("[" + clientName + "] Waiting for client auth");
            if (!verifyAuth())
                return;
            logger.info("[" + clientName + "] Auth complete");
            messageWatch();
        } catch (Throwable e) {
            logger.error("[" + clientName + "] Socket error", e);
        } finally {
            disconnect();
        }
    }

    private boolean checkVersion() {
        String[] split = clientVersion.split(" ");
        if (split.length != 2) {
            logger.error("[" + clientName + "] Invalid client version string: " + clientVersion);
            return false;
        }
        if (!split[0].equals(NetUtil.ARIS_NAME)) {
            logger.error("[" + clientName + "] Invalid client program name: " + split[0]);
            return false;
        }
        if (NetUtil.versionCompare(Main.VERSION, split[1]) < 0) {
            logger.warn("[" + clientName + "] Client's version is newer than server");
            logger.warn("[" + clientName + "] This may or may not cause problems");
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
        username = URLDecoder.decode(auth[2], "UTF-8");
        String pass = URLDecoder.decode(auth[3], "UTF-8");
        try {
            PreparedStatement statement = dbManager.getStatement("SELECT salt, password_hash, access_token, id FROM username WHERE username = ?;");
            statement.setString(1, username);
            statement.execute();
            ResultSet set = statement.getResultSet();
            if (set.next()) {
                String salt = set.getString(1);
                String savedHash = set.getString(auth[1].equals(NetUtil.AUTH_PASS) ? 2 : 3);
                userId = set.getInt(4);
                MessageDigest digest = MessageDigest.getInstance("SHA512", "BC");
                digest.update(Base64.getDecoder().decode(salt));
                String hash = Base64.getEncoder().encodeToString(digest.digest(pass.getBytes()));
                if (hash.equals(savedHash)) {
                    String access_token = generateAccessToken();
                    digest.update(Base64.getDecoder().decode(salt));
                    String hashed = Base64.getEncoder().encodeToString(digest.digest(Base64.getDecoder().decode(access_token)));
                    PreparedStatement updateAccessToken = dbManager.getStatement("UPDATE username SET access_token = ? WHERE username = ?;");
                    updateAccessToken.setString(1, hashed);
                    updateAccessToken.setString(2, username);
                    updateAccessToken.execute();
                    sendMessage(NetUtil.AUTH_OK + " " + access_token);
                    return true;
                } else {
                    if (auth[1].equals(NetUtil.AUTH_PASS)) {
                        if (updateBanList())
                            sendMessage(NetUtil.AUTH_BAN);
                        else
                            sendMessage(NetUtil.AUTH_FAIL);
                    } else
                        sendMessage(NetUtil.AUTH_FAIL);
                    return false;
                }
            } else {
                if (updateBanList())
                    sendMessage(NetUtil.AUTH_BAN);
                else
                    sendMessage(NetUtil.AUTH_FAIL);
                return false;
            }
        } catch (NoSuchAlgorithmException | NoSuchProviderException | SQLException e) {
            logger.error("[" + clientName + "] Error while verifying auth", e);
            sendMessage(NetUtil.AUTH_ERR);
            return false;
        }
    }

    private boolean updateBanList() {
        String ip = socket.getInetAddress().toString();
        HashSet<Long> attempts = loginAttempts.get(ip);
        if (attempts == null)
            attempts = new HashSet<>();
        else {
            Calendar now = Calendar.getInstance();
            now.add(Calendar.MINUTE, -10);
            attempts.removeIf(l -> l < now.getTimeInMillis());
        }
        attempts.add(System.currentTimeMillis());
        loginAttempts.put(ip, attempts);
        if (attempts.size() >= 10) {
            banList.put(ip, ip);
            return true;
        }
        return false;
    }

    private String generateAccessToken() {
        byte[] tokenBytes = new byte[256];
        SecureRandom random = new SecureRandom();
        random.nextBytes(tokenBytes);
        return Base64.getEncoder().encodeToString(tokenBytes);
    }

    private void messageWatch() {
        //noinspection InfiniteLoopStatement
        while (true) {
            try {
                String msg = in.readUTF();
                logger.info("[" + clientName + "] Request: " + msg);
                try {
                    switch (msg) {
                        case NetUtil.GET_USER_TYPE:
                            getUserType();
                            break;
                        case NetUtil.GET_ASSIGNMENTS:
                            getAssignments();
                            break;
                        case NetUtil.GET_PROOFS:
                            getProofs();
                            break;
                        case NetUtil.LIST_SUBMISSIONS:
                            getSubmissions();
                            break;
                        case NetUtil.GET_SUBMISSION:
                            getSubmissionData();
                            break;
                        default:
                            sendMessage("UNKNOWN REQUEST");
                    }
                } catch (SQLException e) {
                    logger.error("SQL Error", e);
                    sendMessage("ERROR");
                }
            } catch (IOException ignored) {
                // ignored so we don't print an exception whenever client disconnects
            } catch (Throwable e) {
                logger.error("Unexpected error occurred", e);
            }
        }
    }

    private void getUserType() throws SQLException {
        PreparedStatement statement = dbManager.getStatement("SELECT user_type FROM user WHERE username = ?;");
        statement.setString(1, username);
        if (statement.execute()) {
            ResultSet rs = statement.getResultSet();
            if (rs.next())
                sendMessage(NetUtil.GET_USER_TYPE + " " + rs.getString(1));
            else
                sendMessage("ERROR");
        } else {
            sendMessage("ERROR");
        }
    }

    private void getAssignments() throws SQLException, UnsupportedEncodingException {
        PreparedStatement statement = dbManager.getStatement("SELECT c.name, a.name, a.due_date, a.assigned_by, c.id, a.id FROM assignment a, user u, class c WHERE u.class_id = a.class_id AND c.id = u.class_id AND u.username = ? ORDER BY c.id, a.due_date;");
        statement.setString(1, username);
        if (statement.execute()) {
            ResultSet rs = statement.getResultSet();
            while (rs.next()) {
                String className = URLEncoder.encode(rs.getString(1), "UTF-8");
                String assignmentName = URLEncoder.encode(rs.getString(2), "UTF-8");
                String dueDate = URLEncoder.encode(rs.getString(3), "UTF-8");
                String assignedBy = URLEncoder.encode(rs.getString(4), "UTF-8");
                int classId = rs.getInt(5);
                int assignmentId = rs.getInt(6);
                sendMessage(className + "|" + assignmentName + "|" + dueDate + "|" + assignedBy + "|" + classId + "|" + assignmentId);
            }
        }
        sendMessage("DONE");
    }

    private void getProofs() throws SQLException, IOException {
        try {
            String[] assignmentData = in.readUTF().split("\\|");
            if (assignmentData.length != 2)
                return;
            int cid, aid;
            try {
                cid = Integer.parseInt(assignmentData[0]);
                aid = Integer.parseInt(assignmentData[1]);
            } catch (NumberFormatException e) {
                return;
            }
            PreparedStatement statement = dbManager.getStatement("SELECT p.id, p.name FROM proof p, assignment a WHERE a.proof_id = p.id AND a.id = ? AND a.class_id = ?;");
            statement.setInt(1, aid);
            statement.setInt(2, cid);
            if (statement.execute()) {
                ResultSet rs = statement.getResultSet();
                while (rs.next()) {
                    int id = rs.getInt(1);
                    String name = URLEncoder.encode(rs.getString(2), "UTF-8");
                    sendMessage(id + "|" + name);
                }
            }
        } finally {
            sendMessage("DONE");
        }
    }

    private void getSubmissions() throws IOException, SQLException {
        try {
            String[] assignmentData = in.readUTF().split("\\|");
            if (assignmentData.length != 3)
                return;
            int cid, aid, pid;
            try {
                cid = Integer.parseInt(assignmentData[0]);
                aid = Integer.parseInt(assignmentData[1]);
                pid = Integer.parseInt(assignmentData[2]);
            } catch (NumberFormatException e) {
                return;
            }
            // submission(id, assignment_id, user_id, proof_id, data, timestamp, status)
            PreparedStatement statement = dbManager.getStatement("SELECT id, timestamp, status, data FROM submission WHERE class_id = ? AND assignment_id = ? AND proof_id = ? AND user_id = ?;");
            statement.setInt(1, cid);
            statement.setInt(2, aid);
            statement.setInt(3, pid);
            statement.setInt(4, userId);
            if (statement.execute()) {
                ResultSet rs = statement.getResultSet();
                while (rs.next()) {
                    int id = rs.getInt(1);
                    String timestamp = URLEncoder.encode(rs.getString(2), "UTF-8");
                    String status = URLEncoder.encode(rs.getString(3), "UTF-8");
                    sendMessage(id + "|" + timestamp + "|" + status);
                }
            }
        } finally {
            sendMessage("DONE");
        }
    }

    private void getSubmissionData() throws IOException, SQLException {
        String idStr = in.readUTF();
        try {
            int id = Integer.parseInt(idStr);
            PreparedStatement statement = dbManager.getStatement("SELECT data FROM submission WHERE id = ? LIMIT 1;");
            statement.setInt(1, id);
            ResultSet rs;
            if (statement.execute() && (rs = statement.getResultSet()).next()) {
                InputStream dataStream = rs.getBinaryStream(1);
                ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                IOUtils.copy(dataStream, byteStream);
                dataStream.close();
                sendMessage(String.valueOf(byteStream.size()));
                out.write(byteStream.toByteArray());
                out.flush();
                byteStream.close();
            } else {
                sendMessage("0");
            }
        } catch (NumberFormatException e) {
            sendMessage("0");
        }
    }

    private void createSubmission() throws IOException, SQLException {
        String[] submissionData = in.readUTF().split("\\|");
        if (submissionData.length != 3)
            sendMessage("INVALID");
        int cid, aid, pid;
        try {
            cid = Integer.parseInt(submissionData[0]);
            aid = Integer.parseInt(submissionData[1]);
            pid = Integer.parseInt(submissionData[2]);
        } catch (NumberFormatException e) {
            sendMessage("INVALID");
            return;
        }
        PreparedStatement subVerify = dbManager.getStatement("SELECT count(*) FROM assignment a, user u WHERE id a.class_id = ? AND a.id = ? AND a.proof_id = ? AND a.class_id = u.class_id AND u.id = ?;");
        subVerify.setInt(1, cid);
        subVerify.setInt(2, aid);
        subVerify.setInt(3, pid);
        subVerify.setInt(4, userId);
        ResultSet rs;
        if (subVerify.execute() && (rs = subVerify.getResultSet()).next() && rs.getInt(1) > 0) {
            sendMessage("OK");
            String sizeStr = in.readUTF();
            long size;
            try {
                size = Long.parseLong(sizeStr);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid size");
            }
            if (size > NetUtil.MAX_FILE_SIZE)
                throw new IOException("File too large");
            byte[] data = new byte[(int) size];
            if (size != in.read(data)) {
                sendMessage("FAILED");
                return;
            }
            // submission(id, class_id, assignment_id, user_id, proof_id, data, timestamp, status)
            PreparedStatement insert = dbManager.getStatement("INSERT INFO submission VALUES(NULL, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, 'grading');");
            insert.setInt(1, cid);
            insert.setInt(2, aid);
            insert.setInt(3, userId);
            insert.setInt(4, pid);
            ByteArrayInputStream is = new ByteArrayInputStream(data);
            insert.setBinaryStream(5, is);
            insert.execute();
            sendMessage("OK");
        } else {
            sendMessage("INVALID");
        }
    }

    public void sendMessage(String msg) {
        try {
            out.writeUTF(msg);
            out.flush();
        } catch (IOException e) {
            logger.error("[" + clientName + "] Failed to send message", e);
            logger.error("[" + clientName + "] Disconnecting");
            disconnect();
        }
    }

    public void disconnect() {
        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                logger.error("[" + clientName + "] Failed to close input stream", e);
            }
        }
        if (out != null) {
            try {
                out.close();
            } catch (IOException e) {
                logger.error("[" + clientName + "] Failed to close output stream", e);
            }
        }
        try {
            socket.close();
        } catch (IOException e) {
            logger.error("[" + clientName + "] Failed to close socket");
        }
        logger.info("[" + clientName + "] Disconnected");
    }

}