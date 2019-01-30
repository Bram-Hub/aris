package edu.rpi.aris.rules;

public enum RuleList {

    CONJUNCTION,
    SIMPLIFICATION,
    ADDITION,
    DISJUNCTIVE_SYLLOGISM,
    MODUS_PONENS,
    MODUS_TOLLENS,
    HYPOTHETICAL_SYLLOGISM,
    EXCLUDED_MIDDLE,
    CONSTRUCTIVE_DILEMMA,
    ASSOCIATION,
    COMMUTATION,
    DOUBLENEGATION,
    IDEMPOTENCE,
    DE_MORGAN,
    DISTRIBUTION;

    public final String name, simpleName;
    public final Rule rule;

    RuleList() {
        this.rule = Rule.fromRule(this);
        if (rule != null) {
            name = rule.getName();
            simpleName = rule.getSimpleName();
        } else
            name = simpleName = null;
    }

}
