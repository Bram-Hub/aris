package edu.rpi.aris.net.message;

import edu.rpi.aris.net.NetUtil;
import edu.rpi.aris.net.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ProofDeleteMsg extends Message {

    private final int pid;

    public ProofDeleteMsg(int pid) {
        this.pid = pid;
    }

    // DO NOT REMOVE!! Default constructor is required for gson deserialization
    private ProofDeleteMsg() {
        pid = 0;
    }

    @Override
    public ErrorType processMessage(Connection connection, User user) throws SQLException {
        if (!user.userType.equals(NetUtil.USER_INSTRUCTOR))
            return ErrorType.UNAUTHORIZED;
        try (PreparedStatement deleteProof = connection.prepareStatement("DELETE FROM proof WHERE id = ?;")) {
            deleteProof.setInt(1, pid);
            deleteProof.executeUpdate();
        }
        return null;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.DELETE_PROOF;
    }

    @Override
    public boolean checkValid() {
        return pid > 0;
    }
}
