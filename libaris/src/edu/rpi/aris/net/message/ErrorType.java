package edu.rpi.aris.net.message;

public enum ErrorType {

    UNKNOWN_ERROR,
    PARSE_ERR,
    NOT_IMPLEMENTED,
    SQL_ERR,
    EXCEPTION,
    UNKNOWN_MSG_TYPE,
    IO_ERROR,
    INCORRECT_MSG_TYPE,
    NOT_FOUND,
    AUTH_FAIL,
    INVALID_PASSWORD,
    UNAUTHORIZED
}
