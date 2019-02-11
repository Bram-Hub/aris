package edu.rpi.aris.gui;

public interface LineInterface {

    int getLineNum();

    boolean isGoal();

    boolean isAssumption();

    void cut();

    void copy();

    void paste();

    void select();

    void deselect();

}
