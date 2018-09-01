package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.Problem;
import edu.rpi.aris.assign.User;
import edu.rpi.aris.assign.UserType;
import edu.rpi.aris.assign.spi.ArisModule;

import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ProblemCreateMsg<T extends ArisModule> extends DataMessage {

    private final String name;
    private final String moduleName;
    private Problem<T> problem;

    public ProblemCreateMsg(String name, String moduleName, Problem<T> problem) {
        this.name = name;
        this.moduleName = moduleName;
        this.problem = problem;
    }

    // DO NOT REMOVE!! Default constructor is required for gson deserialization
    private ProblemCreateMsg() {
        name = null;
        moduleName = null;
        problem = null;
    }

    @Override
    public ErrorType processMessage(Connection connection, User user) throws SQLException {
        if (!UserType.hasPermission(user, UserType.INSTRUCTOR))
            return ErrorType.UNAUTHORIZED;
        //TODO
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO problem (name, data, created_by, created_on, module_name) VALUES (?, ?, (SELECT username FROM users WHERE id = ? LIMIT 1), now(), ?)")) {
            statement.setString(1, name);
//            statement.setBytes(2, problem);
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
        return name != null && problem != null && moduleName != null;
    }

    @Override
    public void sendData(OutputStream out) {

    }

    @Override
    public void receiveData(InputStream in) {

    }
}
