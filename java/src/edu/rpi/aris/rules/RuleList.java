package edu.rpi.aris.rules;

public enum RuleList {

    SIMPLIFICATION(new Simplification(), new String[][]{{"(& A ...)"}}, new String[]{"(& A ...)"}, false, false),
    DEMORGAN(null, new String[][]{{"(! (& A ...))"}, {"(! (| A ...))"}}, new String[]{"(| (! A) ...)", "(& (! A) ...)"}, false, true),
    BICONDITIONAL(null, new String[][]{{"(<-> A B)", "A"}, {"(<-> A B)", "B"}}, new String[]{"B", "A"}, false, true),
    MODUS_PONENS(null, new String[][]{{"(-> A B)", "A"}}, new String[]{"B"}, false, true);

    public final String name, simpleName;
    public final Rule rule;
    public final String[][] premises;
    public final String[] conclusions;
    public final boolean bindConclusionFirst, obeyOrder;

    RuleList(Rule rule, String[][] premises, String[] conclusions, boolean bindConclusionFirst, boolean obeyOrder) {
        this.rule = rule;
        this.premises = premises;
        this.conclusions = conclusions;
        this.bindConclusionFirst = bindConclusionFirst;
        this.obeyOrder = obeyOrder;
        if (rule != null) {
            name = rule.getName();
            simpleName = rule.getSimpleName();
        } else
            name = simpleName = null;
    }

}
