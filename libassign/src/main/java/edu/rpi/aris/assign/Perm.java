package edu.rpi.aris.assign;

public enum Perm {

    ASSIGNMENT_CREATE(1),
    ASSIGNMENT_DELETE(1),
    ASSIGNMENT_EDIT(2),
    CLASS_CREATE_DELETE(0),
    PROBLEM_CREATE(2),
    PROBLEM_DELETE(1),
    PROBLEM_EDIT(2),
    PROBLEMS_GET(2),
    USER_EDIT(1);

    public final int defaultRoleRank;

    Perm(int defaultRoleRank) {
        this.defaultRoleRank = defaultRoleRank;
    }

}
