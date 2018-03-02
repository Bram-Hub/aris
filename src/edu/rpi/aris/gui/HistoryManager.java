package edu.rpi.aris.gui;

import edu.rpi.aris.SizedStack;
import edu.rpi.aris.gui.event.HistoryEvent;

public class HistoryManager {

    private static final int MAX_UNDO_HISTORY = 100;

    private SizedStack<HistoryEvent> undoHistory = new SizedStack<>(MAX_UNDO_HISTORY);
    private SizedStack<HistoryEvent> redoHistory = new SizedStack<>(MAX_UNDO_HISTORY);


}
