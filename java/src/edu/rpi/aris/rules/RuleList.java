package edu.rpi.aris.rules;

public enum RuleList {

    SIMPLIFICATION(new Simplification()),
    MODUS_PONENS(new ModusPonens()),
    MODUS_TOLLENS(new ModusTollens());

    public final String name, simpleName;
    public final Rule rule;

    RuleList(Rule rule) {
        this.rule = rule;
        if (rule != null) {
            name = rule.getName();
            simpleName = rule.getSimpleName();
        } else
            name = simpleName = null;
    }

}
