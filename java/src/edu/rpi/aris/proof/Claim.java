package edu.rpi.aris.proof;

import edu.rpi.aris.rules.Rule;
import edu.rpi.aris.rules.RuleList;

public class Claim {

    private Expression conclusion;
    private Premise[] premises;
    private Rule rule;

    public Claim(Expression conclusion, Premise[] premises, Rule rule) {
        this.conclusion = conclusion;
        if (premises == null)
            premises = new Premise[0];
        this.premises = premises;
        this.rule = rule;
    }

    public String isValidClaim() {
        if (rule == null)
            return "No rule specified";
        if (conclusion == null)
            return "No conclusion specified";
        return rule.verifyClaim(this);
    }

    public Expression getConclusion() {
        return conclusion;
    }

    public Premise[] getPremises() {
        return premises;
    }

    public Rule getRule() {
        return rule;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Premises:\n");
        for (Premise p : premises) {
            if (p.isSubproof()) {
                sb.append("{ ").append(p.getAssumption()).append(", ").append(p.getConclusion()).append(" }\n");
            } else {
                sb.append(p.getPremis()).append("\n");
            }
        }
        sb.append("Conclusion: ").append(conclusion).append("\n");
        sb.append("Rule: ").append(rule.getName());
        return sb.toString();
    }

}
