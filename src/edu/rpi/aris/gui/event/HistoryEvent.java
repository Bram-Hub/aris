package edu.rpi.aris.gui.event;

import edu.rpi.aris.gui.MainWindow;

public abstract class HistoryEvent {

    public abstract void undoEvent(MainWindow window);

    public abstract void redoEvent(MainWindow window);

}
