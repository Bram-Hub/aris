package edu.rpi.aris.assign;

public enum Perm {

    PROBLEMS_GET(2),
    ASSIGNMENT_CREATE(1, new Perm[]{PROBLEMS_GET}),
    ASSIGNMENT_DELETE(1),
    ASSIGNMENT_EDIT(2, new Perm[]{PROBLEMS_GET}),
    CLASS_EDIT(1),
    CLASS_CREATE_DELETE(0, new Perm[]{CLASS_EDIT}),
    PROBLEM_CREATE(2),
    PROBLEM_DELETE(1),
    PROBLEM_EDIT(2),
    USER_LIST(2),
    USER_EDIT(1, new Perm[]{USER_LIST}),
    USER_CHANGE_PASS(1),
    ASSIGNMENT_GET(3),
    PROBLEM_FETCH(3),
    USER_GET(3),
    PERMISSIONS_EDIT(0),
    SUB_GRADE_REFRESH(3),
    ASSIGNMENT_GET_STUDENT(3, new Perm[]{SUB_GRADE_REFRESH}),
    SUBMISSION_CREATE(3),
    SUBMISSION_FETCH(3),
    ASSIGNMENT_GET_INSTRUCTOR(2, new Perm[]{SUB_GRADE_REFRESH}),
    USER_CREATE(1),
    USER_DELETE(1);

    public static final Perm[][] mutuallyExclusivePerms = new Perm[][]{};

    public final int defaultRoleRank;

    Perm(int defaultRoleRank, Perm[] requiredPerms) {
        this.defaultRoleRank = defaultRoleRank;
    }

    Perm(int defaultRoleRank) {
        this(defaultRoleRank, new Perm[]{});
    }

}
