package edu.rpi.aris.assign;

import java.util.ArrayList;
import java.util.HashMap;

public enum Perm {

    PROBLEMS_GET(2, "Allow the users to get the list of all problems on the server", Group.PROBLEM),
    ASSIGNMENT_CREATE(1, new Perm[]{PROBLEMS_GET}, "Allow the users to create new assignments", Group.ASSIGNMENT),
    ASSIGNMENT_DELETE(1, "Allow the users to delete assignments", Group.ASSIGNMENT),
    ASSIGNMENT_EDIT(2, new Perm[]{PROBLEMS_GET}, "Allow the users to edit assignments", Group.ASSIGNMENT),
    CLASS_EDIT(1, "Allow the users to rename and add/remove users from the class", Group.CLASS),
    CLASS_CREATE_DELETE(0, new Perm[]{CLASS_EDIT}, "Allow the users to create and delete classes", Group.CLASS),
    PROBLEM_CREATE(2, "Allow the users to create new problems", Group.PROBLEM),
    PROBLEM_DELETE(1, "Allow the users to delete problems", Group.PROBLEM),
    PROBLEM_EDIT(2, "Allow the users to edit problems", Group.PROBLEM),
    USER_LIST(2, "Allow the users to get a list of users on the server", Group.USER),
    USER_EDIT(1, new Perm[]{USER_LIST}, "Allow the users to edit user information", Group.USER),
    USER_CHANGE_PASS(1, "Allow the users to change user passwords other than their own", Group.USER),
    ASSIGNMENT_GET(3, "Allow the users to get available assignments", Group.ASSIGNMENT),
    PROBLEM_FETCH(3, "Allow the users to retrieve individual problems from the server", Group.PROBLEM),
    CONNECTION_INIT(3, "Allow the users to connect to the server. THIS SHOULD BE USABLE BY EVERYONE", Group.OTHER),
    PERMISSIONS_EDIT(0, "Allow the users to modify permissions. Only administrators should be given this permission", Group.OTHER),
    SUB_GRADE_REFRESH(3, "Allow the users to refresh the submission grade status", Group.SUBMISSION),
    ASSIGNMENT_GET_STUDENT(3, new Perm[]{SUB_GRADE_REFRESH}, "Allow the users to get student assignment details", Group.ASSIGNMENT),
    SUBMISSION_CREATE(3, "Allow the users to create submissions", Group.SUBMISSION),
    SUBMISSION_FETCH(3, "Allow the users to get their submissions from the server", Group.SUBMISSION),
    ASSIGNMENT_GET_INSTRUCTOR(2, new Perm[]{SUB_GRADE_REFRESH}, "Allow the users to get instructor assignment details", Group.ASSIGNMENT),
    USER_CREATE(1, "Allow the users to create new users", Group.USER),
    USER_DELETE(1, "Allow the users to delete other users", Group.USER),
    BATCH_USER_IMPORT(1, new Perm[]{USER_CREATE}, "Allow the users to perform a batch user import", Group.USER);

    public static final Perm[][] mutuallyExclusivePerms = new Perm[][]{{ASSIGNMENT_GET_INSTRUCTOR, ASSIGNMENT_GET_STUDENT}};
    public static final HashMap<Group, ArrayList<Perm>> permissionGroups = new HashMap();

    public final int defaultRoleRank;
    public final String description;
    public final Perm[] requiredPerms;

    Perm(int defaultRoleRank, Perm[] requiredPerms, String description, Group group) {
        this.defaultRoleRank = defaultRoleRank;
        this.requiredPerms = requiredPerms;
        this.description = description == null ? this.name() : description;
        group.permissions.add(this);
    }

    Perm(int defaultRoleRank, String description, Group group) {
        this(defaultRoleRank, new Perm[]{}, description, group);
    }

    public enum Group {
        CLASS("Class Permissions"),
        ASSIGNMENT("Assignment Permissions"),
        PROBLEM("Problem Permissions"),
        SUBMISSION("Submission Permissions"),
        USER("User Permissions"),
        OTHER("Other Permissions");

        public final ArrayList<Perm> permissions = new ArrayList<>();
        public final String description;

        Group(String description) {
            this.description = description;
        }

    }

}
