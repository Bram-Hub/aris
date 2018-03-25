package edu.rpi.aris.net.server;

import edu.rpi.aris.Main;
import edu.rpi.aris.net.NetUtil;
import javafx.util.Pair;
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
import java.util.*;
import java.util.concurrent.TimeUnit;

public abstract class ClientHandler implements Runnable {

    private static final Logger logger = LogManager.getLogger(ClientHandler.class);

    private static final SecureRandom random = new SecureRandom();
    private final SSLSocket socket;
    private DatabaseManager dbManager;
    private String clientName, clientVersion;
    private DataInputStream in;
    private DataOutputStream out;
    private PassiveExpiringMap<String, String> banList = new PassiveExpiringMap<>(60, TimeUnit.MINUTES);
    private PassiveExpiringMap<String, HashSet<Long>> loginAttempts = new PassiveExpiringMap<>(10, TimeUnit.MINUTES);
    private String username, userType;
    private int userId;
    private MessageDigest digest;

    ClientHandler(SSLSocket socket, DatabaseManager dbManager) {
        this.socket = socket;
        this.dbManager = dbManager;
        try {
            digest = MessageDigest.getInstance("SHA512", "BC");
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            logger.error("Failed to create MessageDigest", e);
        }
    }

    @Override
    public void run() {
        try {
            clientName = socket.getInetAddress().getHostName();
            logger.info("[" + clientName + "] Incoming connection from " + socket.getInetAddress().toString());
            socket.setUseClientMode(false);
            socket.setNeedClientAuth(false);
            socket.setSoTimeout(NetUtil.SOCKET_TIMEOUT);
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
                sendMessage(NetUtil.INVALID_VERSION);
                return;
            } else
                sendMessage(NetUtil.ARIS_NAME + " " + Main.VERSION);
            String versionVerify = in.readUTF();
            if (!versionVerify.equals(NetUtil.VERSION_OK))
                return;
            logger.info("[" + clientName + "] Waiting for client auth");
            if (!verifyAuth()) {
                logger.info("[" + clientName + "] Auth failed");
                return;
            }
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
        username = URLDecoder.decode(auth[2], "UTF-8").toLowerCase();
        String pass = URLDecoder.decode(auth[3], "UTF-8");
        try {
            PreparedStatement statement = dbManager.getStatement("SELECT salt, password_hash, access_token, id, user_type FROM user WHERE username = ?;");
            statement.setString(1, username);
            statement.execute();
            ResultSet set = statement.getResultSet();
            if (set.next()) {
                String salt = set.getString(1);
                String savedHash = set.getString(auth[1].equals(NetUtil.AUTH_PASS) ? 2 : 3);
                userId = set.getInt(4);
                userType = set.getString(5);
                digest.update(Base64.getDecoder().decode(salt));
                String hash = Base64.getEncoder().encodeToString(digest.digest(pass.getBytes()));
                if (hash.equals(savedHash)) {
                    String access_token = generateAccessToken();
                    digest.update(Base64.getDecoder().decode(salt));
                    String hashed = Base64.getEncoder().encodeToString(digest.digest(access_token.getBytes()));
                    PreparedStatement updateAccessToken = dbManager.getStatement("UPDATE user SET access_token = ? WHERE username = ?;");
                    updateAccessToken.setString(1, hashed);
                    updateAccessToken.setString(2, username);
                    updateAccessToken.execute();
                    sendMessage(NetUtil.AUTH_OK + " " + URLEncoder.encode(access_token, "UTF-8"));
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
        } catch (SQLException e) {
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
        random.nextBytes(tokenBytes);
        return Base64.getEncoder().encodeToString(tokenBytes);
    }

    private void messageWatch() {
        try {
            //noinspection InfiniteLoopStatement
            while (true) {
                String msg = in.readUTF();
                logger.info("[" + clientName + "] Request: " + msg);
                try {
                    switch (msg) {
                        case NetUtil.GET_USER_INFO:
                            getUserInfo();
                            break;
                        case NetUtil.GET_ASSIGNMENTS:
                            getAssignments();
                            break;
                        case NetUtil.GET_ASSIGNMENT_DETAIL:
                            getAssignmentDetail();
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
                        case NetUtil.CREATE_SUBMISSION:
                            createSubmission();
                            break;
                        case NetUtil.CREATE_ASSIGNMENT:
                            createAssignment();
                            break;
                        case NetUtil.DELETE_ASSIGNMENT:
                            deleteAssignment();
                            break;
                        case NetUtil.UPDATE_ASSIGNMENT:
                            updateAssignment();
                            break;
                        case NetUtil.CREATE_PROOF:
                            createProof();
                            break;
                        case NetUtil.DELETE_PROOF:
                            deleteProof();
                            break;
                        case NetUtil.UPDATE_PROOF:
                            updateProof();
                            break;
                        case NetUtil.CREATE_USER:
                            createUser();
                            break;
                        case NetUtil.DELETE_USER:
                            deleteUser();
                            break;
                        case NetUtil.UPDATE_USER:
                            updateUser();
                            break;
                        case NetUtil.CREATE_CLASS:
                            createClass();
                            break;
                        case NetUtil.DELETE_CLASS:
                            deleteClass();
                            break;
                        case NetUtil.UPDATE_CLASS:
                            updateClass();
                            break;
                        default:
                            sendMessage("UNKNOWN REQUEST");
                    }
                } catch (SQLException e) {
                    logger.error("SQL Error", e);
                    sendMessage(NetUtil.ERROR);
                }
            }
        } catch (IOException ignored) {
            // ignored so we don't print an exception whenever client disconnects
        } catch (Throwable e) {
            logger.error("Unexpected error occurred", e);
        }
    }

    private void getUserInfo() throws SQLException, UnsupportedEncodingException {
        PreparedStatement statement = dbManager.getStatement("SELECT user_type FROM user WHERE username = ?;");
        statement.setString(1, username);
        if (statement.execute()) {
            ResultSet rs = statement.getResultSet();
            if (rs.next())
                sendMessage(rs.getString(1));
            else
                sendMessage(NetUtil.ERROR);
        } else {
            sendMessage(NetUtil.ERROR);
        }
        statement = dbManager.getStatement("SELECT c.id, c.name FROM class c, user u, user_class uc WHERE u.id = uc.user_id AND c.id = uc.class_id AND u.id = ?");
        statement.setInt(1, userId);
        ResultSet rs;
        if (statement.execute() && (rs = statement.getResultSet()) != null) {
            while (rs.next())
                sendMessage(rs.getInt(1) + "|" + URLEncoder.encode(rs.getString(2), "UTF-8"));
            sendMessage(NetUtil.DONE);
        } else
            sendMessage(NetUtil.ERROR);
    }

    private void getAssignments() throws SQLException, IOException {
        String idStr = in.readUTF();
        int id;
        try {
            id = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            sendMessage(NetUtil.ERROR);
            return;
        }
        PreparedStatement statement = dbManager.getStatement("SELECT a.name, a.due_date, u2.username, a.id FROM assignment a, user u, user u2, class c, user_class uc WHERE uc.user_id = u.id AND uc.class_id = c.id AND a.class_id = uc.class_id AND a.assigned_by = u2.id AND u.username = ? AND c.id = ? GROUP BY a.id ORDER BY a.due_date;");
        statement.setString(1, username);
        statement.setInt(2, id);
        if (statement.execute()) {
            ResultSet rs = statement.getResultSet();
            while (rs.next()) {
                String assignmentName = URLEncoder.encode(rs.getString(1), "UTF-8");
                String dueDate = URLEncoder.encode(rs.getString(2), "UTF-8");
                String assignedBy = URLEncoder.encode(rs.getString(3), "UTF-8");
                int assignmentId = rs.getInt(4);
                sendMessage(assignmentName + "|" + dueDate + "|" + assignedBy + "|" + assignmentId);
            }
        }
        sendMessage(NetUtil.DONE);
    }

    private void getAssignmentDetail() throws IOException, SQLException {
        String[] idData = in.readUTF().split("\\|");
        if (idData.length != 2) {
            sendMessage(NetUtil.INVALID);
            return;
        }
        int cid, aid;
        try {
            cid = Integer.parseInt(idData[0]);
            aid = Integer.parseInt(idData[1]);
        } catch (NumberFormatException e) {
            sendMessage(NetUtil.ERROR);
            return;
        }
        PreparedStatement assignments = dbManager.getStatement("SELECT p.id, p.name FROM assignment a, proof p WHERE a.class_id = ? AND a.id = ? AND a.proof_id = p.id;");
        assignments.setInt(1, cid);
        assignments.setInt(2, aid);
        if (!assignments.execute()) {
            sendMessage(NetUtil.ERROR);
            return;
        }
        ArrayList<String> messages = new ArrayList<>();
        ResultSet rs = assignments.getResultSet();
        while (rs.next())
            messages.add(rs.getInt(1) + "|" + URLEncoder.encode(rs.getString(2), "UTF-8"));
        sendMessage(String.valueOf(messages.size()));
        messages.forEach(this::sendMessage);
        messages.clear();
        PreparedStatement submissions = dbManager.getStatement("SELECT id, proof_id, time, status FROM submission WHERE class_id = ? AND assignment_id = ? AND user_id = ? ORDER BY proof_id, id DESC;");
        submissions.setInt(1, cid);
        submissions.setInt(2, aid);
        submissions.setInt(3, userId);
        if (!submissions.execute()) {
            sendMessage(NetUtil.ERROR);
            return;
        }
        rs = submissions.getResultSet();
        while (rs.next())
            messages.add(rs.getInt(1) + "|" + rs.getInt(2) + "|" + URLEncoder.encode(rs.getString(3), "UTF-8") + "|" + URLEncoder.encode(rs.getString(4), "UTF-8"));
        sendMessage(String.valueOf(messages.size()));
        messages.forEach(this::sendMessage);
    }

    private void getProofs() throws SQLException, IOException {
        String[] assignmentData = in.readUTF().split("\\|");
        if (assignmentData.length != 2) {
            sendMessage(NetUtil.INVALID);
            return;
        }
        int cid, aid;
        try {
            cid = Integer.parseInt(assignmentData[0]);
            aid = Integer.parseInt(assignmentData[1]);
        } catch (NumberFormatException e) {
            sendMessage(NetUtil.ERROR);
            return;
        }
        PreparedStatement statement = dbManager.getStatement("SELECT p.id, p.name, p.created_by FROM proof p, assignment a WHERE a.proof_id = p.id AND a.id = ? AND a.class_id = ?;");
        statement.setInt(1, aid);
        statement.setInt(2, cid);
        if (statement.execute()) {
            ResultSet rs = statement.getResultSet();
            while (rs.next()) {
                int id = rs.getInt(1);
                String name = URLEncoder.encode(rs.getString(2), "UTF-8");
                String createdBy = URLEncoder.encode(rs.getString(3), "UTF-8");
                sendMessage(id + "|" + name + "|" + createdBy);
            }
        }
        sendMessage(NetUtil.DONE);
    }

    private void getSubmissions() throws IOException, SQLException {
        try {
            String[] assignmentData = in.readUTF().split("\\|");
            if ((assignmentData.length != 3 && userType.equals(NetUtil.USER_STUDENT)) || (assignmentData.length != 4 && userType.equals(NetUtil.USER_INSTRUCTOR)))
                return;
            int cid, aid, pid, uid = userId;
            try {
                cid = Integer.parseInt(assignmentData[0]);
                aid = Integer.parseInt(assignmentData[1]);
                pid = Integer.parseInt(assignmentData[2]);
                if (userType.equals(NetUtil.USER_INSTRUCTOR))
                    uid = Integer.parseInt(assignmentData[3]);
            } catch (NumberFormatException e) {
                return;
            }
            // submission(id, assignment_id, user_id, proof_id, data, timestamp, status)
            PreparedStatement statement = dbManager.getStatement("SELECT id, timestamp, status, data FROM submission WHERE class_id = ? AND assignment_id = ? AND proof_id = ? AND user_id = ?;");
            statement.setInt(1, cid);
            statement.setInt(2, aid);
            statement.setInt(3, pid);
            statement.setInt(4, uid);
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
            sendMessage(NetUtil.DONE);
        }
    }

    private void getSubmissionData() throws IOException, SQLException {
        String idStr = in.readUTF();
        try {
            int id = Integer.parseInt(idStr);
            PreparedStatement statement;
            if (userType.equals(NetUtil.USER_INSTRUCTOR))
                statement = dbManager.getStatement("SELECT data FROM submission WHERE id = ? LIMIT 1;");
            else {
                statement = dbManager.getStatement("SELECT data FROM submission WHERE id = ? AND user_id = ? LIMIT 1;");
                statement.setInt(2, userId);
            }
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
        PreparedStatement subVerify = dbManager.getStatement("SELECT count(*) FROM assignment a, user u, user_class uc WHERE id a.class_id = ? AND a.id = ? AND a.proof_id = ? AND a.class_id = uc.class_id AND u.id = uc.user_id AND u.id = ?;");
        subVerify.setInt(1, cid);
        subVerify.setInt(2, aid);
        subVerify.setInt(3, pid);
        subVerify.setInt(4, userId);
        ResultSet rs;
        if (subVerify.execute() && (rs = subVerify.getResultSet()).next() && rs.getInt(1) > 0) {
            sendMessage(NetUtil.OK);
            String sizeStr = in.readUTF();
            long size;
            try {
                size = Long.parseLong(sizeStr);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid size");
            }
            if (size > NetUtil.MAX_FILE_SIZE) {
                sendMessage("TOO_LARGE");
                return;
            }
            byte[] data = new byte[(int) size];
            if (size != in.read(data)) {
                sendMessage("FAILED");
                return;
            }
            PreparedStatement insert = dbManager.getStatement("INSERT INTO submission VALUES(NULL, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, 'grading');");
            insert.setInt(1, cid);
            insert.setInt(2, aid);
            insert.setInt(3, userId);
            insert.setInt(4, pid);
            ByteArrayInputStream is = new ByteArrayInputStream(data);
            insert.setBinaryStream(5, is);
            insert.execute();
            sendMessage(NetUtil.OK);
            // TODO: schedule for grading
        } else {
            sendMessage(NetUtil.INVALID);
        }
    }

    private void createAssignment() throws IOException, SQLException {
        String[] assignmentData = in.readUTF().split("\\|");
        if (!userType.equals(NetUtil.USER_INSTRUCTOR)) {
            sendMessage(NetUtil.UNAUTHORIZED);
            return;
        }
        if (assignmentData.length != 4) {
            sendMessage(NetUtil.INVALID);
            return;
        }
        String[] proofIdStrings = assignmentData[1].split(",");
        int cid;
        int[] proof_ids = new int[proofIdStrings.length];
        long time;
        try {
            cid = Integer.parseInt(assignmentData[0]);
            time = Long.parseLong(assignmentData[3]);
            for (int i = 0; i < proofIdStrings.length; ++i)
                proof_ids[i] = Integer.parseInt(proofIdStrings[i]);
        } catch (NumberFormatException e) {
            sendMessage(NetUtil.ERROR);
            return;
        }
        String name = URLDecoder.decode(assignmentData[2], "UTF-8");
        String date = NetUtil.DATE_FORMAT.format(new Date(time));
        PreparedStatement select = dbManager.getStatement("SELECT id FROM assignment ORDER BY id DESC LIMIT 1;");
        int id = 0;
        ResultSet rs;
        if (select.execute() && (rs = select.getResultSet()).next())
            id = rs.getInt(1) + 1;
        for (int pid : proof_ids) {
            PreparedStatement statement = dbManager.getStatement("INSERT INTO assignment VALUES(?, ?, ?, ?, ?, ?);");
            statement.setInt(1, id);
            statement.setInt(2, cid);
            statement.setInt(3, pid);
            statement.setString(4, name);
            statement.setString(5, date);
            statement.setInt(6, userId);
            statement.execute();
        }
        sendMessage(NetUtil.OK);
    }

    private void deleteAssignment() throws IOException, SQLException {
        String[] idStrings = in.readUTF().split("\\|");
        if (!userType.equals(NetUtil.USER_INSTRUCTOR)) {
            sendMessage(NetUtil.UNAUTHORIZED);
            return;
        }
        if (idStrings.length != 2) {
            sendMessage(NetUtil.INVALID);
            return;
        }
        int cid, aid;
        try {
            cid = Integer.parseInt(idStrings[0]);
            aid = Integer.parseInt(idStrings[1]);
        } catch (NumberFormatException e) {
            sendMessage(NetUtil.ERROR);
            return;
        }
        PreparedStatement statement = dbManager.getStatement("DELETE FROM assignment WHERE id = ? AND class_id = ?;");
        statement.setInt(1, aid);
        statement.setInt(2, cid);
        statement.execute();
        sendMessage(NetUtil.OK);
    }

    private void updateAssignment() throws IOException, SQLException {
        String[] str = in.readUTF().split("\\|");
        if (!userType.equals(NetUtil.USER_INSTRUCTOR)) {
            sendMessage(NetUtil.UNAUTHORIZED);
            return;
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
        switch (str[0]) {
            case "RENAME":
                String name = URLDecoder.decode(str[3], "UTF-8");
                PreparedStatement statement = dbManager.getStatement("UPDATE assignment SET name = ? WHERE id = ? AND class_id = ?;");
                statement.setString(1, name);
                statement.setInt(2, aid);
                statement.setInt(3, cid);
                statement.execute();
                break;
            case "ADD_PROOF":
                int pid;
                try {
                    pid = Integer.parseInt(str[3]);
                } catch (NumberFormatException e) {
                    sendMessage(NetUtil.ERROR);
                    return;
                }
                PreparedStatement select = dbManager.getStatement("SELECT name, due_date, assigned_by FROM assignment WHERE id = ? AND class_id = ?;");
                select.setInt(1, aid);
                select.setInt(2, cid);
                ResultSet rs;
                if (!select.execute() || !(rs = select.getResultSet()).next()) {
                    sendMessage(NetUtil.ERROR);
                    return;
                }
                String n = rs.getString(1);
                String due_date = rs.getString(2);
                String assigned = rs.getString(3);
                PreparedStatement addProof = dbManager.getStatement("INSERT INTO assignment VALUES(?, ?, ?, ?, ?, ?);");
                addProof.setInt(1, aid);
                addProof.setInt(2, cid);
                addProof.setInt(3, pid);
                addProof.setString(4, n);
                addProof.setString(5, due_date);
                addProof.setString(6, assigned);
                addProof.execute();
                break;
            case "REMOVE_PROOF":
                try {
                    pid = Integer.parseInt(str[3]);
                } catch (NumberFormatException e) {
                    sendMessage(NetUtil.ERROR);
                    return;
                }
                PreparedStatement remove = dbManager.getStatement("DELETE FROM assignment WHERE id = ? AND class_id = ? AND proof_id = ?;");
                remove.setInt(1, aid);
                remove.setInt(2, cid);
                remove.setInt(3, pid);
                remove.execute();
                remove = dbManager.getStatement("DELETE FROM submission WHERE assignment_id = ? AND class_id = ? AND proof_id = ?");
                remove.setInt(1, aid);
                remove.setInt(2, cid);
                remove.setInt(3, pid);
                remove.execute();
                break;
            case "CHANGE_DUE":
                long time;
                try {
                    time = Long.parseLong(str[3]);
                } catch (NumberFormatException e) {
                    sendMessage(NetUtil.ERROR);
                    return;
                }
                PreparedStatement statement1 = dbManager.getStatement("UPDATE assignment SET due_date = ? WHERE id = ? AND class_id = ?;");
                statement1.setString(1, NetUtil.DATE_FORMAT.format(new Date(time)));
                statement1.setInt(2, aid);
                statement1.setInt(3, cid);
                statement1.execute();
                break;
        }
        sendMessage("OK");
    }

    private void createProof() throws IOException, SQLException {
        String[] proofInfo = in.readUTF().split("\\|");
        if (!userType.equals(NetUtil.USER_INSTRUCTOR)) {
            sendMessage(NetUtil.UNAUTHORIZED);
            return;
        }
        if (proofInfo.length != 2) {
            sendMessage(NetUtil.INVALID);
            return;
        }
        long size;
        try {
            size = Long.parseLong(proofInfo[1]);
        } catch (NumberFormatException e) {
            sendMessage(NetUtil.ERROR);
            return;
        }
        if (size <= 0) {
            sendMessage(NetUtil.OK);
            return;
        }
        if (size > NetUtil.MAX_FILE_SIZE) {
            sendMessage("TOO LARGE");
            return;
        }
        byte[] data = new byte[(int) size];
        if (size != in.read(data)) {
            sendMessage("FAILED");
            return;
        }
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        PreparedStatement statement = dbManager.getStatement("INSERT INTO proof VALUES(NULL, ?, ?, ?)");
        statement.setString(1, proofInfo[0]);
        statement.setBinaryStream(2, bis);
        statement.setInt(3, userId);
        statement.execute();
        sendMessage(NetUtil.OK);
    }

    private void deleteProof() throws IOException, SQLException {
        String idStr = in.readUTF();
        if (!userType.equals(NetUtil.USER_INSTRUCTOR)) {
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
        PreparedStatement statement = dbManager.getStatement("DELETE FROM proof WHERE id = ?;");
        statement.setInt(1, id);
        statement.execute();
        statement = dbManager.getStatement("DELETE FROM assignment WHERE proof_id = ?;");
        statement.setInt(1, id);
        statement.execute();
        statement = dbManager.getStatement("DELETE FROM submission WHERE proof_id = ?;");
        statement.setInt(1, id);
        statement.execute();
        sendMessage(NetUtil.OK);
    }

    private void updateProof() throws IOException, SQLException {
        String[] proofData = in.readUTF().split("\\|");
        if (!userType.equals(NetUtil.USER_INSTRUCTOR)) {
            sendMessage(NetUtil.UNAUTHORIZED);
            return;
        }
        if (proofData.length != 3) {
            sendMessage(NetUtil.INVALID);
            return;
        }
        int id;
        try {
            id = Integer.parseInt(proofData[0]);
        } catch (NumberFormatException e) {
            sendMessage(NetUtil.ERROR);
            return;
        }
        String name = proofData[1];
        long size;
        try {
            size = Long.parseLong(proofData[2]);
        } catch (NumberFormatException e) {
            sendMessage(NetUtil.ERROR);
            return;
        }
        PreparedStatement updateName = dbManager.getStatement("UPDATE proof SET name = ? WHERE id = ?");
        updateName.setString(1, name);
        updateName.setInt(2, id);
        updateName.execute();
        if (size > 0) {
            if (size > NetUtil.MAX_FILE_SIZE) {
                sendMessage("TOO_LARGE");
                return;
            }
            byte[] data = new byte[(int) size];
            if (size != in.read(data)) {
                sendMessage("FAILED");
                return;
            }
            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            PreparedStatement updateData = dbManager.getStatement("UPDATE proof SET data = ? WHERE id = ?");
            updateData.setBinaryStream(1, bis);
            updateData.setInt(2, id);
            updateData.execute();
        }
        sendMessage(NetUtil.OK);
    }

    private void createUser() throws IOException, SQLException {
        String[] userData = in.readUTF().split("\\|");
        if (!userType.equals(NetUtil.USER_INSTRUCTOR)) {
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
        PreparedStatement count = dbManager.getStatement("SELECT count(*) FROM user WHERE username = ?;");
        count.setString(1, username);
        ResultSet rs;
        if (count.execute() && (rs = count.getResultSet()).next() && rs.getInt(1) > 0) {
            sendMessage(NetUtil.USER_EXISTS);
            return;
        }
        Pair<String, String> res = dbManager.createUser(username, password, userType);
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
        if (!userType.equals(NetUtil.USER_INSTRUCTOR)) {
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
        PreparedStatement statement = dbManager.getStatement("DELETE FROM user WHERE id = ?;");
        statement.setInt(1, id);
        statement.execute();
        statement = dbManager.getStatement("DELETE FROM user_class WHERE user_id = ?;");
        statement.setInt(1, id);
        statement.execute();
        statement = dbManager.getStatement("DELETE FROM submission WHERE user_id = ?;");
        statement.setInt(1, id);
        statement.execute();
        sendMessage(NetUtil.OK);
    }

    private void updateUser() throws IOException, SQLException {
        String[] userData = in.readUTF().split("\\|");
        if (userData.length != 3) {
            sendMessage(NetUtil.INVALID);
            return;
        }
        String username = userData[0];
        if (!username.equals(this.username) && !userType.equals(NetUtil.USER_INSTRUCTOR)) {
            sendMessage(NetUtil.UNAUTHORIZED);
            return;
        }
        String newPass = URLDecoder.decode(userData[1], "UTF-8");
        String type = URLDecoder.decode(userData[2], "UTF-8");
        PreparedStatement update = dbManager.getStatement("UPDATE user SET user_type = ? WHERE username = ?;");
        update.setString(1, type);
        update.setString(2, username);
        update.execute();
        if (newPass != null && newPass.length() > 0) {
            Pair<String, String> res = dbManager.setPassword(username, newPass);
            if (!res.getValue().equals(NetUtil.OK)) {
                sendMessage(res.getValue());
                return;
            }
        }
        sendMessage(NetUtil.OK);
    }

    private void createClass() throws IOException, SQLException {
        String name = in.readUTF();
        if (!userType.equals(NetUtil.USER_INSTRUCTOR)) {
            sendMessage(NetUtil.UNAUTHORIZED);
            return;
        }
        PreparedStatement insertClass = dbManager.getStatement("INSERT INTO class VALUES(NULL, ?);");
        insertClass.setString(1, name);
        insertClass.execute();
        // user(id, username, class_id, user_type, salt, password_hash, access_token)
        PreparedStatement selectClassId = dbManager.getStatement("SELECT id FROM class ORDER BY id DESC LIMIT 1;");
        ResultSet rs;
        if (!selectClassId.execute() || !(rs = selectClassId.getResultSet()).next()) {
            sendMessage(NetUtil.ERROR);
            return;
        }
        int id = rs.getInt(1);
        PreparedStatement insert = dbManager.getStatement("INSERT INTO user_class VALUES(?, ?);");
        insert.setInt(1, userId);
        insert.setInt(2, id);
        insert.execute();
        sendMessage(NetUtil.OK + " " + id);
    }

    private void deleteClass() throws IOException, SQLException {
        String idStr = in.readUTF();
        if (!userType.equals(NetUtil.USER_INSTRUCTOR)) {
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
        PreparedStatement statement = dbManager.getStatement("DELETE FROM class WHERE id = ?;");
        statement.setInt(1, id);
        statement.execute();
        statement = dbManager.getStatement("DELETE FROM user_class WHERE class_id = ?;");
        statement.setInt(1, id);
        statement.execute();
        statement = dbManager.getStatement("DELETE FROM assignment WHERE class_id = ?;");
        statement.setInt(1, id);
        statement.execute();
        statement = dbManager.getStatement("DELETE FROM submission WHERE class_id = ?;");
        statement.setInt(1, id);
        statement.execute();
        sendMessage(NetUtil.OK);
    }

    private void updateClass() throws IOException, SQLException {
        String[] classData = in.readUTF().split("\\|");
        if (!userType.equals(NetUtil.USER_INSTRUCTOR)) {
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
        PreparedStatement statement = dbManager.getStatement("UPDATE class SET name = ? WHERE id = ?;");
        statement.setString(1, URLDecoder.decode(classData[1], "UTF-8"));
        statement.setInt(2, id);
        statement.execute();
        sendMessage(NetUtil.OK);
    }

    private void sendMessage(String msg) {
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
        try {
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
        } finally {
            onDisconnect(this);
        }
    }

    public abstract void onDisconnect(ClientHandler clientHandler);

}