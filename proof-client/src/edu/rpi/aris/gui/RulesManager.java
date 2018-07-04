package edu.rpi.aris.gui;

import edu.rpi.aris.rules.Rule;
import edu.rpi.aris.rules.RuleList;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.Pair;

import java.util.*;

public class RulesManager {

    private SortedMap<Rule.Type, SortedSet<RuleList>> availableRules = new TreeMap<>();
    private HashSet<EventHandler<RuleSelectEvent>> eventHandlers = new HashSet<>();
    private ContextMenu ruleDropdown = null;
    private ScrollPane ruleTable;
    private HashMap<Rule.Type, Pair<TitledPane, VBox>> ruleTypePanes = new HashMap<>();

    public RulesManager() {
        this(GuiConfig.getConfigManager().getDefaultRuleSet());
    }

    public RulesManager(Collection<RuleList> availableRules) {
        setAvailableRules(availableRules);
        ruleDropdown = new ContextMenu();
        initRuleTable();
    }

    private synchronized void initRuleTable() {
        VBox vbox = new VBox();
        ruleTable = new ScrollPane(vbox);
        ruleTable.visibleProperty().bind(GuiConfig.getConfigManager().hideRulesPanel.not());
        ruleTable.managedProperty().bind(GuiConfig.getConfigManager().hideRulesPanel.not());
        ruleTable.setFitToHeight(true);
        ruleTable.setFitToWidth(true);
        for (Rule.Type t : Rule.Type.values()) {
            VBox box = new VBox();
            TitledPane pane = new TitledPane(t.name, box);
            pane.setOnMouseClicked(mouseEvent -> {
                if (mouseEvent.getEventType() == MouseEvent.MOUSE_CLICKED)
                    pane.setExpanded(!pane.isExpanded());
            });
            vbox.getChildren().add(pane);
            box.setOnMouseClicked(Event::consume);
            box.setSpacing(3);
            box.setPadding(new Insets(3));
            ruleTypePanes.put(t, new Pair<>(pane, box));
        }
    }

    private synchronized void setAvailableRules(Collection<RuleList> availableRules) {
        for (RuleList r : availableRules) {
            if (r != null) {
                for (Rule.Type t : r.rule.getRuleType()) {
                    SortedSet<RuleList> set = this.availableRules.computeIfAbsent(t, type -> new TreeSet<>());
                    set.add(r);
                }
            }
        }
        Platform.runLater(this::buildRuleUI);
    }

    private synchronized void buildRuleUI() {
        ruleDropdown.getItems().clear();
        for (Pair<TitledPane, VBox> v : ruleTypePanes.values()) {
            v.getKey().setVisible(false);
            v.getKey().setManaged(false);
            v.getValue().getChildren().clear();
        }
        for (Map.Entry<Rule.Type, SortedSet<RuleList>> e : availableRules.entrySet()) {
            if (e.getValue().size() > 0) {
                Pair<TitledPane, VBox> tPanes = ruleTypePanes.get(e.getKey());
                tPanes.getKey().setManaged(true);
                tPanes.getKey().setVisible(true);
                Menu m = new Menu(e.getKey().name);
                for (RuleList r : e.getValue()) {
                    MenuItem item = new MenuItem(r.name);
                    item.setOnAction(actionEvent -> ruleSelected(r));
                    m.getItems().add(item);
                    Button btn = new Button(r.name);
                    btn.setMaxWidth(Double.MAX_VALUE);
                    GridPane.setFillWidth(btn, true);
                    btn.setOnAction(actionEvent -> ruleSelected(r));
                    tPanes.getValue().getChildren().add(btn);
                }
                ruleDropdown.getItems().add(m);
            }
        }
    }

    private void ruleSelected(RuleList rule) {
        RuleSelectEvent event = new RuleSelectEvent(RuleSelectEvent.RULE_SELECTED, rule);
        for (EventHandler<RuleSelectEvent> h : eventHandlers)
            h.handle(event);
    }

    public synchronized ContextMenu getRulesDropdown() {
        return ruleDropdown;
    }

    public synchronized ScrollPane getRulesTable() {
        return ruleTable;
    }

    public void addRuleSelectionHandler(EventHandler<RuleSelectEvent> eventHandler) {
        eventHandlers.add(eventHandler);
    }

    public void removeRuleSelectionHandler(EventHandler<RuleSelectEvent> eventHandler) {
        eventHandlers.add(eventHandler);
    }

}
