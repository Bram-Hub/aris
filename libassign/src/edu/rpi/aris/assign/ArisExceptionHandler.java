package edu.rpi.aris.assign;

public interface ArisExceptionHandler {

    void uncaughtException(Thread t, Throwable e, boolean fatal);

}
