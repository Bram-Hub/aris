package edu.rpi.aris.gui;

import edu.rpi.aris.rules.RuleList;
import javafx.event.Event;
import javafx.event.EventType;

public class RuleSelectEvent extends Event {

    public static final EventType<RuleSelectEvent> RULE_SELECTED = new EventType<>("RULE_SELECTED");

    private final RuleList rule;

    public RuleSelectEvent(EventType<RuleSelectEvent> eventType, RuleList rule) {
        super(eventType);
        this.rule = rule;
    }

    public RuleList getRule() {
        return rule;
    }
}
