package edu.rpi.aris.rules;

public enum RuleList {

    CONJUNCTION(null),
    SIMPLIFICATION(null),
    ADDITION(null),
    DISJUNCTIVE_SYLLOGISM(null),
    MODUS_PONENS(null),
    MODUS_TOLLENS(null),
    HYPOTHETICAL_SYLLOGISM(null),
    EXCLUDED_MIDDLE(null),
    CONSTRUCTIVE_DILEMMA(null),
    Association(null),
    COMMUTATION(null),
    DOUBLENEGATION(null),
    Idempotence(null),
    DeMorgan(null),
    Distribution(null);

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
