package edu.rpi.aris.assign.message;

import edu.rpi.aris.assign.NetUtil;
import edu.rpi.aris.assign.User;
import edu.rpi.aris.assign.UserType;

import java.sql.*;
import java.time.ZonedDateTime;
import java.util.ArrayList;

public class AssignmentEditMsg extends Message {

    private final int cid;
    private final int aid;
    private String newName = null;
    private ArrayList<Integer> removeProblems = new ArrayList<>();
    private ArrayList<Integer> addProblems = new ArrayList<>();
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

    public void removeProblem(int pid) {
        removeProblems.add(pid);
    }

    public void addProblem(int pid) {
        addProblems.add(pid);
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
        if (newDueDate == null)
            return;
        try (PreparedStatement statement = connection.prepareStatement("UPDATE assignment SET due_date = ? WHERE id = ? AND class_id = ?;")) {
            statement.setTimestamp(1, NetUtil.ZDTToTimestamp(newDueDate));
            statement.setInt(2, aid);
            statement.setInt(3, cid);
            statement.executeUpdate();
        }
    }

    private void removeProblem(Connection connection) throws SQLException {
        if (removeProblems.size() == 0)
            return;
        try (PreparedStatement removeAssignment = connection.prepareStatement("DELETE FROM assignment WHERE id = ? AND class_id = ? AND problem_id = ?;")) {
            for (int pid : removeProblems) {
                removeAssignment.setInt(1, aid);
                removeAssignment.setInt(2, cid);
                removeAssignment.setInt(3, pid);
                removeAssignment.addBatch();
            }
            removeAssignment.executeBatch();
        }
    }

    private ErrorType addProblem(Connection connection) throws SQLException {
        if (addProblems.size() == 0)
            return null;
        try (PreparedStatement select = connection.prepareStatement("SELECT name, due_date, assigned_by FROM assignment WHERE id = ? AND class_id = ?;");
             PreparedStatement addProblem = connection.prepareStatement("INSERT INTO assignment VALUES(?, ?, ?, ?, ?, ?);")) {
            select.setInt(1, aid);
            select.setInt(2, cid);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next())
                    return ErrorType.NOT_FOUND;
                String n = rs.getString(1);
                Timestamp due_date = rs.getTimestamp(2);
                String assigned = rs.getString(3);
                for (int pid : addProblems) {
                    addProblem.setInt(1, aid);
                    addProblem.setInt(2, cid);
                    addProblem.setInt(3, pid);
                    addProblem.setString(4, n);
                    addProblem.setTimestamp(5, due_date);
                    addProblem.setString(6, assigned);
                    addProblem.addBatch();
                }
                addProblem.executeBatch();
            }
        }
        return null;
    }

    @Override
    public ErrorType processMessage(Connection connection, User user) throws SQLException {
        if (!UserType.hasPermission(user, UserType.INSTRUCTOR))
            return ErrorType.UNAUTHORIZED;
        rename(connection);
        changeDue(connection);
        removeProblem(connection);
        return addProblem(connection);
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.EDIT_ASSIGNMENT;
    }

    @Override
    public boolean checkValid() {
        for (Integer i : removeProblems)
            if (i == null)
                return false;
        for (Integer i : addProblems)
            if (i == null)
                return false;
        return cid > 0 && aid > 0;
    }

    public int getCid() {
        return cid;
    }

    public int getAid() {
        return aid;
    }

    public String getNewName() {
        return newName;
    }

}
