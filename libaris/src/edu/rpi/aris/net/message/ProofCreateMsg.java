package edu.rpi.aris.net.message;

import edu.rpi.aris.net.NetUtil;
import edu.rpi.aris.net.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ProofCreateMsg extends Message {

    private final String name;
    private final byte[] proofData;

    public ProofCreateMsg(String name, byte[] proofData) {
        this.name = name;
        this.proofData = proofData;
    }

    // DO NOT REMOVE!! Default constructor is required for gson deserialization
    private ProofCreateMsg() {
        name = null;
        proofData = null;
    }

    @Override
    public ErrorType processMessage(Connection connection, User user) throws SQLException {
        if (!user.userType.equals(NetUtil.USER_INSTRUCTOR))
            return ErrorType.UNAUTHORIZED;
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO proof (name, data, created_by, created_on) VALUES (?, ?, (SELECT username FROM users WHERE id = ? LIMIT 1), now())")) {
            statement.setString(1, name);
            statement.setBytes(2, proofData);
            statement.setInt(3, user.uid);
            statement.executeUpdate();
        }
        return null;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.CREATE_PROOF;
    }

    @Override
    public boolean checkValid() {
        return name != null && proofData != null;
    }
}
