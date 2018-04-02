package edu.rpi.aris.proof;

import org.apache.commons.lang3.Range;

import java.util.Timer;
import java.util.TimerTask;

public class Goal {
    private int goalNum = -1;
    private String goalString = "";
    private String statusString = null;
    private Proof.Status goalStatus = Proof.Status.NONE;
    private Range<Integer> errorRange = null;
    private Expression expression = null;
    private Timer parseTimer = null;
    private LineChangeListener listener;
    private StatusChangeListener statusListener;
    private Proof proof;

    public Goal(Proof proof) {
        this.proof = proof;
    }

    public void setChangeListener(LineChangeListener listener) {
        this.listener = listener;
    }

    public void setStatusListener(StatusChangeListener statusListener) {
        this.statusListener = statusListener;
    }

    public int getGoalNum() {
        return goalNum;
    }

    public void setGoalNum(int goalNum) {
        this.goalNum = goalNum;
        if (listener != null)
            listener.lineNumber(goalNum);
        proof.modify();
    }

    public String getGoalString() {
        return goalString;
    }

    public void setGoalString(String expression) {
        this.goalString = expression;
        if (listener != null)
            listener.expressionString(expression);
        this.expression = null;
        startTimer();
        proof.modify();
    }

    public String getStatusString() {
        return statusString;
    }

    public void setStatusString(String statusString) {
        this.statusString = statusString;
        if (listener != null)
            listener.statusString(statusString);
        if (statusListener != null)
            statusListener.statusString(this, statusString);
    }

    public Proof.Status getStatus() {
        return goalStatus;
    }

    public void setStatus(Proof.Status status) {
        this.goalStatus = status;
        if (listener != null)
            listener.status(status);
    }

    public synchronized boolean buildExpression() {
        stopTimer();
        String str = getGoalString();
        if (str.trim().length() > 0) {
            try {
                String polish = SentenceUtil.toPolishNotation(str);
                try {
                    expression = new Expression(polish);
                } catch (ExpressionParseException e) {
                    SentenceUtil.mapExceptionToStandardForm(polish, str, e);
                }
                setStatusString("");
                setStatus(Proof.Status.NONE);
                setErrorRange(null);
                return true;
            } catch (ExpressionParseException e) {
                setStatusString(e.getMessage());
                setStatus(Proof.Status.INVALID_EXPRESSION);
                expression = null;
                if (e.getErrorOffset() == -1 || e.getErrorLength() == 0)
                    setErrorRange(null);
                else
                    setErrorRange(Range.between(e.getErrorOffset(), e.getErrorOffset() + e.getErrorLength() - 1));
                return false;
            }
        } else {
            expression = null;
            setStatusString("");
            setStatus(Proof.Status.NONE);
            setErrorRange(null);
            return false;
        }
    }

    private synchronized void startTimer() {
        stopTimer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                synchronized (Goal.this) {
                    parseTimer = null;
                    buildExpression();
                }
            }
        };
        parseTimer = new Timer(true);
        parseTimer.schedule(task, 1000);
    }

    private synchronized void stopTimer() {
        if (parseTimer != null) {
            parseTimer.cancel();
            parseTimer = null;
        }
    }

    public Range<Integer> getErrorRange() {
        return errorRange;
    }

    private void setErrorRange(Range<Integer> range) {
        errorRange = range;
        if (listener != null)
            listener.errorRange(range);
        if (statusListener != null)
            statusListener.errorRange(this, range);
    }

    public Expression getExpression() {
        return expression;
    }
}
