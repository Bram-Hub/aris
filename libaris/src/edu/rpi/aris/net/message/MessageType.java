package edu.rpi.aris.net.message;

public enum MessageType {

    GET_USER_INFO(UserInfoMsg.class),
    GET_ASSIGNMENTS(AssignmentsMsg.class),
    GET_ASSIGNMENT_DETAIL(null),
    GET_PROOFS(null),
    LIST_SUBMISSIONS(null),
    GET_SUBMISSION(null),
    GET_SUBMISSION_DETAIL(null),
    CREATE_SUBMISSION(null),
    CREATE_ASSIGNMENT(null),
    DELETE_ASSIGNMENT(null),
    UPDATE_ASSIGNMENT(null),
    CREATE_PROOF(null),
    DELETE_PROOF(null),
    UPDATE_PROOF(null),
    CREATE_USER(null),
    DELETE_USER(null),
    UPDATE_USER(null),
    CREATE_CLASS(null),
    DELETE_CLASS(null),
    UPDATE_CLASS(null),
    ERROR(ErrorMsg.class);

    public final Class<? extends Message> msgClass;

    MessageType(Class<? extends Message> messageClass) {
        msgClass = messageClass;
    }

}
