package edu.rpi.aris.assign;

public enum Perm {

    PROBLEMS_GET(2),
    ASSIGNMENT_CREATE(1, new Perm[]{PROBLEMS_GET}),
    ASSIGNMENT_DELETE(1),
    ASSIGNMENT_EDIT(2, new Perm[]{PROBLEMS_GET}),
    CLASS_CREATE_DELETE(0),
    PROBLEM_CREATE(2),
    PROBLEM_DELETE(1),
    PROBLEM_EDIT(2),
    USER_EDIT(1),
    ASSIGNMENT_GET(3),
    PROBLEM_FETCH(3),
    USER_GET(3),
    PERMISSIONS_EDIT(0);

    public static final Perm[][] mutuallyExclusivePerms = new Perm[][]{};

    public final int defaultRoleRank;

    Perm(int defaultRoleRank, Perm[] requiredPerms) {
        this.defaultRoleRank = defaultRoleRank;
    }

    Perm(int defaultRoleRank) {
        this(defaultRoleRank, new Perm[]{});
    }

}
