package edu.rpi.aris.rules;

public enum RuleList {

    CONJUNCTION(new Conjunction()),
    SIMPLIFICATION(new Simplification()),
    ADDITION(new Addition()),
    DISJUNCTIVE_SYLLOGISM(new DisjunctiveSyllogism()),
    MODUS_PONENS(new ModusPonens()),
    MODUS_TOLLENS(new ModusTollens()),
    HYPOTHETICAL_SYLLOGISM(new HypotheticalSyllogism()),
    EXCLUDED_MIDDLE(new ExcludedMiddle()),
    CONSTRUCTIVE_DILEMMA(new ConstructiveDilemma());

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
