package edu.rpi.aris.assign;

public enum Perm {

    PROBLEMS_GET(2, "Allow the users to get the list of all problems on the server"),
    ASSIGNMENT_CREATE(1, new Perm[]{PROBLEMS_GET}, "Allow the users to create new assignments"),
    ASSIGNMENT_DELETE(1, "Allow the users to delete assignments"),
    ASSIGNMENT_EDIT(2, new Perm[]{PROBLEMS_GET}, "Allow the users to edit assignments"),
    CLASS_EDIT(1, "Allow the users to rename and add/remove users from the class"),
    CLASS_CREATE_DELETE(0, new Perm[]{CLASS_EDIT}, "Allow the users to create and delete classes"),
    PROBLEM_CREATE(2, "Allow the users to create new problems"),
    PROBLEM_DELETE(1, "Allow the users to delete problems"),
    PROBLEM_EDIT(2, "Allow the users to edit problems"),
    USER_LIST(2, "Allow the users to get a list of users on the server"),
    USER_EDIT(1, new Perm[]{USER_LIST}, "Allow the users to edit user information"),
    USER_CHANGE_PASS(1, "Allow the users to change user passwords other than their own"),
    ASSIGNMENT_GET(3, "Allow the users to get available assignments"),
    PROBLEM_FETCH(3, "Allow the users to retrieve individual problems from the server"),
    CONNECTION_INIT(3, "Allow the users to connect to the server. THIS SHOULD BE USABLE BY EVERYONE"),
    PERMISSIONS_EDIT(0, "Allow the users to modify permissions. Only administrators should be given this permission"),
    SUB_GRADE_REFRESH(3, "Allow the users to refresh the submission grade status"),
    ASSIGNMENT_GET_STUDENT(3, new Perm[]{SUB_GRADE_REFRESH}, "Allow the users to get student assignment details"),
    SUBMISSION_CREATE(3, "Allow the users to create submissions"),
    SUBMISSION_FETCH(3, "Allow the users to get their submissions from the server"),
    ASSIGNMENT_GET_INSTRUCTOR(2, new Perm[]{SUB_GRADE_REFRESH}, "Allow the users to get instructor assignment details"),
    USER_CREATE(1, "Allow the users to create new users"),
    USER_DELETE(1, "Allow the users to delete other users"),
    BATCH_USER_IMPORT(1, new Perm[]{USER_CREATE}, "Allow the users to perform a batch user import");

    public static final Perm[][] mutuallyExclusivePerms = new Perm[][]{{ASSIGNMENT_GET_INSTRUCTOR, ASSIGNMENT_GET_STUDENT}};

    public final int defaultRoleRank;
    public final String description;
    public final Perm[] requiredPerms;

    Perm(int defaultRoleRank, Perm[] requiredPerms, String description) {
        this.defaultRoleRank = defaultRoleRank;
        this.requiredPerms = requiredPerms;
        this.description = description == null ? this.name() : description;
    }

    Perm(int defaultRoleRank, String description) {
        this(defaultRoleRank, new Perm[]{}, description);
    }

}
