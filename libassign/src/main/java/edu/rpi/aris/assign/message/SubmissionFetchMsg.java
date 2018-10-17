package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.*;
import edu.rpi.aris.assign.spi.ArisModule;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class SubmissionFetchMsg<T extends ArisModule> extends ProblemMessage<T> {

    private final int pid;
    private final int cid;
    private final int aid;
    private final int sid;
    private final int uid;

    public SubmissionFetchMsg(int cid, int aid, int pid, int sid, int uid, String moduleName) {
        super(moduleName, null, Perm.SUBMISSION_FETCH);
        this.cid = cid;
        this.aid = aid;
        this.pid = pid;
        this.sid = sid;
        this.uid = uid;
    }

    public SubmissionFetchMsg(int cid, int aid, int pid, int sid, String moduleName) {
        this(cid, aid, pid, sid, -1, moduleName);
    }

    // DO NOT REMOVE!! Default constructor is required for gson deserialization
    private SubmissionFetchMsg() {
        this(-1, -1, -1, -1, null);
    }

    @Override
    public ErrorType processMessage(Connection connection, User user, ServerPermissions permissions) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT p.module_name, s.data FROM submission s, problem p WHERE s.id = ? AND s.class_id=? AND s.assignment_id=? AND s.user_id=? AND s.problem_id=? AND s.problem_id = p.id;")) {
            statement.setInt(1, sid);
            statement.setInt(2, cid);
            statement.setInt(3, aid);
            if (uid > 0) {
                if (!permissions.hasClassPermission(user, cid, Perm.ASSIGNMENT_GET_INSTRUCTOR, connection))
                    return ErrorType.UNAUTHORIZED;
                statement.setInt(4, uid);
            } else {
                statement.setInt(4, user.uid);
            }
            statement.setInt(5, pid);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next())
                    return ErrorType.NOT_FOUND;
                String moduleName = rs.getString(1);
                ArisModule<T> module = ModuleService.getService().getModule(moduleName);
                if (module == null)
                    return ErrorType.MISSING_MODULE;
                ProblemConverter<T> converter = module.getProblemConverter();
                try (InputStream in = rs.getBinaryStream(2)) {
                    setProblem(converter.loadProblem(in, true));
                }
            }
        }
        return null;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.FETCH_SUBMISSION;
    }

}
