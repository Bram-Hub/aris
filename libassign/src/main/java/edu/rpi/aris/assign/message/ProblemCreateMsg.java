package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.NetUtil;
import edu.rpi.aris.assign.User;
import edu.rpi.aris.assign.UserType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ProblemCreateMsg extends Message {

    private final String name, moduleName;
    private final byte[] problemData;

    public ProblemCreateMsg(String name, String moduleName, byte[] problemData) {
        this.name = name;
        this.moduleName = moduleName;
        this.problemData = problemData;
    }

    // DO NOT REMOVE!! Default constructor is required for gson deserialization
    private ProblemCreateMsg() {
        name = null;
        moduleName = null;
        problemData = null;
    }

    @Override
    public ErrorType processMessage(Connection connection, User user) throws SQLException {
        if (!UserType.hasPermission(user, UserType.INSTRUCTOR))
            return ErrorType.UNAUTHORIZED;
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO problem (name, data, created_by, created_on, module_name) VALUES (?, ?, (SELECT username FROM users WHERE id = ? LIMIT 1), now(), ?)")) {
            statement.setString(1, name);
            statement.setBytes(2, problemData);
            statement.setInt(3, user.uid);
            statement.setString(4, moduleName);
            statement.executeUpdate();
        }
        return null;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.CREATE_PROBLEM;
    }

    @Override
    public boolean checkValid() {
        return name != null && problemData != null && moduleName != null;
    }
}
