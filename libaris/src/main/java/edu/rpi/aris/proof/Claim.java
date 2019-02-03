package edu.rpi.aris.proof;

import edu.rpi.aris.ast.Expression;
import edu.rpi.aris.rules.Rule;

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
        return rule.verifyClaim(conclusion, premises);
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
                sb.append("{\n\t").append(p.getAssumption()).append("\n-----------------\n");
                for (Expression e : p.getSubproofLines())
                    sb.append("\t").append(e).append("\n");
                sb.append("}\n");
            } else {
                sb.append(p.getPremise()).append("\n");
            }
        }
        sb.append("Conclusion: ").append(conclusion).append("\n");
        sb.append("Rule: ").append(rule.getName());
        return sb.toString();
    }

}
