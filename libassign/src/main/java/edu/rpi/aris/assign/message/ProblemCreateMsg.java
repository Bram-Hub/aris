package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.*;
import edu.rpi.aris.assign.spi.ArisModule;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class ProblemCreateMsg<T extends ArisModule> extends ProblemMessage<T> {

    private final String name;
    private int pid;

    public ProblemCreateMsg(String name, String moduleName, Problem<T> problem) {
        super(moduleName, problem);
        this.name = name;
    }

    // DO NOT REMOVE!! Default constructor is required for gson deserialization
    private ProblemCreateMsg() {
        super(null, null);
        name = null;
    }

    @Override
    public ErrorType processMessage(Connection connection, User user) throws Exception {
        if (!UserType.hasPermission(user, UserType.INSTRUCTOR))
            return ErrorType.UNAUTHORIZED;
        ArisModule<T> module = ModuleService.getService().getModule(getModuleName());
        if (module == null)
            return ErrorType.MISSING_MODULE;
        ProblemConverter<T> converter = module.getProblemConverter();
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO problem (name, data, created_by, created_on, module_name) VALUES (?, ?, (SELECT username FROM users WHERE id = ? LIMIT 1), now(), ?) RETURNING id")) {
            statement.setString(1, name);

            PipedInputStream pis = new PipedInputStream();
            PipedOutputStream pos = new PipedOutputStream(pis);
            converter.convertProblem(getProblem(), pos, false);
            pos.close();
            statement.setBinaryStream(2, pis);

            statement.setInt(3, user.uid);
            statement.setString(4, getModuleName());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next())
                    pid = rs.getInt(1);
            }
        }
        setProblem(null);
        return null;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.CREATE_PROBLEM;
    }

    @Override
    public boolean checkValid() {
        return name != null && super.checkValid();
    }

    public int getPid() {
        return pid;
    }

    public String getName() {
        return name;
    }

}
