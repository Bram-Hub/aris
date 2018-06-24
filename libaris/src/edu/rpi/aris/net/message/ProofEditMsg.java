package edu.rpi.aris.net.message;

import edu.rpi.aris.net.NetUtil;
import edu.rpi.aris.net.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ProofEditMsg extends Message {

    private final int pid;
    private final String name;
    private final byte[] proofData;

    public ProofEditMsg(int pid, String name) {
        this(pid, name, null);
    }

    public ProofEditMsg(int pid, byte[] proofData) {
        this(pid, null, proofData);
    }

    public ProofEditMsg(int pid, String name, byte[] proofData) {
        this.pid = pid;
        this.name = name;
        this.proofData = proofData;
    }

    // DO NOT REMOVE!! Default constructor is required for gson deserialization
    private ProofEditMsg() {
        pid = 0;
        name = null;
        proofData = null;
    }

    @Override
    public ErrorType processMessage(Connection connection, User user) throws SQLException {
        if (!user.userType.equals(NetUtil.USER_INSTRUCTOR))
            return ErrorType.UNAUTHORIZED;
        if (name != null) {
            try (PreparedStatement updateName = connection.prepareStatement("UPDATE proof SET name = ? WHERE id = ?")) {
                updateName.setString(1, name);
                updateName.setInt(2, pid);
                updateName.executeUpdate();
            }
        }
        if (proofData != null) {
            try (PreparedStatement updateData = connection.prepareStatement("UPDATE proof SET data = ? WHERE id = ?")) {
                updateData.setBytes(1, proofData);
                updateData.setInt(2, pid);
                updateData.executeUpdate();
            }
        }
        return null;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.EDIT_PROOF;
    }

    @Override
    public boolean checkValid() {
        return pid > 0;
    }
}
