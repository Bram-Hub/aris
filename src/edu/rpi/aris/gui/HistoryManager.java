package edu.rpi.aris.gui;

import edu.rpi.aris.SizedStack;
import edu.rpi.aris.gui.event.HistoryEvent;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

public class HistoryManager {

    private static final int MAX_UNDO_HISTORY = 100;

    private SizedStack<HistoryEvent> undoHistory = new SizedStack<>(MAX_UNDO_HISTORY);
    private SizedStack<HistoryEvent> redoHistory = new SizedStack<>(MAX_UNDO_HISTORY);
    private SimpleBooleanProperty canUndoProperty = new SimpleBooleanProperty(false);
    private SimpleBooleanProperty canRedoProperty = new SimpleBooleanProperty(false);
    private MainWindow window;
    private boolean historyEvent = false;
    private boolean upcomingHistoryEvent = false;

    public HistoryManager(MainWindow window) {
        this.window = window;
    }

    public synchronized void addHistoryEvent(HistoryEvent event) {
        if (historyEvent || !window.isLoaded())
            return;
        upcomingHistoryEvent = false;
        historyEvent = true;
        window.commitSentenceChanges();
        redoHistory.clear();
        canRedoProperty.set(false);
        undoHistory.push(event);
        canUndoProperty.set(true);
        historyEvent = false;
    }

    public BooleanProperty canUndo() {
        return canUndoProperty;
    }

    public BooleanProperty canRedo() {
        return canRedoProperty;
    }

    public synchronized void undo() {
        window.commitSentenceChanges();
        if (undoHistory.size() == 0)
            return;
        historyEvent = true;
        HistoryEvent event = undoHistory.pop();
        canUndoProperty.set(undoHistory.size() > 0);
        event.undoEvent(window);
        redoHistory.push(event);
        canRedoProperty.set(true);
        historyEvent = false;
    }

    public synchronized void redo() {
        window.commitSentenceChanges();
        if (redoHistory.size() == 0)
            return;
        historyEvent = true;
        HistoryEvent event = redoHistory.pop();
        canRedoProperty.set(redoHistory.size() > 0);
        event.redoEvent(window);
        undoHistory.push(event);
        canUndoProperty.set(true);
        historyEvent = false;
    }

    public synchronized void upcomingHistoryEvent(boolean isEvent) {
        if (isEvent) {
            canUndoProperty.set(true);
            canRedoProperty.set(false);
        } else {
            canUndoProperty.set(undoHistory.size() > 0);
            canRedoProperty.set(redoHistory.size() > 0);
        }
    }

}
