package edu.rpi.aris.assign.message;

public enum MessageType {

    GET_USER_INFO(ConnectionInitMsg.class),
    GET_ASSIGNMENTS(AssignmentsGetMsg.class),
    GET_PROBLEMS(ProblemsGetMsg.class),
    LIST_SUBMISSIONS(null),
    GET_SUBMISSION(null),
    GET_SUBMISSION_DETAIL(null),
    CREATE_SUBMISSION(SubmissionCreateMsg.class),
    CREATE_ASSIGNMENT(AssignmentCreateMsg.class),
    DELETE_ASSIGNMENT(AssignmentDeleteMsg.class),
    EDIT_ASSIGNMENT(AssignmentEditMsg.class),
    CREATE_PROBLEM(ProblemCreateMsg.class),
    DELETE_PROBLEM(ProblemDeleteMsg.class),
    EDIT_PROBLEM(ProblemEditMsg.class),
    CREATE_USER(null),
    DELETE_USER(null),
    USER_LIST(UserListMsg.class),
    CHANGE_PASSWORD(UserChangePasswordMsg.class),
    CREATE_CLASS(ClassCreateMsg.class),
    DELETE_CLASS(ClassDeleteMsg.class),
    UPDATE_CLASS(null),
    ERROR(ErrorMsg.class),
    FETCH_PROBLEM(ProblemFetchMsg.class),
    ASSIGNMENT_GET_STUDENT(AssignmentGetStudentMsg.class),
    AUTH(AuthMessage.class),
    FETCH_SUBMISSION(SubmissionFetchMsg.class),
    ASSIGNMENT_GET_INSTRUCTOR(AssignmentGetInstructorMsg.class),
    USER_EDIT(UserEditMsg.class),
    REFRESH_SUBMISSION(SubmissionRefresh.class),
    CLASS_USER_LIST(ClassUserListMsg.class),
    USER_CLASS_ADD(UserClassAddMsg.class),
    USER_CLASS_REMOVE(UserClassRemoveMsg.class),
    USER_CREATE(UserCreateMsg.class),
    USER_DELETE(UserDeleteMsg.class),
    BATCH_USER_IMPORT(BatchUserImportMsg.class);

    public final Class<? extends Message> msgClass;

    MessageType(Class<? extends Message> messageClass) {
        msgClass = messageClass;
    }

}
