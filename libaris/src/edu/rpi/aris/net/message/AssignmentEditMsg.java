package edu.rpi.aris.net.message;

import edu.rpi.aris.net.NetUtil;
import edu.rpi.aris.net.User;

import java.sql.*;
import java.time.ZonedDateTime;
import java.util.ArrayList;

public class AssignmentEditMsg extends Message {

    private final int cid;
    private final int aid;
    private String newName = null;
    private ArrayList<Integer> removeProofs = new ArrayList<>();
    private ArrayList<Integer> addProofs = new ArrayList<>();
    private ZonedDateTime newDueDate = null;

    public AssignmentEditMsg(int cid, int aid) {
        this.cid = cid;
        this.aid = aid;
    }

    // DO NOT REMOVE!! Default constructor is required for gson deserialization
    private AssignmentEditMsg() {
        cid = aid = 0;
    }

    public void setName(String name) {
        newName = name;
    }

    public void setNewDueDate(ZonedDateTime utcTime) {
        newDueDate = utcTime;
    }

    public void removeProof(int pid) {
        removeProofs.add(pid);
    }

    public void addProof(int pid) {
        addProofs.add(pid);
    }

    private void rename(Connection connection) throws SQLException {
        if (newName == null)
            return;
        try (PreparedStatement statement = connection.prepareStatement("UPDATE assignment SET name = ? WHERE id = ? AND class_id = ?;")) {
            statement.setString(1, newName);
            statement.setInt(2, aid);
            statement.setInt(3, cid);
            statement.executeUpdate();
        }
    }

    private void changeDue(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE assignment SET due_date = ? WHERE id = ? AND class_id = ?;")) {
            statement.setTimestamp(1, NetUtil.ZDTToTimestamp(newDueDate));
            statement.setInt(2, aid);
            statement.setInt(3, cid);
            statement.executeUpdate();
        }
    }

    private void removeProofs(Connection connection) throws SQLException {
        if (removeProofs.size() == 0)
            return;
        try (PreparedStatement removeAssignment = connection.prepareStatement("DELETE FROM assignment WHERE id = ? AND class_id = ? AND proof_id = ?;")) {
            for (int pid : removeProofs) {
                removeAssignment.setInt(1, aid);
                removeAssignment.setInt(2, cid);
                removeAssignment.setInt(3, pid);
                removeAssignment.addBatch();
            }
            removeAssignment.executeBatch();
        }
    }

    private ErrorType addProofs(Connection connection) throws SQLException {
        if (addProofs.size() == 0)
            return null;
        try (PreparedStatement select = connection.prepareStatement("SELECT name, due_date, assigned_by FROM assignment WHERE id = ? AND class_id = ?;");
             PreparedStatement addProof = connection.prepareStatement("INSERT INTO assignment VALUES(?, ?, ?, ?, ?, ?);")) {
            select.setInt(1, aid);
            select.setInt(2, cid);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next())
                    return ErrorType.NOT_FOUND;
                String n = rs.getString(1);
                Timestamp due_date = rs.getTimestamp(2);
                String assigned = rs.getString(3);
                for (int pid : addProofs) {
                    addProof.setInt(1, aid);
                    addProof.setInt(2, cid);
                    addProof.setInt(3, pid);
                    addProof.setString(4, n);
                    addProof.setTimestamp(5, due_date);
                    addProof.setString(6, assigned);
                    addProof.addBatch();
                }
                addProof.executeBatch();
            }
        }
        return null;
    }

    @Override
    public ErrorType processMessage(Connection connection, User user) throws SQLException {
        if (!user.userType.equals(NetUtil.USER_INSTRUCTOR))
            return ErrorType.UNAUTHORIZED;
        rename(connection);
        changeDue(connection);
        removeProofs(connection);
        return addProofs(connection);
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.EDIT_ASSIGNMENT;
    }

    @Override
    public boolean checkValid() {
        for (Integer i : removeProofs)
            if (i == null)
                return false;
        for (Integer i : addProofs)
            if (i == null)
                return false;
        return cid > 0 && aid > 0;
    }

}
