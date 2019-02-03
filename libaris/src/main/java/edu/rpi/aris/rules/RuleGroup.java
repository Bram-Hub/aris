package edu.rpi.aris.rules;

import static edu.rpi.aris.rules.RuleList.*;

public enum RuleGroup {

    ALL("All Rules", RuleList.values()),
    FITCH("Fitch", new RuleList[]{REITERATION, CONJUNCTION, SIMPLIFICATION, ADDITION, DISJUNCTIVE_SYLLOGISM,
            CONDITIONAL_PROOF, MODUS_PONENS, PROOF_BY_CONTRADICTION, DOUBLENEGATION, CONTRADICTION, PRINCIPLE_OF_EXPLOSION,
            BICONDITIONAL_INTRO, BICONDITIONAL_ELIM, UNIVERSAL_GENERALIZATION, UNIVERSAL_INSTANTIATION, EXISTENTIAL_GENERALIZATION,
            EXISTENTIAL_INSTANTIATION});

    public final String groupName;
    public final RuleList[] rules;

    RuleGroup(String groupName, RuleList[] rules) {
        this.groupName = groupName;
        this.rules = rules;
    }

}
