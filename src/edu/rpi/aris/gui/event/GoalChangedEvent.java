package edu.rpi.aris.gui.event;

import edu.rpi.aris.gui.MainWindow;
import edu.rpi.aris.proof.Goal;

public class GoalChangedEvent extends HistoryEvent {

    private int goalNum;
    private Goal goal;
    private boolean deleted;

    public GoalChangedEvent(int goalNum, Goal goal, boolean deleted) {
        this.goalNum = goalNum;
        this.goal = goal;
        this.deleted = deleted;
    }

    private void deleteGoal(MainWindow window) {
        window.deleteLine(-2 - goalNum);
    }

    private void addGoal(MainWindow window) {
        Goal g = window.getProof().addGoal(goalNum);
        g.setGoalString(goal.getGoalString());
        window.addGoal(g);
        window.requestFocus(window.getGoalLines().get(goalNum));
    }

    @Override
    public void undoEvent(MainWindow window) {
        if (deleted)
            addGoal(window);
        else
            deleteGoal(window);
    }

    @Override
    public void redoEvent(MainWindow window) {
        if (deleted)
            deleteGoal(window);
        else
            addGoal(window);
    }

}
