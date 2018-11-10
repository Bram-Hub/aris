package edu.rpi.aris.assign;

/**
 * This enum tells the {@link ModuleUI} what edit mode it should be in
 */
public enum EditMode {

    /**
     * This mode will normally be used by instructors to create or edit a problem file within a {@link ModuleUI}.
     * This mode also assumes the user will not be solving the problem only creating it. However depending on the module
     * implementation this may be equivalent to {@link EditMode#UNRESTRICTED_EDIT}
     */
    CREATE_EDIT_PROBLEM,
    /**
     * This mode allows the user to interact with the {@link ModuleUI} in any way they would like. Whether that be
     * editing a problem solving a problem etc.
     */
    UNRESTRICTED_EDIT,
    /**
     * This mode allows the user to solve a problem but not modify the actual problem within the {@link ModuleUI}.
     * This mode will be used by any students creating a submission for a homework assignment
     */
    RESTRICTED_EDIT,
    /**
     * This mode displays the problem within the {@link ModuleUI} but does not allow any form of editing.
     */
    READ_ONLY

}
