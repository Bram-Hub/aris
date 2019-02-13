package edu.rpi.aris.gui.event;

import edu.rpi.aris.gui.MainWindow;

import java.util.ArrayList;

public class BundledEvent extends HistoryEvent {

    private ArrayList<HistoryEvent> bundledEvents = new ArrayList<>();

    public void addEvent(HistoryEvent event) {
        if (event != null)
            bundledEvents.add(event);
    }

    @Override
    public void undoEvent(MainWindow window) {
        for (int i = bundledEvents.size() - 1; i >= 0; --i) {
            bundledEvents.get(i).undoEvent(window);
        }
    }

    @Override
    public void redoEvent(MainWindow window) {
        for (HistoryEvent bundledEvent : bundledEvents) {
            bundledEvent.redoEvent(window);
        }
    }
}
