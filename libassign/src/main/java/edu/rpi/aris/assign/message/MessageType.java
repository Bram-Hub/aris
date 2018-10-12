package edu.rpi.aris.assign.message;

public enum MessageType {

    GET_USER_INFO(UserGetMsg.class),
    GET_ASSIGNMENTS(AssignmentsGetMsg.class),
    GET_SUBMISSIONS_STUDENT(SubmissionGetStudentMsg.class),
    GET_SUBMISSIONS_INST(SubmissionGetInstructorMsg.class),
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
    EDIT_USER(UserEditMsg.class),
    CREATE_CLASS(ClassCreateMsg.class),
    DELETE_CLASS(ClassDeleteMsg.class),
    UPDATE_CLASS(null),
    ERROR(ErrorMsg.class),
    FETCH_PROBLEM(ProblemFetchMessage.class),
    ASSIGNMENT_GET_STUDENT(AssignmentGetStudentMsg.class),
    AUTH(AuthMessage.class);

    public final Class<? extends Message> msgClass;

    MessageType(Class<? extends Message> messageClass) {
        msgClass = messageClass;
    }

}
