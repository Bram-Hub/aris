package edu.rpi.aris.rules;

public enum RuleList {

    REITERATION,
    CONJUNCTION,
    SIMPLIFICATION,
    ADDITION,
    DISJUNCTIVE_SYLLOGISM,
    CONDITIONAL_PROOF,
    MODUS_PONENS,
    PROOF_BY_CONTRADICTION,
    DOUBLENEGATION,
    CONTRADICTION,
    PRINCIPLE_OF_EXPLOSION,

    UNIVERSAL_GENERALIZATION,
    UNIVERSAL_INSTANTIATION,
    EXISTENTIAL_GENERALIZATION,
    EXISTENTIAL_INSTANTIATION,

    MODUS_TOLLENS,
    HYPOTHETICAL_SYLLOGISM,
    EXCLUDED_MIDDLE,
    CONSTRUCTIVE_DILEMMA,

    ASSOCIATION,
    COMMUTATION,
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
