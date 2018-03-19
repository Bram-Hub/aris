package edu.rpi.aris.gui.submit;

public class ProofInfo {

    private final int id;
    private final int classId;
    private final int assignmentId;
    private final String name;
    private final String createdBy;

    public ProofInfo(int id, int classId, int assignmentId, String name, String createdBy) {
        this.id = id;
        this.classId = classId;
        this.assignmentId = assignmentId;
        this.name = name;
        this.createdBy = createdBy;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public int getClassId() {
        return classId;
    }

    public int getAssignmentId() {
        return assignmentId;
    }
}
