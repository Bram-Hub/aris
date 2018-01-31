package edu.rpi.aris.rules;

public enum RuleList {

    SIMPLIFICATION(new Simplification());

    public final String name, simpleName;
    public final Rule rule;

    RuleList(Rule rule) {
        this.rule = rule;
        name = rule.getName();
        simpleName = rule.getSimpleName();
    }

}
