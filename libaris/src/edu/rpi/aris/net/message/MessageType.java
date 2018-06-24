package edu.rpi.aris.net.message;

public enum MessageType {

    GET_USER_INFO(UserGetMsg.class),
    GET_ASSIGNMENTS(AssignmentsGetMsg.class),
    GET_SUBMISSIONS_STUDENT(SubmissionGetStudentMsg.class),
    GET_SUBMISSIONS_INST(SubmissionGetInstructorMsg.class),
    GET_PROOFS(ProofsGetMsg.class),
    LIST_SUBMISSIONS(null),
    GET_SUBMISSION(null),
    GET_SUBMISSION_DETAIL(null),
    CREATE_SUBMISSION(null),
    CREATE_ASSIGNMENT(AssignmentCreateMsg.class),
    DELETE_ASSIGNMENT(AssignmentDeleteMsg.class),
    EDIT_ASSIGNMENT(AssignmentEditMsg.class),
    CREATE_PROOF(ProofCreateMsg.class),
    DELETE_PROOF(ProofDeleteMsg.class),
    EDIT_PROOF(ProofEditMsg.class),
    CREATE_USER(null),
    DELETE_USER(null),
    EDIT_USER(UserEditMsg.class),
    CREATE_CLASS(ClassCreateMsg.class),
    DELETE_CLASS(ClassDeleteMsg.class),
    UPDATE_CLASS(null),
    ERROR(ErrorMsg.class);

    public final Class<? extends Message> msgClass;

    MessageType(Class<? extends Message> messageClass) {
        msgClass = messageClass;
    }

}
