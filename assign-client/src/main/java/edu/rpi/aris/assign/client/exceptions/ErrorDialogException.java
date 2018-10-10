package edu.rpi.aris.assign.client.exceptions;

public class ErrorDialogException extends RuntimeException {

    private final String title;
    private final boolean wait;

    public ErrorDialogException(String title, String msg, boolean wait) {
        super(msg);
        this.title = title;
        this.wait = wait;
    }

    public ErrorDialogException(String title, String msg, boolean wait, Exception e) {
        super(msg, e);
        this.title = title;
        this.wait = wait;
    }

    public boolean doWait() {
        return wait;
    }

    public String getTitle() {
        return title;
    }
}
