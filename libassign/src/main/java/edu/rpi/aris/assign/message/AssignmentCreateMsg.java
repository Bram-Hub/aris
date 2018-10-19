package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.NetUtil;
import edu.rpi.aris.assign.Perm;
import edu.rpi.aris.assign.ServerPermissions;
import edu.rpi.aris.assign.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;

public class AssignmentCreateMsg extends Message implements ClassMessage {

    private static final Logger logger = LogManager.getLogger(AssignmentCreateMsg.class);
    private final int cid;
    private final ArrayList<Integer> problems = new ArrayList<>();
    private final String name;
    private final ZonedDateTime dueDate;
    private int aid;

    public AssignmentCreateMsg(int cid, String name, ZonedDateTime dueDate) {
        super(Perm.ASSIGNMENT_CREATE);
        this.cid = cid;
        this.name = name;
        this.dueDate = dueDate;
    }

    // DO NOT REMOVE!! Default constructor is required for gson deserialization
    private AssignmentCreateMsg() {
        this(0, null, null);
    }

    public void addProof(int pid) {
        problems.add(pid);
    }

    public void addProofs(Collection<Integer> pids) {
        problems.addAll(pids);
    }

    @Nullable
    @Override
    public ErrorType processMessage(@NotNull Connection connection, @NotNull User user, @NotNull ServerPermissions permissions) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT id FROM assignment ORDER BY id DESC LIMIT 1;");
             PreparedStatement statement = connection.prepareStatement("INSERT INTO assignment VALUES(?, ?, ?, ?, ?, ?);")) {
            aid = 1;
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next())
                    aid = rs.getInt(1) + 1;
            }
            for (int pid : problems) {
                statement.setInt(1, aid);
                statement.setInt(2, cid);
                statement.setInt(3, pid);
                statement.setString(4, name);
                statement.setTimestamp(5, NetUtil.ZDTToTimestamp(dueDate));
                statement.setInt(6, user.uid);
                statement.addBatch();
            }
            statement.executeBatch();
        }
        return null;
    }

    @NotNull
    @Override
    public MessageType getMessageType() {
        return MessageType.CREATE_ASSIGNMENT;
    }

    @Override
    public boolean checkValid() {
        for (Integer i : problems)
            if (i == null)
                return false;
        return cid > 0 && name != null && dueDate != null;
    }

    public int getAid() {
        return aid;
    }

    public String getName() {
        return name;
    }

    public ArrayList<Integer> getProblems() {
        return problems;
    }

    public ZonedDateTime getDueDate() {
        return dueDate;
    }

    public int getClassId() {
        return cid;
    }
}
