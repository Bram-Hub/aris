package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.NetUtil;
import edu.rpi.aris.assign.Perm;
import edu.rpi.aris.assign.ServerPermissions;
import edu.rpi.aris.assign.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.ArrayList;

public class ProblemsGetMsg extends Message {

    private final ArrayList<MsgUtil.ProblemInfo> problems = new ArrayList<>();

    public ProblemsGetMsg() {
        super(Perm.PROBLEMS_GET);
    }

    public ArrayList<MsgUtil.ProblemInfo> getProblems() {
        return problems;
    }

    @Override
    public ErrorType processMessage(Connection connection, User user, ServerPermissions permissions) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id, name, created_by, created_on, module_name FROM problem ORDER BY created_on DESC;")) {
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt(1);
                    String name = rs.getString(2);
                    String createdBy = rs.getString(3);
                    ZonedDateTime createdOn = NetUtil.localToUTC(rs.getTimestamp(4).toLocalDateTime());
                    String moduleName = rs.getString(5);
                    problems.add(new MsgUtil.ProblemInfo(id, name, createdBy, createdOn, moduleName));
                }
            }
        }
        return null;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.GET_PROBLEMS;
    }

    @Override
    public boolean checkValid() {
        for (MsgUtil.ProblemInfo info : problems)
            if (info == null || !info.checkValid())
                return false;
        return true;
    }
}
