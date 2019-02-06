package edu.rpi.aris.rules;

public class CustomRuleGroup implements RuleGroup {

    private final String name;
    private final RuleList[] rules;

    public CustomRuleGroup(String name, RuleList[] rules) {
        this.name = name;
        this.rules = rules;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public RuleList[] getRules() {
        return rules;
    }
}
