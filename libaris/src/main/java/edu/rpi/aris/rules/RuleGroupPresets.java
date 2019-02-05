package edu.rpi.aris.rules;

import static edu.rpi.aris.rules.RuleList.*;

public enum RuleGroupPresets implements RuleGroup {

    ALL("All Rules", RuleList.values()),
    FITCH("Fitch", new RuleList[]{REITERATION, CONJUNCTION, SIMPLIFICATION, ADDITION, DISJUNCTIVE_SYLLOGISM,
            CONDITIONAL_PROOF, MODUS_PONENS, PROOF_BY_CONTRADICTION, DOUBLENEGATION, CONTRADICTION, PRINCIPLE_OF_EXPLOSION,
            BICONDITIONAL_INTRO, BICONDITIONAL_ELIM, UNIVERSAL_GENERALIZATION, UNIVERSAL_INSTANTIATION, EXISTENTIAL_GENERALIZATION,
            EXISTENTIAL_INSTANTIATION});

    private final String groupName;
    private final RuleList[] rules;

    RuleGroupPresets(String groupName, RuleList[] rules) {
        this.groupName = groupName;
        this.rules = rules;
    }

    @Override
    public String getName() {
        return groupName;
    }

    @Override
    public RuleList[] getRules() {
        return rules;
    }
}
