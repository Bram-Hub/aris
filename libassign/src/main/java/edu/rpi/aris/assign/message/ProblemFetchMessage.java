package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.ModuleService;
import edu.rpi.aris.assign.ProblemConverter;
import edu.rpi.aris.assign.ServerPermissions;
import edu.rpi.aris.assign.User;
import edu.rpi.aris.assign.spi.ArisModule;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class ProblemFetchMessage<T extends ArisModule> extends ProblemMessage<T> {

    private final int pid;

    public ProblemFetchMessage(int pid, String moduleName) {
        super(moduleName, null);
        this.pid = pid;
    }

    // DO NOT REMOVE!! Default constructor is required for gson deserialization
    private ProblemFetchMessage() {
        super(null, null);
        pid = -1;
    }

    @Override
    public ErrorType processMessage(Connection connection, User user, ServerPermissions permissions) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT module_name, data FROM problem WHERE id = ?;")) {
            statement.setInt(1, pid);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next())
                    return ErrorType.NOT_FOUND;
                String moduleName = rs.getString(1);
                ArisModule<T> module = ModuleService.getService().getModule(moduleName);
                if (module == null)
                    return ErrorType.MISSING_MODULE;
                ProblemConverter<T> converter = module.getProblemConverter();
                try (InputStream in = rs.getBinaryStream(2)) {
                    setProblem(converter.loadProblem(in, false));
                }
            }
        }
        return null;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.FETCH_PROBLEM;
    }

    public int getPid() {
        return pid;
    }
}
